package com.autoreel.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.PruebaDeIntegracion;
import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.costos.dominio.RegistroCosto;
import com.autoreel.costos.infraestructura.RegistroCostoRepositorio;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
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
    /** El clip completo que devuelve Veo al extender (el base más la extensión). */
    private static final byte[] CLIP_EXTENDIDO = "clip-extendido-de-prueba".getBytes(StandardCharsets.UTF_8);
    /** Estructura EXTENDIDA: una sola toma de 12 segundos y el cierre de 3. */
    private static final String GUION_EXTENDIDO = """
            {"estructura":"EXTENDIDA","creatividad":"PRODUCTO",
             "idea":"La cámara rodea la torta y termina mostrando la vitrina.",
             "planos":[
              {"orden":1,"foto":1,"toma":"Slow orbit around the cake that ends revealing the shop window.",
               "descripcionToma":"Órbita que termina en la vitrina","texto":"Un antojo de fresas frescas"}],
             "cierre":{"precio":null,"frase":"Hecha con cariño para ti","ubicacion":null},
             "descripcion":"Nuestra Torta Tres Leches lleva fresas frescas de Huaral. Te esperamos en Miraflores.",
             "hashtags":["#pasteleria","#tortas","#tresleches","#postres","#miraflores"]}""";

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
    void generaLosTresClipsComponeYSePuedeDescargar() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id).andExpect(status().isOk());

        generacion.procesar(id);
        assertThat(VEO.creacionesRecibidas()).hasSize(3);
        assertThat(VEO.creacionesRecibidas().getFirst().autorizacion()).startsWith("Bearer token-");
        // El prompt envuelve la toma que escribió la IA con la plantilla, las prohibiciones y los negativos del
        // paquete de nicho, más los del nivel de creatividad que eligió.
        Movimientos movimientos = paquete.movimientos();
        String inicioDeLaToma = movimientos.plantillaDeToma()
                .substring(0, movimientos.plantillaDeToma().indexOf(Movimientos.MARCA_INSTRUCCION)).strip();
        JsonNode pedido = json.readTree(VEO.creacionesRecibidas().getFirst().cuerpo());
        assertThat(pedido.at("/instances/0/prompt").asString())
                .startsWith(inicioDeLaToma)
                .contains("Slow push-in on the strawberries.", movimientos.prohibiciones())
                .endsWith(movimientos.creatividad("PRODUCTO").orElseThrow().prohibicionesExtra())
                .doesNotContain(Movimientos.MARCA_INSTRUCCION);
        assertThat(pedido.at("/parameters/negativePrompt").asString()).isEqualTo(movimientos.negativos("PRODUCTO"));
        assertThat(pedido.at("/parameters/aspectRatio").asString()).isEqualTo("9:16");
        // La toma dura 4 segundos (estructura CLÁSICA): Veo admite 4, 6 u 8 y se pide la más corta que la cubre.
        assertThat(pedido.at("/parameters/durationSeconds").asInt()).isEqualTo(4);
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
        assertThat(componedor.ultima.planos()).allSatisfy(plano -> assertThat(plano.clip()).isNotNull());
        assertThat(componedor.ultima.musica()).as("la pista del estilo").isNotNull();
        assertThat(componedor.ultima.cierre().llamado()).isEqualTo("Te esperamos en Miraflores");

        // RN-32 y V39: tres clips de 4 s (Veo no informa los segundos generados: se usa la duración pedida) a 0,05
        // dólares por segundo, con Veo 3.1 Lite, el modelo de video del plan Lite.
        List<RegistroCosto> clips = costos.findByVideoIdOrderById(id).stream()
                .filter(costo -> costo.getConcepto() == ConceptoCosto.CLIP).toList();
        assertThat(clips).hasSize(3).allSatisfy(costo ->
                assertThat(costo.getCostoUsd()).isEqualByComparingTo(new BigDecimal("0.20")));
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
        assertThat(VEO.creacionesRecibidas()).hasSize(4);
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
        // Cambiar la foto también es regenerar.
        editar(negocio, id, 2, Map.of("foto", Map.of("tipo", "FOTO_2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_REGENERACIONES"));
        editar(negocio, id, 3, Map.of("texto", "Sigue siendo gratis editar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(3));
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

    /** Sin respaldo sin IA (2026-11-06): sin el modelo de video no se crea el video ni se cambian fotos. */
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
        editar(negocio, listo, 1, Map.of("foto", Map.of("tipo", "FOTO_1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IA_NO_DISPONIBLE"));
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
        VEO.consultaEnCurso();
        generacion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("ERROR"))
                .andExpect(jsonPath("$.generacion.videosHoy.usados").value(0));
        // Las tareas ya se habían enviado: el proveedor las cobra (RN-32).
        assertThat(costos.findByVideoIdOrderById(id)).filteredOn(costo -> costo.getConcepto() == ConceptoCosto.CLIP)
                .hasSize(3);
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

        assertThat(VEO.creacionesRecibidas()).hasSize(3);
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("COMPONIENDO"))
                .andExpect(jsonPath("$.planos[*].usoRespaldo").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))));
    }

    /** Tomas largas (2026-11-25): cada toma termina en la foto de la siguiente; la última termina libre. */
    @Test
    void conTomasLargasCadaClipTerminaEnLaFotoDeLaTomaSiguiente() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio, "LARGAS");
        aprobar(negocio, id).andExpect(status().isOk());

        generacion.procesar(id);

        List<JsonNode> pedidos = VEO.creacionesRecibidas().stream().map(peticion -> json.readTree(peticion.cuerpo()))
                .toList();
        assertThat(pedidos).hasSize(3);
        for (int orden = 0; orden < 2; orden++) {
            JsonNode ultimoCuadro = pedidos.get(orden).at("/instances/0/lastFrame");
            assertThat(ultimoCuadro.path("mimeType").asString()).isEqualTo("image/jpeg");
            // La foto final es la misma foto (ya vertical) con la que empieza la toma siguiente.
            assertThat(ultimoCuadro.path("bytesBase64Encoded").asString())
                    .isEqualTo(pedidos.get(orden + 1).at("/instances/0/image/bytesBase64Encoded").asString());
        }
        assertThat(pedidos.getLast().at("/instances/0/lastFrame").isMissingNode()).isTrue();

        generacion.procesar(id);
        composicion.procesar(id);
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.concepto.tomas").value("Tomas largas con transiciones"));
    }

    /** Con tomas cortas los clips terminan libres. */
    @Test
    void conTomasCortasNingunClipLlevaFotoFinal() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id).andExpect(status().isOk());

        generacion.procesar(id);

        assertThat(VEO.creacionesRecibidas()).hasSize(3).allSatisfy(peticion -> assertThat(
                json.readTree(peticion.cuerpo()).at("/instances/0/lastFrame").isMissingNode()).isTrue());
    }

    /**
     * Plano extendido (2026-11-25): Veo crea el clip base de 8 s y luego lo continúa; el clip del video es el
     * extendido y cada parte se cobra.
     */
    @Test
    void elPlanoExtendidoGeneraElClipBaseYLuegoLoExtiende() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboardExtendido(negocio);
        aprobar(negocio, id).andExpect(status().isOk());

        generacion.procesar(id);
        assertThat(VEO.creacionesRecibidas()).hasSize(1);
        JsonNode base = json.readTree(VEO.creacionesRecibidas().getFirst().cuerpo());
        assertThat(base.at("/instances/0/image").isMissingNode()).isFalse();
        assertThat(base.at("/instances/0/video").isMissingNode()).isTrue();
        assertThat(base.at("/parameters/durationSeconds").asInt()).isEqualTo(8);

        // El clip base llega: se guarda y se pide su extensión con el video.
        generacion.procesar(id);
        assertThat(VEO.creacionesRecibidas()).hasSize(2);
        JsonNode extension = json.readTree(VEO.creacionesRecibidas().get(1).cuerpo());
        assertThat(extension.at("/instances/0/video/bytesBase64Encoded").asString())
                .isEqualTo(Base64.getEncoder().encodeToString(ServidorVeoSimulado.CLIP));
        assertThat(extension.at("/instances/0/video/mimeType").asString()).isEqualTo("video/mp4");
        assertThat(extension.at("/instances/0/image").isMissingNode()).isTrue();
        assertThat(extension.at("/instances/0/prompt").asString())
                .startsWith("Continue this exact shot seamlessly")
                .contains("Slow orbit around the cake that ends revealing the shop window.");
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("GENERANDO"))
                .andExpect(jsonPath("$.planos[0].estado").value("GENERANDO"));

        VEO.consultaLista(CLIP_EXTENDIDO);
        generacion.procesar(id);
        mvc.perform(get("/api/videos/" + id).cookie(negocio)).andExpect(jsonPath("$.estado").value("COMPONIENDO"));
        composicion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.concepto.tomas").value("Plano extendido"))
                .andExpect(jsonPath("$.planos[0].usoRespaldo").value(false));
        assertThat(componedor.ultima.planos()).singleElement().satisfies(plano -> {
            assertThat(plano.clip()).isEqualTo(CLIP_EXTENDIDO);
            assertThat(plano.segundos()).isEqualTo(12);
        });
        // El clip base (8 s) y la extensión (7 s), a 0,05 dólares por segundo.
        List<RegistroCosto> clips = costos.findByVideoIdOrderById(id).stream()
                .filter(costo -> costo.getConcepto() == ConceptoCosto.CLIP).toList();
        assertThat(clips).hasSize(2);
        assertThat(clips.get(0).getCostoUsd()).isEqualByComparingTo(new BigDecimal("0.40"));
        assertThat(clips.get(1).getCostoUsd()).isEqualByComparingTo(new BigDecimal("0.35"));
    }

    @Test
    void siLaExtensionFallaElPlanoConservaElClipBaseYElVideoSeCompone() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboardExtendido(negocio);
        aprobar(negocio, id).andExpect(status().isOk());
        generacion.procesar(id);
        generacion.procesar(id);
        assertThat(VEO.creacionesRecibidas()).hasSize(2);

        VEO.consultaFallida();
        generacion.procesar(id);
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("COMPONIENDO"))
                .andExpect(jsonPath("$.planos[0].estado").value("LISTO"));
        composicion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[0].usoRespaldo").value(false));
        assertThat(componedor.ultima.planos().getFirst().clip()).isEqualTo(ServidorVeoSimulado.CLIP);
        assertThat(VEO.creacionesRecibidas()).as("no se vuelve a pedir el clip").hasSize(2);
        // La extensión ya se había enviado: el proveedor la cobra (RN-32).
        assertThat(costos.findByVideoIdOrderById(id)).filteredOn(costo -> costo.getConcepto() == ConceptoCosto.CLIP)
                .hasSize(2);
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
        return storyboard(negocio, "CORTAS");
    }

    private long storyboard(Cookie negocio, String tomas) throws Exception {
        long id = completarChat(negocio, tomas);
        guiones.procesar(id);
        return id;
    }

    /** Storyboard con plano extendido: una toma de 12 segundos. */
    private long storyboardExtendido(Cookie negocio) throws Exception {
        AI_STUDIO.guionResponde(GUION_EXTENDIDO);
        long id = storyboard(negocio, "EXTENDIDO");
        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("STORYBOARD"))
                .andExpect(jsonPath("$.planos[0].segundos").value(12));
        return id;
    }

    private ResultActions aprobar(Cookie cookie, long id) throws Exception {
        return mvc.perform(post("/api/videos/" + id + "/aprobar-storyboard").with(csrf()).cookie(cookie));
    }

    private ResultActions regenerar(Cookie cookie, long id, int orden) throws Exception {
        return mvc.perform(post("/api/videos/" + id + "/planos/" + orden + "/regenerar").with(csrf()).cookie(cookie));
    }

    private ResultActions editar(Cookie cookie, long id, int orden, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(put("/api/videos/" + id + "/planos/" + orden).with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    /** Recorre el chat completo, con el modo de tomas pedido, y devuelve el video que nace al terminar. */
    private long completarChat(Cookie cookie, String tomas) throws Exception {
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
        responder(cookie, id, Map.of("paso", "extra", "omitir", true));
        JsonNode fin = responder(cookie, id, Map.of("paso", "tomas", "opcion", tomas));
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
