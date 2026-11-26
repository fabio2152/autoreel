package com.autoreel.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.PruebaDeIntegracion;
import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.costos.dominio.RegistroCosto;
import com.autoreel.costos.infraestructura.RegistroCostoRepositorio;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import com.autoreel.ia.infraestructura.ModeloIARepositorio;
import com.autoreel.ia.infraestructura.ServidorOmniSimulado;
import com.autoreel.ia.infraestructura.ServidorVeoSimulado;
import com.autoreel.nicho.dominio.Movimientos;
import com.autoreel.nicho.pasteleria.PaquetePasteleria;
import com.autoreel.video.aplicacion.ServicioComposicion;
import com.autoreel.video.aplicacion.ServicioGeneracion;
import com.autoreel.video.aplicacion.ServicioGuion;
import com.autoreel.video.dominio.ComponedorDeVideo;
import com.autoreel.video.infraestructura.VideoRepositorio;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Generación y revisión (V22 a V27, V39 y V08) de punta a punta: aprobar el storyboard, clips con el adaptador VEO
 * (Veo 3.1 Lite en Google Cloud, el del plan Lite desde V11) real contra un servidor simulado, composición (con un
 * componedor de prueba; FFmpeg se prueba aparte), regeneraciones, consumo diario y descarga.
 */
@Import(GeneracionIntegracionTest.Configuracion.class)
class GeneracionIntegracionTest extends PruebaDeIntegracion {

    /** Google AI Studio simulado: responde un guion válido al modelo de texto del plan Lite. */
    private static final ServidorOmniSimulado AI_STUDIO = new ServidorOmniSimulado();
    /** Google Cloud simulado: entrega el token y los clips de Veo. */
    private static final ServidorVeoSimulado VEO = new ServidorVeoSimulado();
    /** Lo que responde el modelo de texto al reescribir una toma con las correcciones del negocio. */
    private static final String TOMA_CORREGIDA = """
            {"toma":"The slice is lifted once on a cake server and carried toward the camera over a bare plate.",
             "descripcionToma":"La porción se acerca sobre una pala, sin hojas de menta",
             "negativos":"  mint leaves,   mint sprigs, green herbs "}""";

    /** El prompt negativo de un clip con esas correcciones: los del nicho más los de la corrección, en una línea. */
    private static final String NEGATIVOS_CORREGIDOS = ", mint leaves, mint sprigs, green herbs";
    @DynamicPropertySource
    static void google(DynamicPropertyRegistry propiedades) {
        propiedades.add("autoreel.ia.gemini.url", AI_STUDIO::url);
        propiedades.add("autoreel.ia.google-cloud.url", VEO::url);
        propiedades.add("autoreel.ia.google-cloud.url-global", VEO::url);
        propiedades.add("autoreel.ia.google-cloud.url-token", VEO::urlToken);
    }

    /** La clave de AI Studio (guion) y la cuenta de servicio de Google Cloud (Veo), que pueden quitar las pruebas. */
    static class SecretosDePrueba implements ProveedorDeSecretos {

        volatile boolean conClave = true;

        @Override
        public Optional<String> obtener(String nombreSecreto) {
            if (!conClave) {
                return Optional.empty();
            }
            return switch (nombreSecreto) {
                case "autoreel/gemini" -> Optional.of("clave-ai-studio");
                case "autoreel/google-cloud" -> Optional.of(VEO.cuentaDeServicio());
                default -> Optional.empty();
            };
        }
    }

    /** Componedor de prueba: guarda la última composición y puede fallar a pedido. */
    static class ComponedorDePrueba implements ComponedorDeVideo {

        volatile boolean fallar;
        volatile Composicion ultima;
        private int numero;

