package com.autoreel.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.Imagenes;
import com.autoreel.PruebaDeIntegracion;
import com.autoreel.costos.infraestructura.RegistroCostoRepositorio;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import com.autoreel.ia.infraestructura.ServidorGeminiSimulado;
import com.autoreel.video.aplicacion.ServicioGuion;
import com.autoreel.video.dominio.MotivoRespaldo;
import com.autoreel.video.dominio.Video;
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
 * Guion y storyboard (V18 a V21) de punta a punta: chat real, {@code GeminiGuionista} real contra un servidor
 * simulado, validador, respaldo, costos y edición del storyboard.
 */
@Import(VideoIntegracionTest.Secretos.class)
class VideoIntegracionTest extends PruebaDeIntegracion {

    private static final ServidorGeminiSimulado GEMINI = new ServidorGeminiSimulado();
    private static final String CLAVE = "clave-de-prueba";

    private static final String GUION_VALIDO = """
            {"planos":[
              {"orden":1,"movimiento":"ACERCAMIENTO_LENTO","texto":"Un antojo de fresas frescas"},
              {"orden":2,"movimiento":"DESPLAZAMIENTO_LATERAL","texto":"Torta Tres Leches de casa"},
              {"orden":3,"movimiento":"LUZ_CALIDA","texto":"Cada porción con fresas de Huaral"}],
             "descripcion":"Nuestra Torta Tres Leches lleva fresas frescas de Huaral. Te esperamos en Miraflores.",
             "hashtags":["#pasteleria","#tortas","#tresleches","#postres","#miraflores"]}""";
    private static final String GUION_LARGO = GUION_VALIDO.replace("Un antojo de fresas frescas",
            "Un antojo de fresas frescas para toda la familia");

    @DynamicPropertySource
    static void gemini(DynamicPropertyRegistry propiedades) {
        propiedades.add("autoreel.ia.gemini.url", GEMINI::url);
        propiedades.add("autoreel.ia.gemini.tiempo-lectura", () -> "2s");
    }

    /** Credencial que cada prueba puede quitar. */
    static class SecretosAjustables implements ProveedorDeSecretos {

        private volatile String clave = CLAVE;

