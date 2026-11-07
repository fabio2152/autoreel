package com.autoreel.ia.infraestructura;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ErrorDeProveedor.Tipo;
import com.autoreel.ia.dominio.GeneradorDeClips;
import com.autoreel.ia.dominio.ModeloIA;
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
 * Adaptador GEMINI_OMNI: Gemini Omni Flash en la API de Gemini (Google AI Studio), imagen a video, con la misma clave
 * que el guion. Usa la API de interacciones ({@code POST /v1beta/interactions}).
 * <ul>
 * <li>Por defecto, la generación es síncrona (lo que documenta Google para Omni): la creación devuelve el video y se
 * guarda hasta la siguiente consulta. Con {@code segundoPlano: true} en los parámetros del modelo se usa
 * {@code background} y se consulta con {@code GET /v1beta/interactions/{id}}.</li>
 * <li>Omni no admite prompt negativo: las prohibiciones van en el mismo texto. Siempre genera audio, que la composición
 * descarta.</li>
 * <li>El costo usa los tokens de video de la respuesta ({@code usage.output_tokens_by_modality}) si vienen.</li>
 * </ul>
 */
@Component
public class OmniGeneradorDeClips implements GeneradorDeClips {

    static final String ADAPTADOR = "GEMINI_OMNI";
    private static final int TOKENS_POR_SEGUNDO_720P = 5792;
    private static final Set<String> TERMINADOS = Set.of("completed", "failed", "cancelled", "incomplete");
    /** Resultados síncronos que esperan su consulta; si el proceso se reinicia, la consulta los pide otra vez. */
    private static final int MAXIMO_GUARDADOS = 30;

    /** @param tiempoLectura la generación síncrona puede tardar minutos. */
    @ConfigurationProperties("autoreel.ia.omni")
    public record PropiedadesOmni(Duration tiempoLectura) {

        public PropiedadesOmni {
            tiempoLectura = tiempoLectura == null ? Duration.ofMinutes(5) : tiempoLectura;
        }
    }

    private final RestClient cliente;
    private final ObjectMapper json;
    private final Map<String, EstadoDeClip> guardados = new ConcurrentHashMap<>();