        @Override
        public synchronized byte[] componer(Composicion composicion) {
            if (fallar) {
                throw new ErrorDeComposicion("Falla simulada");
            }
            ultima = composicion;
            return ("mp4-" + ++numero).getBytes(StandardCharsets.UTF_8);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuracion {

        @Bean
        @Primary
        SecretosDePrueba secretosDePrueba() {
            return new SecretosDePrueba();
        }

        @Bean
        @Primary
        ComponedorDePrueba componedorDePrueba() {
            return new ComponedorDePrueba();
        }
    }

    @Autowired
    private ServicioGuion guiones;

    @Autowired
    private PaquetePasteleria paquete;

    @Autowired
    private ServicioGeneracion generacion;

    @Autowired
    private ServicioComposicion composicion;

    @Autowired
    private VideoRepositorio videos;

    @Autowired
    private RegistroCostoRepositorio costos;

    @Autowired
    private SecretosDePrueba secretos;

    @Autowired
    private ComponedorDePrueba componedor;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private ModeloIARepositorio modelos;

    @BeforeEach
    void reiniciar() {
        AI_STUDIO.reiniciar();
        VEO.reiniciar();
        secretos.conClave = true;
        componedor.fallar = false;
        componedor.ultima = null;
    }

    @Test
    void v22AprobarConsumeElVideoDelDiaYAlDiaSiguienteSePuedeOtro() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long primero = storyboard(negocio);

        aprobar(negocio, primero)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("GENERANDO"))
                .andExpect(jsonPath("$.generacion.videosHoy.usados").value(1))
                .andExpect(jsonPath("$.generacion.videosHoy.puedeAprobar").value(false));

        long segundo = storyboard(negocio);
        mvc.perform(get("/api/videos/" + segundo).cookie(negocio))
                .andExpect(jsonPath("$.generacion.videosHoy.puedeAprobar").value(false));
        aprobar(negocio, segundo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VIDEOS_DEL_DIA_AGOTADOS"))
                .andExpect(jsonPath("$.mensaje").value("Ya usaste tu video de hoy. Mañana tienes uno nuevo."));

        reloj.avanzar(Duration.ofDays(1));
        aprobar(negocio, segundo).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("GENERANDO"));
    }

