package com.autoreel.video.aplicacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.autoreel.nicho.dominio.Movimientos.Creatividad;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PlantillaVideo.Estructura;
import com.autoreel.nicho.dominio.PlantillaVideo.ModoDeTomas;
import com.autoreel.video.dominio.DatosDelGuion;
import com.autoreel.video.dominio.GuionPropuesto;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Arma el mensaje para el modelo de texto a partir de las instrucciones del paquete de nicho ({@code guion.md}) y el
 * esquema JSON de la respuesta, y lee lo que el modelo devuelve.
 */
final class InstruccionesDelGuion {

    private static final String SIN_DATO = "(no lo indicó)";

    private InstruccionesDelGuion() {
    }

    /**
     * @param fotos             números de las fotos del producto que van adjuntas, en ese orden.
     * @param erroresAnteriores problemas del intento anterior; vacío en el primero.
     */
    static String armar(DatosDelGuion datos, PaqueteNicho paquete, ModoDeTomas modo, List<Integer> fotos,
            List<String> erroresAnteriores) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("palabrasProhibidas",
                datos.palabrasProhibidas().isEmpty() ? "ninguna" : String.join(", ", datos.palabrasProhibidas()));
        valores.put("tono", paquete.vocabulario().tono(datos.estilo()));
        valores.put("fotos", fotos.stream().map(numero -> "Foto " + numero).collect(Collectors.joining(", ")));
        valores.put("modoDeTomas", modo.indicacion());
        valores.put("estructuras", estructuras(paquete, modo));
        valores.put("creatividades", creatividades(paquete, modo));
        valores.put("distritoHashtag", datos.distritoHashtag());
        valores.put("hashtagsBase", String.join(" ", paquete.plantillaVideo().hashtagsBase()));
        valores.put("maxPalabrasFrase", String.valueOf(paquete.plantillaVideo().maxPalabrasCierre()));
        valores.put("maximoCaracteresCierre", String.valueOf(paquete.plantillaVideo().maximoCaracteresPrecio()));
        valores.put("maximoCaracteresUbicacion",
                String.valueOf(paquete.plantillaVideo().maximoCaracteresUbicacion()));
        valores.put("nombreNegocio", datos.nombreNegocio());
        valores.put("estilo", datos.estiloTexto());
        valores.put("especialidad", datos.especialidad());
        valores.put("publico", datos.publico());
        valores.put("ciudad", datos.ciudad());
        valores.put("distrito", datos.distrito());
        valores.put("distintivo", datos.distintivo());
        valores.put("tipoVideo", datos.tipoVideoTexto());
        valores.put("producto", datos.producto());
        valores.put("descripcionProducto", datos.descripcionProducto());
        valores.put("especial", datos.especial());
        valores.put("precio", datos.precio() == null ? "no escribió ninguno: no menciones precios ni promociones"
                : datos.precio());
        valores.put("extra", datos.extra());