        @Override
        public Optional<String> obtener(String nombreSecreto) {
            return Optional.ofNullable(clave);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Secretos {

        @Bean
        @Primary
        SecretosAjustables secretosAjustables() {
            return new SecretosAjustables();
        }
    }

    @Autowired
    private ServicioGuion guiones;

    @Autowired
    private VideoRepositorio videos;

    @Autowired
    private RegistroCostoRepositorio costos;

    @Autowired
    private SecretosAjustables secretos;

    @Autowired
    private PlatformTransactionManager transacciones;

    @BeforeEach
    void reiniciar() {
        GEMINI.reiniciar();
        secretos.clave = CLAVE;
    }

    @Test
    void v18UnGuionValidoPasaAlStoryboard() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long videoId = completarChat(negocio, "PRODUCTO_ESTRELLA", null);

        mvc.perform(get("/api/videos/" + videoId).cookie(negocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("GUION_PENDIENTE"))
                .andExpect(jsonPath("$.planos", hasSize(0)));
        // RN-54: el video guarda el plan y los modelos con los que nació.
        Video pendiente = videos.findById(videoId).orElseThrow();
        assertThat(pendiente.getPlanAplicado().getId()).isEqualTo(planLite());
        assertThat(pendiente.getOpcionesProduccion()).containsEntry("vozEnOff", false);
        assertThat(guiones.pendientes(500)).contains(videoId);

        GEMINI.responderTexto(GUION_VALIDO);
        guiones.procesar(videoId);

        mvc.perform(get("/api/videos/" + videoId).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("STORYBOARD"))
                .andExpect(jsonPath("$.tipoVideo").value("Producto estrella"))
                .andExpect(jsonPath("$.producto.nombre").value("Torta Tres Leches"))
                .andExpect(jsonPath("$.plan.videosPorDia").value(1))
                .andExpect(jsonPath("$.avisoDeRespaldo").doesNotExist())
                .andExpect(jsonPath("$.planos", hasSize(3)))
                .andExpect(jsonPath("$.planos[0].nombre").value("Gancho"))
                .andExpect(jsonPath("$.planos[0].foto.tipo").value("DETALLE"))
                .andExpect(jsonPath("$.planos[0].foto.url").isString())
                .andExpect(jsonPath("$.planos[0].movimiento.id").value("ACERCAMIENTO_LENTO"))
                .andExpect(jsonPath("$.planos[0].texto").value("Un antojo de fresas frescas"))
                .andExpect(jsonPath("$.planos[0].maxPalabras").value(6))
                .andExpect(jsonPath("$.planos[1].foto.tipo").value("ENTERO"))
                .andExpect(jsonPath("$.planos[2].foto.tipo").value("PORCION"))
                .andExpect(jsonPath("$.cierre.precio").doesNotExist())
                .andExpect(jsonPath("$.cierre.llamado").value("Te esperamos en Miraflores"))
                .andExpect(jsonPath("$.cierre.ubicacion").value("Miraflores"))
                .andExpect(jsonPath("$.cierre.colorPrimario").value("#be123c"))
                .andExpect(jsonPath("$.cierre.logo").isString())
                .andExpect(jsonPath("$.hashtags", hasSize(5)))
                .andExpect(jsonPath("$.fotosDelProducto", hasSize(3)));

        assertThat(GEMINI.peticiones()).hasSize(1);
        assertThat(GEMINI.peticiones().getFirst().clave()).isEqualTo(CLAVE);
        assertThat(GEMINI.peticiones().getFirst().cuerpo()).contains("Torta Tres Leches", "Fresas frescas de Huaral",
                "#miraflores");
        // RN-32: cada llamada registra su costo.
        Video listo = videos.findById(videoId).orElseThrow();
        assertThat(listo.getIntentosGuion()).isEqualTo(1);
        assertThat(listo.getCostoEstimado()).isEqualByComparingTo("0.01");
        assertThat(listo.getGuion()).containsKey("planos");
        assertThat(costos.findByVideoIdOrderById(videoId)).singleElement()
                .satisfies(costo -> assertThat(costo.getCostoUsd()).isEqualByComparingTo(new BigDecimal("0.01")));
        assertThat(guiones.pendientes(500)).doesNotContain(videoId);
    }

    @Test
    void v19ElCierreLlevaElPrecioDelUsuarioYUnPrecioInventadoSeCorrige() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long videoId = completarChat(negocio, "PROMOCION", "S/ 65 la torta entera");

        GEMINI.responderTexto(GUION_VALIDO.replace("Te esperamos en Miraflores.", "Hoy a solo S/ 50."));
        GEMINI.responderTexto(GUION_VALIDO);
        guiones.procesar(videoId);

        mvc.perform(get("/api/videos/" + videoId).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("STORYBOARD"))
                .andExpect(jsonPath("$.avisoDeRespaldo").doesNotExist())
                .andExpect(jsonPath("$.cierre.precio").value("S/ 65 la torta entera"))
                .andExpect(jsonPath("$.cierre.maximoCaracteresPrecio").value(40));
        assertThat(GEMINI.peticiones()).hasSize(2);
        assertThat(GEMINI.peticiones().get(1).cuerpo()).contains("corrígelos", "menciona \\\"50\\\"");
        assertThat(costos.findByVideoIdOrderById(videoId)).hasSize(2);
    }

    @Test
    void v20TresGuionesInvalidosUsanElRespaldo() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long videoId = completarChat(negocio, "PRODUCTO_ESTRELLA", null);

        GEMINI.responderTexto(GUION_LARGO);
        GEMINI.responderTexto(GUION_LARGO);
        GEMINI.responderTexto(GUION_LARGO);
        guiones.procesar(videoId);

        mvc.perform(get("/api/videos/" + videoId).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("STORYBOARD"))
                .andExpect(jsonPath("$.avisoDeRespaldo").value(
                        "Preparamos una propuesta básica con tus respuestas. Revisa los textos y ajústalos a tu gusto."))
                .andExpect(jsonPath("$.planos[1].texto").value("Torta Tres Leches"))
                .andExpect(jsonPath("$.planos[2].texto").value("Fresas frescas de Huaral"))
                .andExpect(jsonPath("$.cierre.llamado").value("Te esperamos en Miraflores"));
        Video video = videos.findById(videoId).orElseThrow();
        assertThat(video.getMotivoRespaldo()).isEqualTo(MotivoRespaldo.GUION_INVALIDO);
        assertThat(video.getIntentosGuion()).isEqualTo(3);
        assertThat(GEMINI.peticiones()).hasSize(3);
        assertThat(costos.findByVideoIdOrderById(videoId)).hasSize(3);
    }

    @Test
    void sinCredencialUsaElRespaldoSinLlamarNiCobrar() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long videoId = completarChat(negocio, "RECIEN_HORNEADO", null);
        secretos.clave = null;

