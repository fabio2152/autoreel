package com.autoreel.ia.infraestructura;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ErrorDeProveedor.Tipo;
import com.autoreel.ia.dominio.Guionista;
import com.autoreel.ia.dominio.ModeloIA;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador GEMINI (sección 11.2): API generateContent de Google con respuesta JSON según un esquema. El
 * identificador del modelo y la temperatura salen del registro {@link ModeloIA}, así que un modelo nuevo de Gemini
 * se agrega desde la base de datos sin programar (RN-56).
 */
@Component
public class GeminiGuionista implements Guionista {

    static final String ADAPTADOR = "GEMINI";
    private static final double TEMPERATURA_POR_DEFECTO = 0.7;

    /**
     * @param url           base de la API; en las pruebas apunta a un servidor simulado.
     * @param tiempoConexion espera máxima para conectar.
     * @param tiempoLectura  espera máxima de la respuesta.
     */
    @ConfigurationProperties("autoreel.ia.gemini")
    public record PropiedadesGemini(String url, Duration tiempoConexion, Duration tiempoLectura) {
    }

    private final RestClient cliente;
    private final ObjectMapper json;

    public GeminiGuionista(PropiedadesGemini propiedades, ObjectMapper json) {
        var fabrica = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(propiedades.tiempoConexion()).build());
        fabrica.setReadTimeout(propiedades.tiempoLectura());
        this.cliente = RestClient.builder().baseUrl(propiedades.url()).requestFactory(fabrica).build();
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
        JsonNode respuesta;
        try {
            respuesta = cliente.post()
                    .uri("/v1beta/models/{modelo}:generateContent", modelo.getIdentificadorModelo())
                    .header("x-goog-api-key", credencial)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw clasificar(e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Gemini no respondió a tiempo o no se pudo conectar", e);
        }
        return leer(respuesta);
    }

    private RespuestaDelModelo leer(JsonNode respuesta) {
        if (respuesta == null) {
            return RespuestaDelModelo.sinTexto("Gemini respondió vacío");
        }
        JsonNode bloqueo = respuesta.path("promptFeedback").path("blockReason");
        if (!bloqueo.isMissingNode() && !bloqueo.isNull()) {
            return RespuestaDelModelo.sinTexto("Gemini bloqueó la solicitud: " + bloqueo.asString());
        }
        JsonNode candidato = respuesta.path("candidates").path(0);
        if (candidato.isMissingNode()) {
            return RespuestaDelModelo.sinTexto("Gemini no devolvió ninguna respuesta");
        }
        String fin = candidato.path("finishReason").asString("STOP");
        if (!fin.equals("STOP")) {
            return RespuestaDelModelo.sinTexto("La respuesta de Gemini terminó antes de tiempo (" + fin + ")");
        }
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : candidato.path("content").path("parts")) {
            if (!parte.path("thought").asBoolean(false) && parte.has("text")) {
                texto.append(parte.get("text").asString());
            }
        }
        return texto.isEmpty() ? RespuestaDelModelo.sinTexto("Gemini devolvió una respuesta sin texto")
                : RespuestaDelModelo.conTexto(texto.toString());
    }

    private static ErrorDeProveedor clasificar(HttpStatusCode estado, Exception causa) {
        int codigo = estado.value();
        if (codigo == 401 || codigo == 403) {
            return new ErrorDeProveedor(Tipo.CREDENCIAL_INVALIDA, "Gemini rechazó la credencial (" + codigo + ")", causa);
        }
        if (codigo == 400 || codigo == 404) {
            return new ErrorDeProveedor(Tipo.CONFIGURACION_INVALIDA,
                    "Gemini rechazó la petición; revisa el identificador del modelo (" + codigo + ")", causa);
        }
        return new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Gemini no está disponible (" + codigo + ")", causa);
    }

    private static double temperatura(ModeloIA modelo) {
        Object valor = modelo.getParametros() == null ? null : modelo.getParametros().get("temperatura");
        return valor instanceof Number numero ? numero.doubleValue() : TEMPERATURA_POR_DEFECTO;
    }
}