    @Test
    void generaLosClipsComponeYSePuedeDescargar() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id).andExpect(status().isOk());

        generacion.procesar(id);
        assertThat(VEO.creacionesRecibidas()).hasSize(2);
        assertThat(VEO.creacionesRecibidas().getFirst().autorizacion()).startsWith("Bearer token-");
        // El prompt envuelve la toma que escribió la IA con la plantilla, las prohibiciones y los negativos del
        // paquete de nicho, más los del nivel de creatividad que eligió.
        Movimientos movimientos = paquete.movimientos();
        String inicioDeLaToma = movimientos.plantillaDeToma()
                .substring(0, movimientos.plantillaDeToma().indexOf(Movimientos.MARCA_INSTRUCCION)).strip();
        JsonNode pedido = json.readTree(VEO.creacionesRecibidas().getFirst().cuerpo());
        assertThat(pedido.at("/instances/0/prompt").asString())
                .startsWith(inicioDeLaToma)
                .contains("The camera cranes up and reveals the cake inside a bakery display case.",
                        movimientos.prohibiciones())
                .endsWith(movimientos.creatividad("AMBIENTE").orElseThrow().prohibicionesExtra())
                .doesNotContain(Movimientos.MARCA_INSTRUCCION);
        assertThat(pedido.at("/parameters/negativePrompt").asString()).isEqualTo(movimientos.negativos("AMBIENTE"));
        assertThat(pedido.at("/parameters/aspectRatio").asString()).isEqualTo("9:16");
        // La toma dura 6 segundos (estructura HISTORIA) y la composición salta el primer segundo del clip (V13): Veo
        // admite 4, 6 u 8 y se pide la más corta que cubre 7 segundos.
        assertThat(pedido.at("/parameters/durationSeconds").asInt()).isEqualTo(8);
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("GENERANDO"))
                .andExpect(jsonPath("$.planos[0].estado").value("GENERANDO"));

        generacion.procesar(id);
        mvc.perform(get("/api/videos/" + id).cookie(negocio)).andExpect(jsonPath("$.estado").value("COMPONIENDO"));
        composicion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.generacion.urlVideo").isString())
                .andExpect(jsonPath("$.generacion.iaDisponible").value(true))
                .andExpect(jsonPath("$.planos[0].estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[0].usoRespaldo").value(false));
        assertThat(componedor.ultima.planos()).allSatisfy(plano -> {
            assertThat(plano.clip()).isNotNull();
            assertThat(plano.inicioDelClip()).as("se salta la foto quieta del inicio").isEqualTo(1.0);
        });
        assertThat(componedor.ultima.musica()).as("la pista del estilo").isNotNull();
        assertThat(componedor.ultima.cierre().llamado()).isEqualTo("Te esperamos en Miraflores");

        // RN-32 y V39: dos clips de 8 s (Veo no informa los segundos generados: se usa la duración pedida) a 0,05
        // dólares por segundo, con Veo 3.1 Lite, el modelo de video del plan Lite.
        List<RegistroCosto> clips = costos.findByVideoIdOrderById(id).stream()
                .filter(costo -> costo.getConcepto() == ConceptoCosto.CLIP).toList();
        assertThat(clips).hasSize(2).allSatisfy(costo ->
                assertThat(costo.getCostoUsd()).isEqualByComparingTo(new BigDecimal("0.40")));
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> assertThat(
                videos.findById(id).orElseThrow().getModeloVideoUsado().getIdentificadorModelo())
                .isEqualTo("veo-3.1-lite-generate-001"));

        mvc.perform(get("/api/videos/" + id + "/descarga").cookie(negocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("descarga=autoreel-")));
        mvc.perform(post("/api/videos/" + id + "/aprobar").with(csrf()).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("APROBADO"));
        mvc.perform(get("/api/videos/" + id + "/descarga").cookie(negocio)).andExpect(status().isOk());
        editar(negocio, id, 1, Map.of("texto", "Otro texto corto"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("VIDEO_NO_EDITABLE"));
    }

    /** V24 sin respaldo sin IA (decisión del usuario, 2026-11-06): el video pasa a ERROR y devuelve el del día. */
    @Test
    void v24UnClipQueFallaDosVecesDejaElVideoEnErrorYDevuelveElVideoDelDia() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id);

        // El primer plano falla en sus dos intentos (Veo lo informa al consultar la tarea).
        generacion.procesar(id);
        VEO.consultaFallida();
        generacion.procesar(id);
        generacion.procesar(id);
        VEO.consultaFallida();
        generacion.procesar(id);
        generacion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("ERROR"))
                .andExpect(jsonPath("$.generacion.motivoError").value(ServicioGeneracion.MOTIVO_SIN_CLIPS))
                .andExpect(jsonPath("$.generacion.videosHoy.usados").value(0))
                .andExpect(jsonPath("$.generacion.puedeReintentar").value(true));
        assertThat(VEO.creacionesRecibidas()).hasSize(3);
        assertThat(componedor.ultima == null || !componedor.ultima.planos().isEmpty()).isTrue();

        // Reintentar: vuelve a generar y a usar el video del día.
        mvc.perform(post("/api/videos/" + id + "/reintentar").with(csrf()).cookie(negocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("GENERANDO"))
                .andExpect(jsonPath("$.generacion.motivoError").doesNotExist())
                .andExpect(jsonPath("$.generacion.videosHoy.usados").value(1));
        mvc.perform(post("/api/videos/" + id + "/reintentar").with(csrf()).cookie(negocio))
                .andExpect(status().isConflict());
    }

    @Test
    void v25YV26EditarNoGastaRegeneracionesYTresRegeneracionesAgotanElBoton() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = videoListo(negocio);

        editar(negocio, id, 1, Map.of("texto", "Tu antojo de hoy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPONIENDO"))
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(0));
        composicion.procesar(id);
        assertThat(componedor.ultima.planos().getFirst().texto()).isEqualTo("Tu antojo de hoy");

        for (int vez = 1; vez <= 3; vez++) {
            regenerar(negocio, id, 2)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("GENERANDO"))
                    .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(vez));
            generacion.procesar(id);
            generacion.procesar(id);
            composicion.procesar(id);
        }
        regenerar(negocio, id, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_REGENERACIONES"));
        editar(negocio, id, 1, Map.of("texto", "Editar sigue siendo gratis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(3));
    }

    /**
     * Correcciones de un plano (2026-11-25): guardarlas no recompone ni gasta nada; al regenerar, el modelo de texto
     * reescribe la toma una sola vez (se cobra como GUION) y el clip nuevo sale con la toma corregida.
     */
    @Test
    void lasCorreccionesReescribenLaTomaUnaVezAlRegenerar() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = videoListo(negocio);
        int creaciones = VEO.creacionesRecibidas().size();

        editar(negocio, id, 2, Map.of("correcciones", "  Que no tenga\nhojas de menta  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[1].correcciones").value("Que no tenga hojas de menta"))
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(0));
        editar(negocio, id, 2, Map.of("correcciones", "x".repeat(301)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORRECCIONES_MUY_LARGAS"));
        editar(negocio, id, 2, Map.of("correcciones", "Sin hojas \uD83C\uDF43"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CARACTER_NO_PERMITIDO"));
        editar(negocio, id, 3, Map.of("correcciones", "Sin hojas"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        AI_STUDIO.guionResponde(TOMA_CORREGIDA);
        regenerarConTexto(negocio, id, 2, "Recién salida para ti")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("GENERANDO"))
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(1))
                .andExpect(jsonPath("$.planos[1].texto").value("Recién salida para ti"))
                .andExpect(jsonPath("$.planos[1].movimiento.descripcion")
                        .value("La porción se acerca sobre una pala, sin hojas de menta"));
        // El modelo de texto recibió la toma actual, las correcciones, las reglas del nicho y la foto.
        assertThat(AI_STUDIO.pedidosDeTexto()).hasSize(2);
        String pedido = AI_STUDIO.pedidosDeTexto().getLast().cuerpo();
        assertThat(pedido).contains("The slice lifts toward the camera on a marble table by a window.",
                "Que no tenga hojas de menta", "pala para tortas", "inlineData");
        generacion.procesar(id);
        assertThat(VEO.creacionesRecibidas()).hasSize(creaciones + 1);
        JsonNode clipCorregido = json.readTree(VEO.creacionesRecibidas().getLast().cuerpo());
        assertThat(clipCorregido.at("/instances/0/prompt").asString()).contains("over a bare plate");
        assertThat(clipCorregido.at("/parameters/negativePrompt").asString())
                .isEqualTo(paquete.movimientos().negativos("AMBIENTE") + NEGATIVOS_CORREGIDOS);
        generacion.procesar(id);
        composicion.procesar(id);
        assertThat(componedor.ultima.planos().get(1).texto()).isEqualTo("Recién salida para ti");
        assertThat(costos.findByVideoIdOrderById(id)).filteredOn(costo -> costo.getConcepto() == ConceptoCosto.GUION)
                .hasSize(2);

        // Ya se aplicaron: otra regeneración no vuelve a llamar al modelo de texto.
        regenerar(negocio, id, 2).andExpect(status().isOk());
        assertThat(AI_STUDIO.pedidosDeTexto()).hasSize(2);
    }

    @Test
    void siNoSePuedenAplicarLasCorreccionesNoSeGastaLaRegeneracion() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = videoListo(negocio);
        int creaciones = VEO.creacionesRecibidas().size();
        editar(negocio, id, 1, Map.of("correcciones", "Que no tenga hojas de menta")).andExpect(status().isOk());

        AI_STUDIO.guionFalla();
        regenerar(negocio, id, 1)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("CORRECCION_NO_DISPONIBLE"));
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(0));
        assertThat(VEO.creacionesRecibidas()).hasSize(creaciones);
        // Una respuesta que no sirve se cobra (RN-32), pero tampoco gasta la regeneración.
        AI_STUDIO.guionResponde("{\"toma\":\"\"}");
        regenerar(negocio, id, 1)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("CORRECCION_NO_DISPONIBLE"));
        assertThat(costos.findByVideoIdOrderById(id)).filteredOn(costo -> costo.getConcepto() == ConceptoCosto.GUION)
                .hasSize(2);

        AI_STUDIO.guionResponde(TOMA_CORREGIDA);
        regenerar(negocio, id, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(1));
    }

    /** Las correcciones guardadas en el storyboard se aplican una vez, antes de pedir el primer clip. */
    @Test
    void lasCorreccionesDelStoryboardSeAplicanEnLaPrimeraGeneracion() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        editar(negocio, id, 2, Map.of("correcciones", "Que no tenga hojas de menta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("STORYBOARD"))
                .andExpect(jsonPath("$.planos[1].correcciones").value("Que no tenga hojas de menta"));
        AI_STUDIO.guionResponde(TOMA_CORREGIDA);
        aprobar(negocio, id).andExpect(status().isOk());

        generacion.procesar(id);
        generacion.procesar(id);
        composicion.procesar(id);

        assertThat(AI_STUDIO.pedidosDeTexto()).as("el guion y una corrección").hasSize(2);
        assertThat(VEO.creacionesRecibidas()).hasSize(2);
        JsonNode clipCorregido = json.readTree(VEO.creacionesRecibidas().get(1).cuerpo());
        assertThat(clipCorregido.at("/instances/0/prompt").asString()).contains("over a bare plate");
        assertThat(clipCorregido.at("/parameters/negativePrompt").asString())
                .isEqualTo(paquete.movimientos().negativos("AMBIENTE") + NEGATIVOS_CORREGIDOS);
        JsonNode clipSinCorregir = json.readTree(VEO.creacionesRecibidas().getFirst().cuerpo());
        assertThat(clipSinCorregir.at("/instances/0/prompt").asString()).doesNotContain("over a bare plate");
        assertThat(clipSinCorregir.at("/parameters/negativePrompt").asString())
                .isEqualTo(paquete.movimientos().negativos("AMBIENTE"));
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[1].movimiento.descripcion")
                        .value("La porción se acerca sobre una pala, sin hojas de menta"));
        regenerar(negocio, id, 2).andExpect(status().isOk());
        assertThat(AI_STUDIO.pedidosDeTexto()).hasSize(2);
    }

    @Test
    void unaRegeneracionQueFallaNoSeCobraYConservaElClipAnterior() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = videoListo(negocio);

        regenerar(negocio, id, 1).andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(1));
        VEO.crearResponde(503, "{}");
        VEO.crearResponde(503, "{}");
        generacion.procesar(id);
        generacion.procesar(id);
        composicion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[0].estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[0].usoRespaldo").value(false))
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(0))
                .andExpect(jsonPath("$.generacion.avisoRevision").value(ServicioGeneracion.AVISO_REGENERACION));
    }

    @Test
    void v27UnVideoEnErrorDevuelveElVideoDelDia() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id);
        generacion.procesar(id);
        generacion.procesar(id);

        componedor.fallar = true;
        composicion.procesar(id);
        composicion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("ERROR"))
                .andExpect(jsonPath("$.generacion.motivoError").value(
                        "No pudimos armar el video. Te devolvimos el video del día."))
                .andExpect(jsonPath("$.generacion.videosHoy.usados").value(0))
                .andExpect(jsonPath("$.generacion.videosHoy.puedeAprobar").value(true));
        componedor.fallar = false;
        aprobar(negocio, storyboard(negocio)).andExpect(status().isOk());
    }

    @Test
    void siFallaUnaRecomposicionSeConservaElVideoAnterior() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = videoListo(negocio);
        String anterior = json.readTree(mvc.perform(get("/api/videos/" + id).cookie(negocio)).andReturn()
                .getResponse().getContentAsString()).at("/generacion/urlVideo").asString();

        componedor.fallar = true;
        editar(negocio, id, 1, Map.of("texto", "Un cambio que no entra")).andExpect(status().isOk());
        composicion.procesar(id);
        composicion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.generacion.urlVideo").value(anterior))
                .andExpect(jsonPath("$.generacion.avisoRevision").isString())
                .andExpect(jsonPath("$.generacion.videosHoy.usados").value(1));
    }

    /** Sin respaldo sin IA (2026-11-06): sin el modelo de video no se crea el video ni se regenera un plano. */
    @Test
    void sinCredencialNoSeCreaElVideoNiSeRegeneraPeroSePuedenEditarTextos() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long listo = videoListo(negocio);
        // Las credenciales se quitan después de tener el storyboard.
        long id = storyboard(negocio);
        secretos.conClave = false;

        aprobar(negocio, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IA_NO_DISPONIBLE"));
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("STORYBOARD"))
                .andExpect(jsonPath("$.generacion.iaDisponible").value(false));
        regenerar(negocio, listo, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IA_NO_DISPONIBLE"));
        // Las correcciones se guardan sin IA: se aplican al regenerar.
        editar(negocio, listo, 1, Map.of("correcciones", "Que no tenga hojas de menta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[0].correcciones").value("Que no tenga hojas de menta"));
        editar(negocio, listo, 1, Map.of("texto", "Solo cambio el texto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPONIENDO"));
    }

    @Test
    void sinClipALosDiezMinutosElVideoQuedaEnErrorYSeRegistranLosClipsPedidos() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id);
        generacion.procesar(id);

        reloj.avanzar(Duration.ofMinutes(11));
        VEO.consultaEnCurso();
        VEO.consultaEnCurso();
        generacion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("ERROR"))
                .andExpect(jsonPath("$.generacion.videosHoy.usados").value(0));
        // Las tareas ya se habían enviado: el proveedor las cobra (RN-32).
        assertThat(costos.findByVideoIdOrderById(id)).filteredOn(costo -> costo.getConcepto() == ConceptoCosto.CLIP)
                .hasSize(2);
    }

    @Test
    void unVideoQueEsperaEnLaColaNoPierdeSusClipsPorElPlazo() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id);
        // El video pudo esperar en la cola a que terminaran otros.
        reloj.avanzar(Duration.ofMinutes(25));

        generacion.procesar(id);
        generacion.procesar(id);

        assertThat(VEO.creacionesRecibidas()).hasSize(2);
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("COMPONIENDO"))
                .andExpect(jsonPath("$.planos[*].usoRespaldo").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))));
    }

    /** El único modo de tomas (2026-11-25) no usa transiciones: los clips terminan libres. */
    @Test
    void ningunClipLlevaFotoFinal() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id).andExpect(status().isOk());

        generacion.procesar(id);

        assertThat(VEO.creacionesRecibidas()).hasSize(2).allSatisfy(peticion -> assertThat(
                json.readTree(peticion.cuerpo()).at("/instances/0/lastFrame").isMissingNode()).isTrue());
    }

    @Test
    void v08OtroNegocioNoPuedeTocarElVideo() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = videoListo(negocio);
        Cookie otro = sesionNegocioConOnboarding(correoUnico());

        aprobar(otro, id).andExpect(status().isNotFound());
        regenerar(otro, id, 1).andExpect(status().isNotFound());
        mvc.perform(get("/api/videos/" + id + "/descarga").cookie(otro)).andExpect(status().isNotFound());
        mvc.perform(post("/api/videos/" + id + "/aprobar").with(csrf()).cookie(otro))
                .andExpect(status().isNotFound());
    }

    /**
     * V40 (beneficio por usos, 2026-11-25): los videos creados mientras el beneficio tiene usos usan su plan, y cada
     * uno gasta un uso al aprobarse. Gastados los usos, el negocio vuelve al plan Lite, y un video que se creó con el
     * beneficio pero llega tarde a aprobarse ya no se puede aprobar.
     */
    @Test
    void v40LosVideosAprobadosGastanLosUsosDelBeneficioYLuegoVuelveAlPlanLite() throws Exception {
        Cookie admin = sesionAdmin();
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long negocioId = negocioId(negocio);
        String nombrePlan = "Pro " + System.nanoTime() % 100000;
        Long planPro = crearPlanDeBeneficio(admin, nombrePlan);
        try {
            otorgarBeneficio(admin, negocioId, planPro, 2);
            long primero = storyboard(negocio);
            long segundo = storyboard(negocio);
            long tercero = storyboard(negocio);
            mvc.perform(get("/api/videos/" + tercero).cookie(negocio))
                    .andExpect(jsonPath("$.plan.nombre").value(nombrePlan));

            aprobar(negocio, primero).andExpect(status().isOk());
            mvc.perform(get("/api/admin/negocios/" + negocioId).cookie(admin))
                    .andExpect(jsonPath("$.planEfectivo.plan").value(nombrePlan))
                    .andExpect(jsonPath("$.planEfectivo.beneficioUsosRestantes").value(1));
            aprobar(negocio, segundo).andExpect(status().isOk());

            aprobar(negocio, tercero)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("BENEFICIO_AGOTADO"));
            mvc.perform(get("/api/videos/" + tercero).cookie(negocio))
                    .andExpect(jsonPath("$.estado").value("STORYBOARD"))
                    .andExpect(jsonPath("$.generacion.videosHoy.usados").value(2));
            mvc.perform(get("/api/admin/negocios/" + negocioId).cookie(admin))
                    .andExpect(jsonPath("$.planEfectivo.plan").value("Lite"))
                    .andExpect(jsonPath("$.planEfectivo.modeloVideo").value("Veo 3.1 Lite"));
            mvc.perform(get("/api/admin/negocios/" + negocioId + "/beneficios").cookie(admin))
                    .andExpect(jsonPath("$[0].estado").value("AGOTADO"))
                    .andExpect(jsonPath("$[0].usosConsumidos").value(2));

            long cuarto = storyboard(negocio);
            mvc.perform(get("/api/videos/" + cuarto).cookie(negocio))
                    .andExpect(jsonPath("$.plan.nombre").value("Lite"));
        } finally {
            desactivarPlan(admin, planPro, nombrePlan);
        }
    }

    /** Un video creado con un beneficio que el administrador terminó no se aprueba, aunque le quedaran usos. */
    @Test
    void unVideoDeUnBeneficioTerminadoNoSeApruebaYLoDiceAsi() throws Exception {
        Cookie admin = sesionAdmin();
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long negocioId = negocioId(negocio);
        String nombrePlan = "Pro " + System.nanoTime() % 100000;
        Long planPro = crearPlanDeBeneficio(admin, nombrePlan);
        try {
            otorgarBeneficio(admin, negocioId, planPro, 3);
            long id = storyboard(negocio);
            long beneficioId = vista(mvc.perform(get("/api/admin/negocios/" + negocioId + "/beneficios")
                    .cookie(admin))).get(0).get("id").asLong();
            mvc.perform(delete("/api/admin/negocios/" + negocioId + "/beneficios/" + beneficioId).with(csrf())
                    .cookie(admin)).andExpect(status().isNoContent());

            aprobar(negocio, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("BENEFICIO_TERMINADO"));
        } finally {
            desactivarPlan(admin, planPro, nombrePlan);
        }
    }

    /** RN-27 con beneficio: el video que falla al generarse devuelve el video del día y el uso del beneficio. */
    @Test
    void unVideoDelBeneficioQueFallaDevuelveSuUso() throws Exception {
        Cookie admin = sesionAdmin();
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long negocioId = negocioId(negocio);
        String nombrePlan = "Pro " + System.nanoTime() % 100000;
        Long planPro = crearPlanDeBeneficio(admin, nombrePlan);
        try {
            otorgarBeneficio(admin, negocioId, planPro, 1);
            long id = storyboard(negocio);
            aprobar(negocio, id).andExpect(status().isOk());
            mvc.perform(get("/api/admin/negocios/" + negocioId).cookie(admin))
                    .andExpect(jsonPath("$.planEfectivo.plan").value("Lite"));

            // El primer plano falla en sus dos intentos: el video pasa a ERROR (como en V24).
            generacion.procesar(id);
            VEO.consultaFallida();
            generacion.procesar(id);
            generacion.procesar(id);
            VEO.consultaFallida();
            generacion.procesar(id);
            generacion.procesar(id);
            mvc.perform(get("/api/videos/" + id).cookie(negocio))
                    .andExpect(jsonPath("$.estado").value("ERROR"))
                    .andExpect(jsonPath("$.generacion.videosHoy.usados").value(0));
            mvc.perform(get("/api/admin/negocios/" + negocioId).cookie(admin))
                    .andExpect(jsonPath("$.planEfectivo.plan").value(nombrePlan))
                    .andExpect(jsonPath("$.planEfectivo.beneficioUsosRestantes").value(1));
            mvc.perform(get("/api/admin/negocios/" + negocioId + "/beneficios").cookie(admin))
                    .andExpect(jsonPath("$[0].estado").value("VIGENTE"))
                    .andExpect(jsonPath("$[0].usosConsumidos").value(0));

            // Reintentar vuelve a gastar el uso.
            mvc.perform(post("/api/videos/" + id + "/reintentar").with(csrf()).cookie(negocio))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("GENERANDO"));
            mvc.perform(get("/api/admin/negocios/" + negocioId + "/beneficios").cookie(admin))
                    .andExpect(jsonPath("$[0].estado").value("AGOTADO"))
                    .andExpect(jsonPath("$[0].usosConsumidos").value(1));
        } finally {
            desactivarPlan(admin, planPro, nombrePlan);
        }
    }

    private long negocioId(Cookie negocio) throws Exception {
        return vista(mvc.perform(get("/api/auth/yo").cookie(negocio))).get("negocioId").asLong();
    }

    /** Plan Pro de prueba: Veo 3.1 Fast y el mismo modelo de texto del plan Lite (el de AI Studio simulado). */
    private Long crearPlanDeBeneficio(Cookie admin, String nombre) throws Exception {
        return vista(mvc.perform(post("/api/admin/planes").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(planDeBeneficio(nombre, true))))).get("id").asLong();
    }

    private void desactivarPlan(Cookie admin, Long planId, String nombre) throws Exception {
        mvc.perform(put("/api/admin/planes/" + planId).with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(planDeBeneficio(nombre, false))));
    }

    private Map<String, Object> planDeBeneficio(String nombre, boolean activo) {
        Plan lite = planes.listarConModelos().stream().filter(plan -> plan.getNombre().equals("Lite")).findFirst()
                .orElseThrow();
        Long veoFast = modelos.findAll().stream()
                .filter(modelo -> modelo.getIdentificadorModelo().equals("veo-3.1-fast-generate-001")).findFirst()
                .orElseThrow().getId();
        Map<String, Object> plan = new HashMap<>();
        plan.put("nombre", nombre);
        plan.put("descripcion", "Plan de prueba");
        plan.put("videosPorDia", 3);
        plan.put("regeneracionesPorVideo", 3);
        plan.put("modeloTextoId", lite.getModeloTexto().getId());
        plan.put("modeloVideoId", veoFast);
        plan.put("precioReferencial", new BigDecimal("99.00"));
        plan.put("activo", activo);
        return plan;
    }

    private void otorgarBeneficio(Cookie admin, long negocioId, Long planId, int usos) throws Exception {
        mvc.perform(post("/api/admin/negocios/" + negocioId + "/beneficios").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("planId", planId, "usos", usos,
                                "motivo", "Cortesía"))))
                .andExpect(status().isCreated());
    }

    private long videoListo(Cookie negocio) throws Exception {
        long id = storyboard(negocio);
        aprobar(negocio, id).andExpect(status().isOk());
        generacion.procesar(id);
        generacion.procesar(id);
        composicion.procesar(id);
        mvc.perform(get("/api/videos/" + id).cookie(negocio)).andExpect(jsonPath("$.estado").value("LISTO"));
        return id;
    }

    private long storyboard(Cookie negocio) throws Exception {
        long id = completarChat(negocio);
        guiones.procesar(id);
        return id;
    }

    private ResultActions aprobar(Cookie cookie, long id) throws Exception {
        return mvc.perform(post("/api/videos/" + id + "/aprobar-storyboard").with(csrf()).cookie(cookie));
    }

    private ResultActions regenerar(Cookie cookie, long id, int orden) throws Exception {
        return mvc.perform(post("/api/videos/" + id + "/planos/" + orden + "/regenerar").with(csrf()).cookie(cookie));
    }

    private ResultActions regenerarConTexto(Cookie cookie, long id, int orden, String texto) throws Exception {
        return mvc.perform(post("/api/videos/" + id + "/planos/" + orden + "/regenerar").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("texto", texto))));
    }

    private ResultActions editar(Cookie cookie, long id, int orden, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(put("/api/videos/" + id + "/planos/" + orden).with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    /** Recorre el chat completo y devuelve el video que nace al terminar. */
    private long completarChat(Cookie cookie) throws Exception {
        Map<String, Object> producto = Map.of("nombre", "Torta Tres Leches",
                "descripcion", "Bizcocho de tres leches con fresas frescas.", "temporadas", List.of("TODO_EL_ANIO"),
                "fotos", fotosCompletas(cookie));
        long productoId = vista(mvc.perform(post("/api/productos").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(producto)))).get("id").asLong();
        long id = vista(mvc.perform(post("/api/chat/conversaciones").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"empezarDeNuevo\":true}"))).get("id").asLong();
        responder(cookie, id, Map.of("paso", "tipoVideo", "opcion", "PRODUCTO_ESTRELLA"));
        responder(cookie, id, Map.of("paso", "producto", "productoId", productoId));
        responder(cookie, id, Map.of("paso", "especial", "texto", "Fresas frescas de Huaral"));
        reloj.avanzar(Duration.ofSeconds(4));
        vista(mvc.perform(post("/api/chat/conversaciones/" + id + "/procesar").with(csrf()).cookie(cookie)));
        responder(cookie, id, Map.of("paso", "precio", "omitir", true));
        JsonNode fin = responder(cookie, id, Map.of("paso", "extra", "omitir", true));
        return fin.get("videoId").asLong();
    }

    private JsonNode responder(Cookie cookie, long id, Map<String, Object> cuerpo) throws Exception {
        return vista(mvc.perform(post("/api/chat/conversaciones/" + id + "/mensajes").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo))));
    }

    private JsonNode vista(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andExpect(status().is2xxSuccessful()).andReturn().getResponse()
                .getContentAsString());
    }
}