    public OmniGeneradorDeClips(GeminiGuionista.PropiedadesGemini gemini, PropiedadesOmni omni, ObjectMapper json) {
        var fabrica = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(gemini.tiempoConexion()).build());
        fabrica.setReadTimeout(omni.tiempoLectura());
        this.cliente = RestClient.builder().baseUrl(gemini.url()).requestFactory(fabrica).build();
        this.json = json;
    }

    @Override
    public String adaptador() {
        return ADAPTADOR;
    }

    @Override
    public String iniciar(ModeloIA modelo, String credencial, SolicitudDeClip solicitud) {
        boolean segundoPlano = Boolean.TRUE.equals(parametro(modelo, "segundoPlano"));
        Map<String, Object> formato = new LinkedHashMap<>();
        formato.put("type", "video");
        formato.put("aspect_ratio", texto(modelo, "relacionAspecto", "9:16"));
        formato.put("resolution", texto(modelo, "resolucion", "720p"));
        formato.put("duration", solicitud.segundos() + "s");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", modelo.getIdentificadorModelo());
        cuerpo.put("input", List.of(
                Map.of("type", "image", "data", Base64.getEncoder().encodeToString(solicitud.foto()),
                        "mime_type", solicitud.tipoFoto()),
                Map.of("type", "text", "text", instruccion(solicitud))));
        cuerpo.put("response_format", formato);
        Object tarea = parametro(modelo, "tarea");
        if (tarea != null) {
            cuerpo.put("generation_config", Map.of("video_config", Map.of("task", String.valueOf(tarea))));
        }
        cuerpo.put("background", segundoPlano);
        // Guardar la interacción permite consultarla después (y es obligatorio en segundo plano).
        cuerpo.put("store", true);

        JsonNode respuesta = llamar(() -> cliente.post().uri("/v1beta/interactions")
                .header("x-goog-api-key", credencial)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(cuerpo))
                .retrieve()
                .body(JsonNode.class));
        String id = respuesta == null ? "" : respuesta.path("id").asString("");
        if (id.isBlank()) {
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Gemini Omni no devolvió el identificador de la interacción");
        }
        if (TERMINADOS.contains(respuesta.path("status").asString(""))) {
            if (guardados.size() >= MAXIMO_GUARDADOS) {
                guardados.clear();
            }
            guardados.put(id, leer(respuesta, modelo));
        }
        return id;
    }

    @Override
    public EstadoDeClip consultar(ModeloIA modelo, String credencial, String idTarea) {
        EstadoDeClip guardado = guardados.remove(idTarea);
        if (guardado != null) {
            return guardado;
        }
        JsonNode respuesta = llamar(() -> cliente.get().uri("/v1beta/interactions/{id}", idTarea)
                .header("x-goog-api-key", credencial)
                .retrieve()
                .body(JsonNode.class));
        return leer(respuesta, modelo);
    }

    /** Consulta la ficha del modelo ({@code GET /v1beta/models/{modelo}}) con la clave: no genera ningún clip. */
    @Override
    public void probar(ModeloIA modelo, String credencial) {
        llamar(() -> cliente.get().uri("/v1beta/models/{modelo}", modelo.getIdentificadorModelo())
                .header("x-goog-api-key", credencial)
                .retrieve()
                .body(JsonNode.class));
    }

    private EstadoDeClip leer(JsonNode respuesta, ModeloIA modelo) {
        if (respuesta == null) {
            return new EnCurso();
        }
        String estado = respuesta.path("status").asString("");
        if (!TERMINADOS.contains(estado)) {
            return new EnCurso();
        }
        if (!estado.equals("completed")) {
            String detalle = respuesta.path("errors").path(0).path("message").asString(estado);
            return new Fallido("Gemini Omni no pudo crear el clip: " + detalle);
        }
        for (JsonNode paso : respuesta.path("steps")) {
            for (JsonNode contenido : paso.path("content")) {
                if (contenido.path("type").asString("").equals("video")) {
                    String datos = contenido.path("data").asString("");
                    if (datos.isEmpty()) {
                        return new Fallido("Gemini Omni devolvió una dirección en lugar del video");
                    }
                    return new Listo(Base64.getDecoder().decode(datos),
                            contenido.path("mime_type").asString("video/mp4"), segundos(respuesta, modelo));
                }
            }
        }
        return new Fallido("Gemini Omni terminó sin devolver el video (pudo bloquearlo su filtro de contenido)");
    }

    /** Segundos generados a partir de los tokens de video que informa la respuesta; vacío si no vienen. */
    private static Double segundos(JsonNode respuesta, ModeloIA modelo) {
        Object porSegundo = parametro(modelo, "tokensPorSegundo");
        double tokensPorSegundo = porSegundo instanceof Number numero ? numero.doubleValue() : TOKENS_POR_SEGUNDO_720P;
        for (JsonNode modalidad : respuesta.path("usage").path("output_tokens_by_modality")) {
            if (modalidad.path("modality").asString("").equalsIgnoreCase("video")) {
                long tokens = modalidad.path("tokens").asLong(0);
                return tokens > 0 ? tokens / tokensPorSegundo : null;
            }
        }
        return null;
    }

    /** El movimiento y, como Omni no admite prompt negativo, lo prohibido en el mismo texto. */
    private static String instruccion(SolicitudDeClip solicitud) {
        return "Animate this exact photo as the first frame. " + solicitud.instruccion()
                + " Keep the product identical to the photo. No dialogue and no voice-over."
                + " Never show: " + solicitud.prohibiciones() + ".";
    }

    private JsonNode llamar(java.util.function.Supplier<JsonNode> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            if (codigo == 401 || codigo == 403) {
                throw new ErrorDeProveedor(Tipo.CREDENCIAL_INVALIDA,
                        "Gemini Omni rechazó la clave (" + codigo + ")", e);
            }
            if (codigo == 400 || codigo == 404) {
                throw new ErrorDeProveedor(Tipo.CONFIGURACION_INVALIDA,
                        "Gemini Omni rechazó la petición; revisa el modelo y sus parámetros (" + codigo + ")", e);
            }
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Gemini Omni no está disponible (" + codigo + ")", e);
        } catch (ResourceAccessException e) {
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Gemini Omni no respondió a tiempo o no se pudo conectar", e);
        }
    }

    private static Object parametro(ModeloIA modelo, String nombre) {
        return modelo.getParametros() == null ? null : modelo.getParametros().get(nombre);
    }

    private static String texto(ModeloIA modelo, String nombre, String porDefecto) {
        Object valor = parametro(modelo, nombre);
        return valor == null ? porDefecto : String.valueOf(valor);
    }
}
