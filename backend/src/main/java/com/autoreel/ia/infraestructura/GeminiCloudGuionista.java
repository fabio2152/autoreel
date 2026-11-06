package com.autoreel.ia.infraestructura;

import java.util.List;
import java.util.Map;

import com.autoreel.ia.dominio.Guionista;
import com.autoreel.ia.dominio.ModeloIA;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador GEMINI_CLOUD: el mismo Gemini de texto, pero en Google Cloud (Vertex AI, hoy "Gemini Enterprise Agent
 * Platform") con la cuenta de servicio, así una sola credencial de Google Cloud sirve para el guion y para Veo. La
 * ubicación sale de los parámetros del modelo ({@code ubicacion}, por defecto {@code global}, la que Google recomienda
 * para los Gemini actuales).
 */
@Component
public class GeminiCloudGuionista implements Guionista {

    static final String ADAPTADOR = "GEMINI_CLOUD";
    private static final double TEMPERATURA_POR_DEFECTO = 0.7;
    private static final String NOMBRE = "Gemini en Google Cloud";

    private final GoogleCloud googleCloud;
    private final ObjectMapper json;

    public GeminiCloudGuionista(GoogleCloud googleCloud, ObjectMapper json) {
        this.googleCloud = googleCloud;
        this.json = json;
    }

    @Override
    public String adaptador() {
        return ADAPTADOR;
    }

    @Override
    public RespuestaDelModelo escribir(ModeloIA modelo, String credencial, String instrucciones, String esquemaJson) {
        Map<String, Object> cuerpo = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", instrucciones)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", json.readTree(esquemaJson),
                        "temperature", temperatura(modelo)));
        JsonNode respuesta = googleCloud.llamar(modelo, credencial,
                GoogleCloud.parametro(modelo, "ubicacion", GoogleCloud.UBICACION_GLOBAL), "generateContent", cuerpo,
                NOMBRE);
        return RespuestaGemini.leer(respuesta, NOMBRE);
    }

    private static double temperatura(ModeloIA modelo) {
        Object valor = modelo.getParametros() == null ? null : modelo.getParametros().get("temperatura");
        return valor instanceof Number numero ? numero.doubleValue() : TEMPERATURA_POR_DEFECTO;
    }
}
