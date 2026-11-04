package com.autoreel.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.autoreel.PruebaDeIntegracion;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Casos de la sección 17 del chat: V10, V11, V14, V15, V16 y V17, más RN-19 y el aislamiento entre negocios. */
class ChatIntegracionTest extends PruebaDeIntegracion {

    private static final Duration AGRUPACION = Duration.ofSeconds(4);

    @Test
    void v10SinOnboardingNoSePuedeCrearUnVideo() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");

        mvc.perform(post("/api/chat/conversaciones").with(csrf()).cookie(negocio))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ONBOARDING_INCOMPLETO"));
    }

    @Test
    void conversacionCompletaReuneLasRespuestasParaElGuion() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long producto = crearProducto(negocio, fotosCompletas(negocio), "Torta Tres Leches de Fresa");
        JsonNode conversacion = iniciar(negocio);
        long id = conversacion.get("id").asLong();
        assertThat(conversacion.get("paso").get("id").asString()).isEqualTo("tipoVideo");

        responder(negocio, id, Map.of("paso", "tipoVideo", "opcion", "PRODUCTO_ESTRELLA"))
                .andExpect(jsonPath("$.paso.id").value("producto"))
                .andExpect(jsonPath("$.productos[*].id", hasItem((int) producto)));
        responder(negocio, id, Map.of("paso", "producto", "productoId", producto))
                .andExpect(jsonPath("$.paso.id").value("especial"))
                .andExpect(jsonPath("$.paso.sugerencia").value("Bizcocho de tres leches con fresas frescas."));
        responderTexto(negocio, id, "especial", "Fresas frescas de Huaral");
        procesarDespues(negocio, id).andExpect(jsonPath("$.paso.id").value("precio"))
                .andExpect(jsonPath("$.paso.omitible").value(true));
        responder(negocio, id, Map.of("paso", "precio", "omitir", true))
                .andExpect(jsonPath("$.paso.id").value("extra"));
        responderTexto(negocio, id, "extra", "Pedidos por WhatsApp");
        procesarDespues(negocio, id)
                .andExpect(jsonPath("$.estado").value("COMPLETA"))
                .andExpect(jsonPath("$.paso").doesNotExist())
                .andExpect(jsonPath("$.respuestas.tipoVideo").value("PRODUCTO_ESTRELLA"))
                .andExpect(jsonPath("$.respuestas.producto").value((int) producto))
                .andExpect(jsonPath("$.respuestas.especial").value("Fresas frescas de Huaral"))
                .andExpect(jsonPath("$.respuestas.precio").doesNotExist())
                .andExpect(jsonPath("$.respuestas.extra").value("Pedidos por WhatsApp"))
                .andExpect(jsonPath("$.resumen[1].respuesta").value("Torta Tres Leches de Fresa"))
                .andExpect(jsonPath("$.mensajes[-1:].texto").value(hasItem("¡Listo! Estoy preparando tu propuesta…")));

        mvc.perform(get("/api/chat/conversaciones/actual").cookie(negocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.estado").value("COMPLETA"));
    }

    @Test
    void v15MensajesSeguidosSeRegistranComoUnaSolaRespuesta() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = hastaElPaso(negocio, "especial", "PRODUCTO_ESTRELLA");

        responderTexto(negocio, id, "especial", "Fresas frescas")
                .andExpect(jsonPath("$.procesarEnMs").isNumber())
                .andExpect(jsonPath("$.mensajes[-1:].pendiente").value(hasItem(true)));
        responderTexto(negocio, id, "especial", "y crema batida");
        responderTexto(negocio, id, "especial", "la más pedida");
        // Antes de 4 s, procesar no cierra la respuesta.
        procesar(negocio, id).andExpect(jsonPath("$.paso.id").value("especial"));

        procesarDespues(negocio, id)
                .andExpect(jsonPath("$.paso.id").value("precio"))
                .andExpect(jsonPath("$.respuestas.especial").value("Fresas frescas y crema batida la más pedida"))
                .andExpect(jsonPath("$.procesarEnMs").doesNotExist());
    }

    @Test
    void unTextoQueLlegaDespuesDe4SegundosEsOtraRespuesta() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = hastaElPaso(negocio, "especial", "PRODUCTO_ESTRELLA");

        responderTexto(negocio, id, "especial", "Fresas frescas");
        reloj.avanzar(AGRUPACION.plusSeconds(1));
        // Sin llamar a procesar: el envío siguiente cierra primero la respuesta anterior.
        responderTexto(negocio, id, "especial", "S/ 65")
                .andExpect(jsonPath("$.paso.id").value("precio"))
                .andExpect(jsonPath("$.respuestas.especial").value("Fresas frescas"));
        procesarDespues(negocio, id).andExpect(jsonPath("$.respuestas.precio").value("S/ 65"));
    }

    @Test
    void v16UnaPreguntaRecibeElMensajeFijoYLaMismaPregunta() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = hastaElPaso(negocio, "especial", "PRODUCTO_ESTRELLA");

        responderTexto(negocio, id, "especial", "¿Qué debería poner?");
        JsonNode vista = json.readTree(procesarDespues(negocio, id)
                .andExpect(jsonPath("$.paso.id").value("especial"))
                .andReturn().getResponse().getContentAsString());

        List<String> ultimos = ultimosMensajes(vista, 2);
        assertThat(ultimos.get(0)).startsWith("Soy un asistente para reunir información");
        assertThat(ultimos.get(1)).isEqualTo("¿Qué lo hace especial hoy?");
    }

    @Test
    void v17EnUnaPromocionResponderNoAlPrecioSeRechaza() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = hastaElPaso(negocio, "especial", "PROMOCION");
        responderTexto(negocio, id, "especial", "Fresas frescas");
        procesarDespues(negocio, id)
                .andExpect(jsonPath("$.paso.id").value("precio"))
                .andExpect(jsonPath("$.paso.obligatoria").value(true))
                .andExpect(jsonPath("$.paso.omitible").value(false));

        responderTexto(negocio, id, "precio", "no");
        JsonNode vista = json.readTree(procesarDespues(negocio, id)
                .andExpect(jsonPath("$.paso.id").value("precio"))
                .andReturn().getResponse().getContentAsString());
        assertThat(ultimosMensajes(vista, 2).get(0)).startsWith("Como es una promoción");

        // "No, gracias" tampoco vale: el chat recuerda que en una promoción el precio es obligatorio.
        vista = json.readTree(responder(negocio, id, Map.of("paso", "precio", "omitir", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paso.id").value("precio"))
                .andReturn().getResponse().getContentAsString());
        assertThat(ultimosMensajes(vista, 2).get(0)).startsWith("Como es una promoción");

        responderTexto(negocio, id, "precio", "2x1 hasta el domingo");
        procesarDespues(negocio, id)
                .andExpect(jsonPath("$.paso.id").value("extra"))
                .andExpect(jsonPath("$.respuestas.precio").value("2x1 hasta el domingo"));
    }

    @Test
    void v11UnProductoConDosFotosNoApareceParaElegir() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        Map<String, String> dos = new HashMap<>(fotosCompletas(negocio));
        dos.remove("PORCION");
        long incompleto = crearProducto(negocio, dos, "Pie de Limón");
        long id = iniciar(negocio).get("id").asLong();

        responder(negocio, id, Map.of("paso", "tipoVideo", "opcion", "PRODUCTO_ESTRELLA"))
                .andExpect(jsonPath("$.productos[*].id", not(hasItem((int) incompleto))))
                .andExpect(jsonPath("$.productosIncompletos").value(1));
        responder(negocio, id, Map.of("paso", "producto", "productoId", incompleto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_DISPONIBLE"));
    }

    @Test
    void v14UnProductoSubidoDesdeElChatSinGuardarloNoApareceEnElCatalogo() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = iniciar(negocio).get("id").asLong();
        responder(negocio, id, Map.of("paso", "tipoVideo", "opcion", "RECIEN_HORNEADO"));

        Map<String, Object> nuevo = Map.of("nombre", "Pan de Plátano", "descripcion", "Con nueces y canela.",
                "fotos", fotosCompletas(negocio), "guardarEnCatalogo", false);
        long producto = json.readTree(responder(negocio, id, Map.of("paso", "producto", "productoNuevo", nuevo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paso.id").value("especial"))
                .andExpect(jsonPath("$.paso.sugerencia").value("Con nueces y canela."))
                .andReturn().getResponse().getContentAsString()).get("respuestas").get("producto").asLong();

        mvc.perform(get("/api/productos").cookie(negocio))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) producto))));
        mvc.perform(get("/api/productos/" + producto).cookie(negocio)).andExpect(status().isNotFound());

        // Si acepta guardarlo, sí aparece.
        long otra = iniciarDeNuevo(negocio).get("id").asLong();
        responder(negocio, otra, Map.of("paso", "tipoVideo", "opcion", "RECIEN_HORNEADO"));
        Map<String, Object> guardado = Map.of("nombre", "Keke de Naranja", "descripcion", "Húmedo y cítrico.",
                "fotos", fotosCompletas(negocio), "guardarEnCatalogo", true);
        long enCatalogo = json.readTree(responder(negocio, otra, Map.of("paso", "producto", "productoNuevo", guardado))
                .andReturn().getResponse().getContentAsString()).get("respuestas").get("producto").asLong();
        mvc.perform(get("/api/productos").cookie(negocio))
                .andExpect(jsonPath("$[*].id", hasItem((int) enCatalogo)))
                .andExpect(jsonPath("$[?(@.id == " + enCatalogo + ")].temporadas[0]").value(hasItem("TODO_EL_ANIO")));
    }

    @Test
    void unProductoNuevoSinSusTresFotosSeRechaza() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = iniciar(negocio).get("id").asLong();
        responder(negocio, id, Map.of("paso", "tipoVideo", "opcion", "RECIEN_HORNEADO"));
        Map<String, String> dos = new HashMap<>(fotosCompletas(negocio));
        dos.remove("DETALLE");

        responder(negocio, id, Map.of("paso", "producto", "productoNuevo", Map.of("nombre", "Pan",
                        "descripcion", "Rico.", "fotos", dos, "guardarEnCatalogo", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PRODUCTO_INCOMPLETO"));
    }

    @Test
    void rn19HayUnaSolaConversacionEnCursoYSePuedeEmpezarDeNuevo() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long primera = iniciar(negocio).get("id").asLong();

        assertThat(iniciar(negocio).get("id").asLong()).as("iniciar es idempotente").isEqualTo(primera);

        long segunda = iniciarDeNuevo(negocio).get("id").asLong();
        assertThat(segunda).isNotEqualTo(primera);
        mvc.perform(get("/api/chat/conversaciones/actual").cookie(negocio)).andExpect(jsonPath("$.id").value((int) segunda));
        responder(negocio, primera, Map.of("paso", "tipoVideo", "opcion", "PROMOCION"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONVERSACION_CERRADA"));
    }

    @Test
    void responderAUnPasoQueYaPasoSeRechaza() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long id = iniciar(negocio).get("id").asLong();
        responder(negocio, id, Map.of("paso", "tipoVideo", "opcion", "PROMOCION")).andExpect(status().isOk());

        responder(negocio, id, Map.of("paso", "tipoVideo", "opcion", "PRODUCTO_ESTRELLA"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PASO_CAMBIADO"));
        responder(negocio, id, Map.of("paso", "producto", "texto", "la torta"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PASO_NO_ADMITE_RESPUESTA"));
        responder(negocio, id, Map.of("paso", "producto", "texto", "x", "productoId", 1))
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void siElProductoSeArchivaAntesDeTerminarHayQueElegirOtro() throws Exception {
        Cookie negocio = sesionNegocioConOnboarding(correoUnico());
        long producto = crearProducto(negocio, fotosCompletas(negocio), "Torta Tres Leches de Fresa");
        long id = hastaElPaso(negocio, "especial", "PRODUCTO_ESTRELLA", producto);
        responderTexto(negocio, id, "especial", "Fresas frescas");
        procesarDespues(negocio, id);
        responder(negocio, id, Map.of("paso", "precio", "omitir", true));
        mvc.perform(post("/api/productos/" + producto + "/archivar").with(csrf()).cookie(negocio))
                .andExpect(status().isOk());

        responder(negocio, id, Map.of("paso", "extra", "omitir", true))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.paso.id").value("producto"));
    }

    @Test
    void unNegocioNoPuedeVerNiResponderLaConversacionDeOtro() throws Exception {
        Cookie a = sesionNegocioConOnboarding(correoUnico());
        Cookie b = sesionNegocioConOnboarding(correoUnico());
        long id = iniciar(a).get("id").asLong();

        responder(b, id, Map.of("paso", "tipoVideo", "opcion", "PROMOCION"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/chat/conversaciones/" + id + "/procesar").with(csrf()).cookie(b))
                .andExpect(status().isNotFound());
    }

    // --- ayudas ---

    private JsonNode iniciar(Cookie cookie) throws Exception {
        return json.readTree(mvc.perform(post("/api/chat/conversaciones").with(csrf()).cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode iniciarDeNuevo(Cookie cookie) throws Exception {
        return json.readTree(mvc.perform(post("/api/chat/conversaciones").with(csrf()).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"empezarDeNuevo\": true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions responder(Cookie cookie, long id, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/chat/conversaciones/" + id + "/mensajes").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions responderTexto(Cookie cookie, long id, String paso, String texto) throws Exception {
        return responder(cookie, id, Map.of("paso", paso, "texto", texto)).andExpect(status().isOk());
    }

    private ResultActions procesar(Cookie cookie, long id) throws Exception {
        return mvc.perform(post("/api/chat/conversaciones/" + id + "/procesar").with(csrf()).cookie(cookie))
                .andExpect(status().isOk());
    }

    /** Deja pasar los 4 s de la agrupación y procesa. */
    private ResultActions procesarDespues(Cookie cookie, long id) throws Exception {
        reloj.avanzar(AGRUPACION);
        return procesar(cookie, id);
    }

    private long hastaElPaso(Cookie cookie, String paso, String tipoVideo) throws Exception {
        return hastaElPaso(cookie, paso, tipoVideo, crearProducto(cookie, fotosCompletas(cookie), "Torta Tres Leches"));
    }

    private long hastaElPaso(Cookie cookie, String paso, String tipoVideo, long producto) throws Exception {
        long id = iniciar(cookie).get("id").asLong();
        responder(cookie, id, Map.of("paso", "tipoVideo", "opcion", tipoVideo)).andExpect(status().isOk());
        responder(cookie, id, Map.of("paso", "producto", "productoId", producto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paso.id").value(paso));
        return id;
    }

    private long crearProducto(Cookie cookie, Map<String, String> fotos, String nombre) throws Exception {
        Map<String, Object> cuerpo = Map.of("nombre", nombre,
                "descripcion", "Bizcocho de tres leches con fresas frescas.", "temporadas", List.of("TODO_EL_ANIO"),
                "fotos", fotos);
        return json.readTree(mvc.perform(post("/api/productos").with(csrf()).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static List<String> ultimosMensajes(JsonNode vista, int cantidad) {
        List<String> textos = new ArrayList<>();
        vista.get("mensajes").forEach(mensaje -> textos.add(mensaje.get("texto").asString()));
        return textos.subList(textos.size() - cantidad, textos.size());
    }
}