        String texto = paquete.instruccionesGuion();
        for (Map.Entry<String, String> valor : valores.entrySet()) {
            String reemplazo = valor.getValue() == null || valor.getValue().isBlank() ? SIN_DATO : valor.getValue();
            texto = texto.replace("{" + valor.getKey() + "}", reemplazo);
        }
        if (!erroresAnteriores.isEmpty()) {
            texto += "\n\n## Tu respuesta anterior tenía estos problemas; corrígelos\n\n- "
                    + String.join("\n- ", erroresAnteriores) + "\n";
        }
        return texto;
    }

    /**
     * Esquema de la respuesta: la estructura y la creatividad (listas cerradas), la idea, las tomas, los textos del
     * cierre, la descripción y los hashtags. Cuántas tomas, los largos y el contenido los revisa el validador.
     */
    static String esquema(PaqueteNicho paquete, ModoDeTomas modo, List<Integer> fotos, ObjectMapper json) {
        List<Estructura> permitidas = paquete.plantillaVideo().estructurasPara(modo);
        List<String> estructuras = permitidas.stream().map(Estructura::id).toList();
        List<String> creatividades = paquete.movimientos().creatividades().stream()
                .map(Creatividad::id).filter(modo::admiteCreatividad).toList();
        int maximoTomas = permitidas.stream().mapToInt(e -> e.tomas().size()).max().orElse(1);
        Map<String, Object> textoOpcional = Map.of("type", List.of("string", "null"));
        Map<String, Object> plano = Map.of(
                "type", "object",
                "properties", Map.of(
                        "orden", Map.of("type", "integer", "minimum", 1, "maximum", maximoTomas),
                        "foto", Map.of("type", "integer", "enum", fotos),
                        "toma", Map.of("type", "string"),
                        "descripcionToma", Map.of("type", "string"),
                        "texto", textoOpcional),
                "required", List.of("orden", "foto", "toma", "descripcionToma", "texto"));
        Map<String, Object> cierre = Map.of(
                "type", "object",
                "properties", Map.of(
                        "precio", textoOpcional,
                        "frase", Map.of("type", "string"),
                        "ubicacion", textoOpcional),
                "required", List.of("precio", "frase", "ubicacion"));
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("estructura", Map.of("type", "string", "enum", estructuras));
        propiedades.put("creatividad", Map.of("type", "string", "enum", creatividades));
        propiedades.put("idea", Map.of("type", "string"));
        propiedades.put("planos", Map.of("type", "array", "items", plano, "minItems", 1, "maxItems", maximoTomas));
        propiedades.put("cierre", cierre);
        propiedades.put("descripcion", Map.of("type", "string"));
        propiedades.put("hashtags", Map.of("type", "array", "items", Map.of("type", "string")));
        Map<String, Object> esquema = Map.of(
                "type", "object",
                "properties", propiedades,
                "required", List.copyOf(propiedades.keySet()));
        return json.writeValueAsString(esquema);
    }

    /** El guion de la respuesta, o vacío si no es un JSON con esa forma. */
    static Optional<GuionPropuesto> leer(String respuesta, ObjectMapper json) {
        JsonNode raiz;
        try {
            raiz = json.readTree(respuesta);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (raiz == null || !raiz.isObject() || !raiz.path("planos").isArray()) {
            return Optional.empty();
        }
        List<GuionPropuesto.PlanoPropuesto> planos = new ArrayList<>();
        for (JsonNode plano : raiz.path("planos")) {
            planos.add(new GuionPropuesto.PlanoPropuesto(entero(plano.path("orden")), entero(plano.path("foto")),
                    texto(plano.path("toma")), texto(plano.path("descripcionToma")), texto(plano.path("texto"))));
        }
        List<String> hashtags = new ArrayList<>();
        for (JsonNode hashtag : raiz.path("hashtags")) {
            if (hashtag.isString()) {
                hashtags.add(hashtag.asString());
            }
        }
        JsonNode cierre = raiz.path("cierre");
        GuionPropuesto.CierrePropuesto cierrePropuesto = cierre.isObject()
                ? new GuionPropuesto.CierrePropuesto(texto(cierre.path("precio")), texto(cierre.path("frase")),
                        texto(cierre.path("ubicacion")))
                : null;
        return Optional.of(new GuionPropuesto(texto(raiz.path("estructura")), texto(raiz.path("creatividad")),
                texto(raiz.path("idea")), planos, cierrePropuesto, texto(raiz.path("descripcion")), hashtags));
    }

    private static String estructuras(PaqueteNicho paquete, ModoDeTomas modo) {
        List<String> lineas = new ArrayList<>();
        for (Estructura estructura : paquete.plantillaVideo().estructurasPara(modo)) {
            lineas.add("- " + estructura.id() + " (" + estructura.nombre() + "): " + estructura.descripcion() + " "
                    + estructura.tomas().size() + (estructura.tomas().size() == 1 ? " toma de " : " tomas de ")
                    + estructura.tomas().stream().map(String::valueOf).collect(Collectors.joining(", "))
                    + " segundos y cierre de " + estructura.cierre() + " segundos; texto en pantalla de máximo "
                    + estructura.maxPalabras() + " palabras por toma.");
        }
        return String.join("\n", lineas);
    }

    private static String creatividades(PaqueteNicho paquete, ModoDeTomas modo) {
        return String.join("\n", paquete.movimientos().creatividades().stream()
                .filter(creatividad -> modo.admiteCreatividad(creatividad.id()))
                .map(creatividad -> "- " + creatividad.id() + " (" + creatividad.nombre() + "): "
                        + creatividad.descripcion() + " Cómo escribir la toma: " + creatividad.guia())
                .toList());
    }

    private static Integer entero(JsonNode nodo) {
        return nodo.isIntegralNumber() ? nodo.asInt() : null;
    }

    private static String texto(JsonNode nodo) {
        return nodo.isString() ? nodo.asString() : null;
    }
}