        guiones.procesar(videoId);

        mvc.perform(get("/api/videos/" + videoId).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("STORYBOARD"))
                .andExpect(jsonPath("$.planos[0].texto").value("Recién salido del horno"))
                .andExpect(jsonPath("$.avisoDeRespaldo").isString());
        assertThat(videos.findById(videoId).orElseThrow().getMotivoRespaldo())
                .isEqualTo(MotivoRespaldo.SIN_CREDENCIAL);
        assertThat(GEMINI.peticiones()).isEmpty();
        assertThat(costos.findByVideoIdOrderById(videoId)).isEmpty();
    }

    @Test
    void siElProveedorFallaUsaElRespaldoSinCobrar() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long videoId = completarChat(negocio, "PRODUCTO_ESTRELLA", null);

        GEMINI.responder(503, "{\"error\":{\"code\":503}}");
        guiones.procesar(videoId);

        Video video = videos.findById(videoId).orElseThrow();
        assertThat(video.getMotivoRespaldo()).isEqualTo(MotivoRespaldo.PROVEEDOR_NO_DISPONIBLE);
        assertThat(GEMINI.peticiones()).hasSize(1);
        assertThat(costos.findByVideoIdOrderById(videoId)).isEmpty();
    }

    @Test
    void unProcesoInterrumpidoTerminaConElRespaldo() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long videoId = completarChat(negocio, "PRODUCTO_ESTRELLA", null);
        // Otro proceso lo tomó y se cortó (por ejemplo, un reinicio).
        new TransactionTemplate(transacciones).executeWithoutResult(estado ->
                videos.reclamar(videoId, reloj.instant(), reloj.instant().plus(Duration.ofMinutes(3))));

        guiones.procesar(videoId);
        assertThat(videos.findById(videoId).orElseThrow().getEstado().name()).isEqualTo("GUION_PENDIENTE");

        reloj.avanzar(Duration.ofMinutes(4));
        GEMINI.responderTexto(GUION_VALIDO);
        guiones.procesar(videoId);

        Video video = videos.findById(videoId).orElseThrow();
        assertThat(video.getEstado().name()).isEqualTo("STORYBOARD");
        assertThat(video.getMotivoRespaldo()).isEqualTo(MotivoRespaldo.PROCESO_INTERRUMPIDO);
        assertThat(GEMINI.peticiones()).isEmpty();
    }

    @Test
    void v21EdicionDelStoryboard() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long videoId = storyboardListo(negocio);

        editar(negocio, videoId, 1, Map.of("texto", "Un antojo de fresas frescas para toda la familia"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TEXTO_MUY_LARGO"))
                .andExpect(jsonPath("$.mensaje").value(
                        "Este plano admite hasta 6 palabras, para que se lea en el video."));
        editar(negocio, videoId, 1, Map.of("texto", "La mejor torta de Lima"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PALABRA_PROHIBIDA"));
        editar(negocio, videoId, 1, Map.of("texto", "Antojo 🍓"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CARACTER_NO_PERMITIDO"));
        editar(negocio, videoId, 1, Map.of("texto", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TEXTO_VACIO"));
        editar(negocio, videoId, 1, Map.of("texto", "  Tu antojo de hoy  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planos[0].texto").value("Tu antojo de hoy"));

        // Cambiar la foto: otra del producto o una nueva.
        editar(negocio, videoId, 1, Map.of("foto", Map.of("tipo", "ENTERO")))
                .andExpect(jsonPath("$.planos[0].foto.tipo").value("ENTERO"));
        String subida = subir(negocio, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg");
        editar(negocio, videoId, 2, Map.of("foto", Map.of("clave", subida)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planos[1].foto.tipo").doesNotExist())
                .andExpect(jsonPath("$.planos[1].foto.url").isString());
        editar(negocio, videoId, 2, Map.of("foto", Map.of("tipo", "ENTERO", "clave", subida)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        // Cierre: solo el precio, hasta 40 caracteres.
        editar(negocio, videoId, 4, Map.of("precio", "S/ 65 la torta entera y llévate un postre"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PRECIO_MUY_LARGO"));
        editar(negocio, videoId, 4, Map.of("precio", "S/ 59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cierre.precio").value("S/ 59"));
        editar(negocio, videoId, 4, Map.of("texto", "Hola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        editar(negocio, videoId, 9, Map.of("texto", "Hola"))
                .andExpect(status().isNotFound());
    }

    @Test
    void descartarYAislamientoEntreNegocios() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long videoId = storyboardListo(negocio);
        Cookie otro = sesionNegocioConOnboarding(correoUnico());

        mvc.perform(get("/api/videos/" + videoId).cookie(otro)).andExpect(status().isNotFound());
        editar(otro, videoId, 1, Map.of("texto", "Hola a todos")).andExpect(status().isNotFound());
        mvc.perform(post("/api/videos/" + videoId + "/descartar").with(csrf()).cookie(otro))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/videos").cookie(otro)).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/videos/" + videoId + "/descartar").with(csrf()).cookie(negocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESCARTADO"));
        editar(negocio, videoId, 1, Map.of("texto", "Hola a todos"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VIDEO_NO_EDITABLE"));
    }

    @Test
    void misVideosMuestraLosMasRecientesPrimero() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long primero = storyboardListo(negocio);
        mvc.perform(post("/api/videos/" + primero + "/descartar").with(csrf()).cookie(negocio))
                .andExpect(status().isOk());
        reloj.avanzar(Duration.ofMinutes(1));
        long segundo = completarChat(negocio, "RECIEN_HORNEADO", null);

        mvc.perform(get("/api/videos").cookie(negocio))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value((int) segundo))
                .andExpect(jsonPath("$[0].estado").value("GUION_PENDIENTE"))
                .andExpect(jsonPath("$[0].producto").value("Torta Tres Leches"))
                .andExpect(jsonPath("$[0].tipoVideo").value("Recién salido del horno"))
                .andExpect(jsonPath("$[0].foto").isString())
                .andExpect(jsonPath("$[1].id").value((int) primero))
                .andExpect(jsonPath("$[1].estado").value("DESCARTADO"));
    }

    private long storyboardListo(Cookie negocio) throws Exception {
        long videoId = completarChat(negocio, "PRODUCTO_ESTRELLA", null);
        GEMINI.responderTexto(GUION_VALIDO);
        guiones.procesar(videoId);
        return videoId;
    }

    private ResultActions editar(Cookie cookie, long videoId, int orden, Map<String, Object> cuerpo)
            throws Exception {
        return mvc.perform(put("/api/videos/" + videoId + "/planos/" + orden).with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    /** Recorre el chat completo y devuelve el video que nace al terminar. */
    private long completarChat(Cookie cookie, String tipoVideo, String precio) throws Exception {
        long producto = crearProducto(cookie);
        long id = vista(mvc.perform(post("/api/chat/conversaciones").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"empezarDeNuevo\":true}"))).get("id").asLong();
        responder(cookie, id, Map.of("paso", "tipoVideo", "opcion", tipoVideo));
        responder(cookie, id, Map.of("paso", "producto", "productoId", producto));
        responder(cookie, id, Map.of("paso", "especial", "texto", "Fresas frescas de Huaral"));
        procesarDespues(cookie, id);
        if (precio == null) {
            responder(cookie, id, Map.of("paso", "precio", "omitir", true));
        } else {
            responder(cookie, id, Map.of("paso", "precio", "texto", precio));
            procesarDespues(cookie, id);
        }
        JsonNode fin = responder(cookie, id, Map.of("paso", "extra", "omitir", true));
        assertThat(fin.get("estado").asString()).isEqualTo("COMPLETA");
        assertThat(fin.get("videoId").isNumber()).isTrue();
        return fin.get("videoId").asLong();
    }

    private long crearProducto(Cookie cookie) throws Exception {
        Map<String, Object> cuerpo = Map.of("nombre", "Torta Tres Leches",
                "descripcion", "Bizcocho de tres leches con fresas frescas.", "temporadas", List.of("TODO_EL_ANIO"),
                "fotos", fotosCompletas(cookie));
        return vista(mvc.perform(post("/api/productos").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)))).get("id").asLong();
    }

    private JsonNode responder(Cookie cookie, long id, Map<String, Object> cuerpo) throws Exception {
        return vista(mvc.perform(post("/api/chat/conversaciones/" + id + "/mensajes").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo))));
    }

    private void procesarDespues(Cookie cookie, long id) throws Exception {
        reloj.avanzar(Duration.ofSeconds(4));
        vista(mvc.perform(post("/api/chat/conversaciones/" + id + "/procesar").with(csrf()).cookie(cookie)));
    }

    private JsonNode vista(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andExpect(status().is2xxSuccessful()).andReturn().getResponse()
                .getContentAsString());
    }
}
