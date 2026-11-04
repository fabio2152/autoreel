package com.autoreel.ia.infraestructura;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ErrorDeProveedor.Tipo;
import com.autoreel.ia.dominio.GeneradorDeClips;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.infraestructura.TokenDeGoogle.CuentaDeServicio;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador VEO (sección 11.1): Veo en Google Cloud (Vertex AI), imagen a video. El identificador del modelo, la
 * región, la duración y el resto de parámetros salen del registro {@link ModeloIA}; la credencial es el JSON de una
 * cuenta de servicio (RN-55). La tarea se crea con {@code predictLongRunning} y se consulta con
 * {@code fetchPredictOperation}; sin {@code storageUri}, el clip vuelve en la respuesta.
 */
@Component
public class VeoGeneradorDeClips implements GeneradorDeClips {

    static final String ADAPTADOR = "VEO";
    private static final String REGION_POR_DEFECTO = "us-central1";

    /**
     * @param url            base de la API con {region}; en las pruebas, un servidor simulado.
     * @param urlToken       endpoint de tokens de Google.
     */
    @ConfigurationProperties("autoreel.ia.veo")
    public record PropiedadesVeo(String url, String urlToken, Duration tiempoConexion, Duration tiempoLectura) {
    }

    private final RestClient cliente;
    private final TokenDeGoogle tokens;
    private final PropiedadesVeo propiedades;
    private final ObjectMapper json;

    public VeoGeneradorDeClips(PropiedadesVeo propiedades, ObjectMapper json, Clock reloj) {
        var fabrica = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(propiedades.tiempoConexion()).build());
        fabrica.setReadTimeout(propiedades.tiempoLectura());
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
        this.tokens = new TokenDeGoogle(cliente, propiedades.urlToken(), json, reloj);
        this.propiedades = propiedades;
        this.json = json;
    }

    @Override
    public String adaptador() {
        return ADAPTADOR;
    }

    @Override
    public String iniciar(ModeloIA modelo, String credencial, SolicitudDeClip solicitud) {
        CuentaDeServicio cuenta = tokens.cuenta(credencial);
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("aspectRatio", parametro(modelo, "relacionAspecto", "9:16"));
        parametros.put("durationSeconds", solicitud.segundos());
        parametros.put("sampleCount", 1);
        parametros.put("negativePrompt", solicitud.prohibiciones());
        parametros.put("personGeneration", "dont_allow");
        parametros.put("generateAudio", modelo.getParametros() != null
                && Boolean.TRUE.equals(modelo.getParametros().get("generarAudio")));
        parametros.put("resolution", parametro(modelo, "resolucion", "720p"));
        parametros.put("resizeMode", parametro(modelo, "ajuste", "crop"));
        Map<String, Object> cuerpo = Map.of(
                "instances", List.of(Map.of(
                        "prompt", solicitud.instruccion(),
                        "image", Map.of("bytesBase64Encoded", Base64.getEncoder().encodeToString(solicitud.foto()),
                                "mimeType", solicitud.tipoFoto()))),
                "parameters", parametros);
        JsonNode respuesta = llamar(modelo, cuenta, "predictLongRunning", cuerpo);
        String nombre = respuesta == null ? "" : respuesta.path("name").asString("");
        if (nombre.isBlank()) {
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Veo no devolvió el identificador de la tarea");
        }
        return nombre;
    }

    @Override
    public EstadoDeClip consultar(ModeloIA modelo, String credencial, String idTarea) {
        CuentaDeServicio cuenta = tokens.cuenta(credencial);
        JsonNode respuesta = llamar(modelo, cuenta, "fetchPredictOperation", Map.of("operationName", idTarea));
        if (respuesta == null || !respuesta.path("done").asBoolean(false)) {
            return new EnCurso();
        }
        JsonNode error = respuesta.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            return new Fallido("Veo no pudo crear el clip: " + error.path("message").asString("sin detalle"));
        }
        JsonNode resultado = respuesta.path("response");
        JsonNode video = resultado.path("videos").path(0);
        if (video.isMissingNode()) {
            int filtrados = resultado.path("raiMediaFilteredCount").asInt(0);
            return new Fallido(filtrados > 0 ? "El filtro de contenido de Veo descartó el clip"
                    : "Veo terminó sin devolver el clip");
        }
        String base64 = video.path("bytesBase64Encoded").asString("");
        if (base64.isEmpty()) {
            return new Fallido("Veo devolvió el clip en un depósito de Google Cloud en lugar de la respuesta");
        }
        return new Listo(Base64.getDecoder().decode(base64), video.path("mimeType").asString("video/mp4"));
    }

    private JsonNode llamar(ModeloIA modelo, CuentaDeServicio cuenta, String metodo, Object cuerpo) {
        String region = parametro(modelo, "region", REGION_POR_DEFECTO);
        String url = propiedades.url().replace("{region}", region)
                + "/v1/projects/" + cuenta.proyecto() + "/locations/" + region + "/publishers/google/models/"
                + modelo.getIdentificadorModelo() + ":" + metodo;
        try {
            return cliente.post().uri(url)
                    .header("Authorization", "Bearer " + tokens.token(cuenta))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(cuerpo))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            if (codigo == 401 || codigo == 403) {
                tokens.olvidar(cuenta);
                throw new ErrorDeProveedor(Tipo.CREDENCIAL_INVALIDA,
                        "Google Cloud rechazó la cuenta de servicio (" + codigo + ")", e);
            }
            if (codigo == 400 || codigo == 404) {
                throw new ErrorDeProveedor(Tipo.CONFIGURACION_INVALIDA,
                        "Veo rechazó la petición; revisa el modelo y sus parámetros (" + codigo + ")", e);
            }
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Veo no está disponible (" + codigo + ")", e);
        } catch (ResourceAccessException e) {
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Veo no respondió a tiempo o no se pudo conectar", e);
        }
    }

    private static String parametro(ModeloIA modelo, String nombre, String porDefecto) {
        Object valor = modelo.getParametros() == null ? null : modelo.getParametros().get(nombre);
        return valor == null ? porDefecto : String.valueOf(valor);
    }
}
