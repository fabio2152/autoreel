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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.PruebaDeIntegracion;
import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.costos.dominio.RegistroCosto;
import com.autoreel.costos.infraestructura.RegistroCostoRepositorio;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import com.autoreel.ia.infraestructura.ServidorVeoSimulado;
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
 * real contra un servidor simulado, respaldo, composición (con un componedor de prueba; FFmpeg se prueba aparte),
 * regeneraciones, consumo diario y descarga.
 */
@Import(GeneracionIntegracionTest.Configuracion.class)
class GeneracionIntegracionTest extends PruebaDeIntegracion {

    private static final ServidorVeoSimulado VEO = new ServidorVeoSimulado();

    @DynamicPropertySource
    static void veo(DynamicPropertyRegistry propiedades) {
        propiedades.add("autoreel.ia.veo.url", VEO::url);
        propiedades.add("autoreel.ia.veo.url-token", VEO::urlToken);
    }

    /** Solo la credencial de Veo; sin la de Gemini, el guion usa su respaldo. */
    static class SecretosDePrueba implements ProveedorDeSecretos {

        volatile boolean conVeo = true;

        @Override
        public Optional<String> obtener(String nombreSecreto) {
            return conVeo && nombreSecreto.equals("autoreel/veo") ? Optional.of(VEO.cuentaDeServicio())
                    : Optional.empty();
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
        VEO.reiniciar();
        secretos.conVeo = true;
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
        assertThat(VEO.creacionesRecibidas().getFirst().cuerpo()).contains("\"negativePrompt\"", "\"9:16\"");
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

        // RN-32 y V39: tres clips de 4 s a 0,05 dólares por segundo, con Veo 3.1 Lite.
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

    @Test
    void v24UnClipQueFallaDosVecesUsaElAcercamientoYElVideoSeEntrega() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id);

        generacion.procesar(id);
        VEO.consultaFallida();
        VEO.consultaLista();
        VEO.consultaLista();
        generacion.procesar(id);
        generacion.procesar(id);
        VEO.consultaFallida();
        generacion.procesar(id);
        composicion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[0].estado").value("RESPALDO"))
                .andExpect(jsonPath("$.planos[0].usoRespaldo").value(true))
                .andExpect(jsonPath("$.planos[1].usoRespaldo").value(false));
        assertThat(VEO.creacionesRecibidas()).hasSize(4);
        var plano1 = componedor.ultima.planos().stream().filter(p -> p.orden() == 1).findFirst().orElseThrow();
        assertThat(plano1.clip()).isNull();
        assertThat(plano1.foto()).isNotEmpty();
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
        editar(negocio, id, 2, Map.of("foto", Map.of("tipo", "DETALLE")))
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
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(0));
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

    @Test
    void sinCredencialTodosLosPlanosUsanElAcercamientoYNoSeOfreceRegenerar() throws Exception {
        secretos.conVeo = false;
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = storyboard(negocio);
        aprobar(negocio, id);

        generacion.procesar(id);
        composicion.procesar(id);

        mvc.perform(get("/api/videos/" + id).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andExpect(jsonPath("$.planos[*].usoRespaldo").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$.generacion.iaDisponible").value(false));
        assertThat(VEO.peticiones()).isEmpty();
        assertThat(costos.findByVideoIdOrderById(id)).noneMatch(costo -> costo.getConcepto() == ConceptoCosto.CLIP);
        regenerar(negocio, id, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IA_NO_DISPONIBLE"));
        // La foto nueva se usa con el acercamiento, sin gastar regeneraciones.
        editar(negocio, id, 1, Map.of("foto", Map.of("tipo", "ENTERO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPONIENDO"))
                .andExpect(jsonPath("$.planos[0].foto.tipo").value("ENTERO"))
                .andExpect(jsonPath("$.generacion.regeneracionesUsadas").value(0));
    }

    @Test
    void sinClipALosDiezMinutosElPlanoUsaElAcercamientoYSeRegistraSuCosto() throws Exception {
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
                .andExpect(jsonPath("$.estado").value("COMPONIENDO"))
                .andExpect(jsonPath("$.planos[*].estado").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("RESPALDO"))));
        // La tarea ya se había enviado: el proveedor la cobra (RN-32).
        assertThat(costos.findByVideoIdOrderById(id)).filteredOn(costo -> costo.getConcepto() == ConceptoCosto.CLIP)
                .hasSize(3);
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
