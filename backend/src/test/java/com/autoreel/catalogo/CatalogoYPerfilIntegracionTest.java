package com.autoreel.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.autoreel.Imagenes;
import com.autoreel.PruebaDeIntegracion;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class CatalogoYPerfilIntegracionTest extends PruebaDeIntegracion {

    @Test
    void v08UnNegocioNoPuedeVerNiModificarLosProductosDeOtro() throws Exception {
        Cookie a = sesionNegocioListo(correoUnico(), "MiClave2026");
        Cookie b = sesionNegocioListo(correoUnico(), "MiClave2026");
        long producto = crearProducto(a, fotosCompletas(a)).get("id").asLong();

        mvc.perform(get("/api/productos/" + producto).cookie(b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(put("/api/productos/" + producto).with(csrf()).cookie(b)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(solicitud(Map.of()))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/productos/" + producto + "/archivar").with(csrf()).cookie(b))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/productos").cookie(b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) producto))));
        mvc.perform(get("/api/productos").cookie(a))
                .andExpect(jsonPath("$[*].id", hasItem((int) producto)));
    }

    @Test
    void v11UnProductoConDosFotosQuedaIncompleto() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");
        Map<String, String> dos = new HashMap<>(fotosCompletas(negocio));
        dos.remove("PORCION");

        JsonNode producto = crearProducto(negocio, dos);
        assertThat(producto.get("completo").asBoolean()).isFalse();

        // Las fotos ya guardadas se envían con su clave definitiva; la nueva, con su clave de subida.
        Map<String, String> tres = new HashMap<>();
        tres.put("ENTERO", foto(producto, "ENTERO"));
        tres.put("DETALLE", foto(producto, "DETALLE"));
        tres.put("PORCION", subir(negocio, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg"));
        actualizarProducto(negocio, producto.get("id").asLong(), tres)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.fotos.length()").value(3));
    }

    @Test
    void v12UnaFotoDeMenosDe720PixelesSeRechaza() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");
        Map<String, String> fotos = new HashMap<>(fotosCompletas(negocio));
        fotos.put("DETALLE", subir(negocio, "FOTO_PRODUCTO", Imagenes.jpg(700, 1200), "image/jpeg"));

        mvc.perform(post("/api/productos").with(csrf()).cookie(negocio)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(solicitud(fotos))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FOTO_RESOLUCION_INSUFICIENTE"));
    }

    @Test
    void reemplazarUnaFotoLaVerificaYLaGuardaEnSuClaveDefinitiva() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");
        Map<String, String> fotos = new HashMap<>(fotosCompletas(negocio));
        JsonNode producto = crearProducto(negocio, fotos);
        String enteroAnterior = foto(producto, "ENTERO");
        assertThat(enteroAnterior).startsWith("negocios/").doesNotContain("subidas/");

        Map<String, String> nuevas = new HashMap<>();
        nuevas.put("ENTERO", subir(negocio, "FOTO_PRODUCTO", Imagenes.png(1920, 1080, false), "image/png"));
        nuevas.put("DETALLE", foto(producto, "DETALLE"));
        nuevas.put("PORCION", foto(producto, "PORCION"));
        JsonNode actualizado = json.readTree(actualizarProducto(negocio, producto.get("id").asLong(), nuevas)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisos[0]").value("FOTO_HORIZONTAL"))
                .andReturn().getResponse().getContentAsString());

        assertThat(foto(actualizado, "ENTERO")).isNotEqualTo(enteroAnterior).endsWith(".png");
        assertThat(almacen.existe(nuevas.get("ENTERO"))).as("la subida temporal se borra").isFalse();
        assertThat(almacen.existe(foto(actualizado, "ENTERO"))).isTrue();
    }

    @Test
    void noSePuedeUsarUnArchivoDeOtroNegocio() throws Exception {
        Cookie a = sesionNegocioListo(correoUnico(), "MiClave2026");
        Cookie b = sesionNegocioListo(correoUnico(), "MiClave2026");
        String deA = subir(a, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg");
        String definitivaDeA = foto(crearProducto(a, fotosCompletas(a)), "ENTERO");
        String deB = subir(b, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg");
        // Recorridos de carpeta armados desde el prefijo del propio negocio B, que apuntan a otra carpeta.
        String conRecorrido = deB.replaceFirst("/foto_producto/", "/foto_producto/../../../");
        String conRecorridoCodificado = deB.replaceFirst("/foto_producto/", "/foto_producto/%2e%2e/%2e%2e/");

        for (String ajena : List.of(deA, definitivaDeA, conRecorrido, conRecorridoCodificado)) {
            Map<String, String> fotos = new HashMap<>(fotosCompletas(b));
            fotos.put("ENTERO", ajena);
            mvc.perform(post("/api/productos").with(csrf()).cookie(b)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(solicitud(fotos))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("ARCHIVO_INVALIDO"));
        }
    }

    @Test
    void archivarYReactivarUnProducto() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");
        long producto = crearProducto(negocio, fotosCompletas(negocio)).get("id").asLong();

        mvc.perform(post("/api/productos/" + producto + "/archivar").with(csrf()).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("ARCHIVADO"));
        mvc.perform(get("/api/productos").cookie(negocio))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) producto))));
        mvc.perform(get("/api/productos").param("estado", "ARCHIVADO").cookie(negocio))
                .andExpect(jsonPath("$[*].id", hasItem((int) producto)));
        mvc.perform(post("/api/productos/" + producto + "/reactivar").with(csrf()).cookie(negocio))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    void validaLosDatosDelProducto() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");
        Map<String, Object> sinDescripcion = new HashMap<>(solicitud(Map.of()));
        sinDescripcion.put("descripcion", " ");
        enviarProducto(negocio, sinDescripcion).andExpect(jsonPath("$.codigo").value("PRODUCTO_DESCRIPCION_INVALIDA"));

        Map<String, Object> nombreLargo = new HashMap<>(solicitud(Map.of()));
        nombreLargo.put("nombre", "x".repeat(51));
        enviarProducto(negocio, nombreLargo).andExpect(jsonPath("$.codigo").value("PRODUCTO_NOMBRE_INVALIDO"));

        Map<String, Object> temporadaInventada = new HashMap<>(solicitud(Map.of()));
        temporadaInventada.put("temporadas", List.of("PRIMAVERA"));
        enviarProducto(negocio, temporadaInventada).andExpect(jsonPath("$.codigo").value("TEMPORADA_INVALIDA"));
    }

    @Test
    void elOnboardingSeCompletaSoloConTodoLoObligatorio() throws Exception {
        String correo = correoUnico();
        Cookie negocio = sesionNegocioListo(correo, "MiClave2026");
        mvc.perform(get("/api/auth/yo").cookie(negocio)).andExpect(jsonPath("$.onboardingCompleto").value(false));

        mvc.perform(post("/api/perfil/completar-onboarding").with(csrf()).cookie(negocio))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERFIL_INCOMPLETO"));

        Map<String, Object> marca = new LinkedHashMap<>();
        marca.put("logo", subir(negocio, "LOGO", Imagenes.png(500, 500, false), "image/png"));
        marca.put("colorPrimario", "#BE123C");
        marca.put("colorSecundario", "#fde68a");
        marca.put("estilo", "CASERO");
        actualizarPerfil(negocio, marca)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colorPrimario").value("#be123c"))
                .andExpect(jsonPath("$.logoSinTransparencia").value(true))
                .andExpect(jsonPath("$.avisos[0]").value("LOGO_SIN_TRANSPARENCIA"));

        Map<String, Object> respuestas = new LinkedHashMap<>(respuestasPaso2());
        respuestas.put("canales", List.of("WHATSAPP"));
        actualizarPerfil(negocio, Map.of("respuestas", respuestas)).andExpect(status().isOk());
        mvc.perform(post("/api/perfil/completar-onboarding").with(csrf()).cookie(negocio))
                .andExpect(jsonPath("$.mensaje").value("Falta responder: Número de WhatsApp"));

        actualizarPerfil(negocio, Map.of("respuestas", Map.of("whatsapp", "987654321"))).andExpect(status().isOk());
        mvc.perform(post("/api/perfil/completar-onboarding").with(csrf()).cookie(negocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleto").value(true))
                .andExpect(jsonPath("$.respuestas.whatsapp").value("+51987654321"));
        mvc.perform(get("/api/auth/yo").cookie(negocio)).andExpect(jsonPath("$.onboardingCompleto").value(true));

        // Terminado el onboarding, el perfil no puede quedar incompleto (RF-17).
        Map<String, Object> borrarHorario = new HashMap<>();
        borrarHorario.put("horario", null);
        actualizarPerfil(negocio, Map.of("respuestas", borrarHorario))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERFIL_INCOMPLETO"));
    }

    @Test
    void siElGuardadoFallaSePuedeReintentarConLaMismaSubida() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");
        String foto = subir(negocio, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg");
        Map<String, String> fotos = new HashMap<>(fotosCompletas(negocio));
        fotos.put("ENTERO", foto);
        fotos.put("DETALLE", "subidas/negocios/999999/foto_producto/00000000-0000-0000-0000-000000000000.jpg");

        // La foto ENTERO se verifica y se copia, pero DETALLE falla: la transacción se deshace.
        mvc.perform(post("/api/productos").with(csrf()).cookie(negocio)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(solicitud(fotos))))
                .andExpect(status().isBadRequest());
        assertThat(almacen.existe(foto)).as("la subida sigue disponible para reintentar").isTrue();

        fotos.put("DETALLE", subir(negocio, "FOTO_PRODUCTO", Imagenes.jpg(1080, 1920), "image/jpeg"));
        mvc.perform(post("/api/productos").with(csrf()).cookie(negocio)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(solicitud(fotos))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completo").value(true));
        assertThat(almacen.existe(foto)).as("confirmado el guardado, la subida temporal se borra").isFalse();
    }

    @Test
    void laConfiguracionDelNichoTraeLasPreguntasYTemporadas() throws Exception {
        mvc.perform(get("/api/nicho/configuracion").cookie(sesionNegocioListo(correoUnico(), "MiClave2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nicho").value("PASTELERIA"))
                .andExpect(jsonPath("$.estilos.length()").value(3))
                .andExpect(jsonPath("$.preguntas[*].id", hasItem("canales")))
                .andExpect(jsonPath("$.temporadas.length()").value(9));
    }

    @Test
    void laUrlDeSubidaValidaTipoYTamano() throws Exception {
        Cookie negocio = sesionNegocioListo(correoUnico(), "MiClave2026");
        pedirSubida(negocio, "LOGO", "image/jpeg", 1000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("LOGO_DEBE_SER_PNG"));
        pedirSubida(negocio, "FOTO_PRODUCTO", "image/heic", 1000)
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_PERMITIDO"));
        pedirSubida(negocio, "FOTO_PRODUCTO", "image/jpeg", 10L * 1024 * 1024 + 1)
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_DEMASIADO_GRANDE"));
        pedirSubida(negocio, "FOTO_PRODUCTO", "image/jpeg", 2048)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodo").value("PUT"))
                .andExpect(jsonPath("$.cabeceras['Content-Type']").value("image/jpeg"));
    }

    @Test
    void elAdministradorVeElPerfilYElCatalogoDelNegocio() throws Exception {
        String correo = correoUnico();
        Cookie negocio = sesionNegocioListo(correo, "MiClave2026");
        crearProducto(negocio, fotosCompletas(negocio));
        actualizarPerfil(negocio, Map.of("respuestas", Map.of("canales", List.of("WHATSAPP", "TIENDA"))));
        long negocioId = json.readTree(mvc.perform(get("/api/auth/yo").cookie(negocio))
                .andReturn().getResponse().getContentAsString()).get("negocioId").asLong();

        mvc.perform(get("/api/admin/negocios/" + negocioId + "/productos").cookie(sesionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Torta Tres Leches de Fresa"));
        mvc.perform(get("/api/admin/negocios/" + negocioId).cookie(sesionAdmin()))
                .andExpect(jsonPath("$.perfil.respuestas[0].pregunta").value("¿Cómo te piden?"))
                .andExpect(jsonPath("$.perfil.respuestas[0].respuesta").value("Por WhatsApp, En la tienda"));
    }

    // --- ayudas ---

    private Map<String, Object> solicitud(Map<String, String> fotos) {
        return Map.of("nombre", "Torta Tres Leches de Fresa",
                "descripcion", "Bizcocho de tres leches con fresas frescas y crema batida.",
                "temporadas", List.of("VERANO"), "fotos", fotos);
    }

    private ResultActions enviarProducto(Cookie cookie, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/productos").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private JsonNode crearProducto(Cookie cookie, Map<String, String> fotos) throws Exception {
        return json.readTree(enviarProducto(cookie, solicitud(fotos))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions actualizarProducto(Cookie cookie, long id, Map<String, String> fotos) throws Exception {
        return mvc.perform(put("/api/productos/" + id).with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(solicitud(fotos))));
    }

    private ResultActions actualizarPerfil(Cookie cookie, Map<String, Object> cambios) throws Exception {
        return mvc.perform(put("/api/perfil").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cambios)));
    }

    private static String foto(JsonNode producto, String tipo) {
        for (JsonNode foto : producto.get("fotos")) {
            if (foto.get("tipo").asString().equals(tipo)) {
                return foto.get("clave").asString();
            }
        }
        return null;
    }

    private static Map<String, Object> respuestasPaso2() {
        return Map.of("especialidad", List.of("TORTAS_PERSONALIZADAS"), "publico", List.of("FAMILIAS"),
                "ciudad", "Lima", "distrito", "Miraflores", "horario", "Lunes a sábado de 9 a 8");
    }
}
