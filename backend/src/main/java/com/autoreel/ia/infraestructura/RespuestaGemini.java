package com.autoreel.ia.infraestructura;

import com.autoreel.ia.dominio.Guionista.RespuestaDelModelo;
import tools.jackson.databind.JsonNode;

/**
 * Lee la respuesta de {@code generateContent}, igual en la API de Gemini (AI Studio) y en Google Cloud (Vertex AI):
 * el texto del primer candidato, sin las partes de razonamiento. Una respuesta bloqueada, cortada o sin texto vuelve
 * sin texto y con el motivo, y cuenta como un intento.
 */
final class RespuestaGemini {

    private RespuestaGemini() {
    }

    static RespuestaDelModelo leer(JsonNode respuesta, String nombre) {
        if (respuesta == null) {
            return RespuestaDelModelo.sinTexto(nombre + " respondió vacío");
        }
        JsonNode bloqueo = respuesta.path("promptFeedback").path("blockReason");
        if (!bloqueo.isMissingNode() && !bloqueo.isNull()) {
            return RespuestaDelModelo.sinTexto(nombre + " bloqueó la solicitud: " + bloqueo.asString());
        }
        JsonNode candidato = respuesta.path("candidates").path(0);
        if (candidato.isMissingNode()) {
            return RespuestaDelModelo.sinTexto(nombre + " no devolvió ninguna respuesta");
        }
        String fin = candidato.path("finishReason").asString("STOP");
        if (!fin.equals("STOP")) {
            return RespuestaDelModelo.sinTexto("La respuesta de " + nombre + " terminó antes de tiempo (" + fin + ")");
        }
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : candidato.path("content").path("parts")) {
            if (!parte.path("thought").asBoolean(false) && parte.has("text")) {
                texto.append(parte.get("text").asString());
            }
        }
        return texto.isEmpty() ? RespuestaDelModelo.sinTexto(nombre + " devolvió una respuesta sin texto")
                : RespuestaDelModelo.conTexto(texto.toString());
    }
}
