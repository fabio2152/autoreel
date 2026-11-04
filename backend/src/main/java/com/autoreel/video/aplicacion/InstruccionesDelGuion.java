package com.autoreel.video.aplicacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.nicho.dominio.Movimientos.Movimiento;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PlantillaVideo.PlanoPlantilla;
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

    /** @param erroresAnteriores problemas del intento anterior; vacío en el primero. */
    static String armar(DatosDelGuion datos, PaqueteNicho paquete, List<String> erroresAnteriores) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("palabrasProhibidas",
                datos.palabrasProhibidas().isEmpty() ? "ninguna" : String.join(", ", datos.palabrasProhibidas()));
        valores.put("tono", paquete.vocabulario().tono(datos.estilo()));
        valores.put("plantilla", plantilla(paquete));
        valores.put("movimientos", movimientos(datos, paquete));
        valores.put("distritoHashtag", datos.distritoHashtag());
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
     * Esquema de la respuesta: los planos con IA, la descripción y los hashtags. Los movimientos van como lista
     * cerrada; los largos los revisa el validador.
     */
    static String esquema(DatosDelGuion datos, PaqueteNicho paquete, ObjectMapper json) {
        List<PlanoPlantilla> planos = paquete.plantillaVideo().planosConIa();
        List<Integer> ordenes = planos.stream().map(PlanoPlantilla::orden).toList();
        List<String> movimientos = paquete.movimientos().para(datos.tipoVideo()).stream().map(Movimiento::id).toList();
        Map<String, Object> plano = Map.of(
                "type", "object",
                "properties", Map.of(
                        "orden", Map.of("type", "integer", "enum", ordenes),
                        "movimiento", Map.of("type", "string", "enum", movimientos),
                        "texto", Map.of("type", "string")),
                "required", List.of("orden", "movimiento", "texto"));
        Map<String, Object> esquema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "planos", Map.of("type", "array", "items", plano,
                                "minItems", planos.size(), "maxItems", planos.size()),
                        "descripcion", Map.of("type", "string"),
                        "hashtags", Map.of("type", "array", "items", Map.of("type", "string"))),
                "required", List.of("planos", "descripcion", "hashtags"));
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
            Integer orden = plano.path("orden").isIntegralNumber() ? plano.path("orden").asInt() : null;
            planos.add(new GuionPropuesto.PlanoPropuesto(orden, texto(plano.path("movimiento")),
                    texto(plano.path("texto"))));
        }
        List<String> hashtags = new ArrayList<>();
        for (JsonNode hashtag : raiz.path("hashtags")) {
            if (hashtag.isString()) {
                hashtags.add(hashtag.asString());
            }
        }
        return Optional.of(new GuionPropuesto(planos, texto(raiz.path("descripcion")), hashtags));
    }

    private static String plantilla(PaqueteNicho paquete) {
        List<String> lineas = new ArrayList<>();
        for (PlanoPlantilla plano : paquete.plantillaVideo().planosConIa()) {
            lineas.add("- Plano " + plano.orden() + " (" + plano.nombre() + "): foto " + plano.foto()
                    + ", " + plano.segundos() + " segundos, texto en pantalla de máximo " + plano.maxPalabras()
                    + " palabras.");
        }
        PlanoPlantilla cierre = paquete.plantillaVideo().cierre();
        lineas.add("- Plano " + cierre.orden() + " (" + cierre.nombre() + "): lo arma el sistema; no lo escribas.");
        return String.join("\n", lineas);
    }

    private static String movimientos(DatosDelGuion datos, PaqueteNicho paquete) {
        return String.join("; ", paquete.movimientos().para(datos.tipoVideo()).stream()
                .map(movimiento -> movimiento.id() + " (" + movimiento.descripcion() + ")")
                .toList());
    }

    private static String texto(JsonNode nodo) {
        return nodo.isString() ? nodo.asString() : null;
    }
}
