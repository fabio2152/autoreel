package com.autoreel.ia.infraestructura;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ErrorDeProveedor.Tipo;
import com.autoreel.ia.dominio.GeneradorDeClips;
import com.autoreel.ia.dominio.ModeloIA;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

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
    private static final String NOMBRE = "Veo";

    private final GoogleCloud googleCloud;

    public VeoGeneradorDeClips(GoogleCloud googleCloud) {
        this.googleCloud = googleCloud;
    }

    @Override
    public String adaptador() {
        return ADAPTADOR;
    }

    @Override
    public String iniciar(ModeloIA modelo, String credencial, SolicitudDeClip solicitud) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("aspectRatio", GoogleCloud.parametro(modelo, "relacionAspecto", "9:16"));
        parametros.put("durationSeconds", solicitud.segundos());
        parametros.put("sampleCount", 1);
        if (solicitud.negativos() != null && !solicitud.negativos().isBlank()) {
            parametros.put("negativePrompt", solicitud.negativos());
        }
        parametros.put("personGeneration", "dont_allow");
        parametros.put("generateAudio", modelo.getParametros() != null
                && Boolean.TRUE.equals(modelo.getParametros().get("generarAudio")));
        parametros.put("resolution", GoogleCloud.parametro(modelo, "resolucion", "720p"));
        parametros.put("resizeMode", GoogleCloud.parametro(modelo, "ajuste", "crop"));
        Map<String, Object> cuerpo = Map.of(
                "instances", List.of(Map.of(
                        "prompt", solicitud.prompt(),
                        "image", Map.of("bytesBase64Encoded", Base64.getEncoder().encodeToString(solicitud.foto()),
                                "mimeType", solicitud.tipoFoto()))),
                "parameters", parametros);
        JsonNode respuesta = googleCloud.llamar(modelo, credencial, region(modelo), "predictLongRunning", cuerpo,
                NOMBRE);
        String nombre = respuesta == null ? "" : respuesta.path("name").asString("");
        if (nombre.isBlank()) {
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, "Veo no devolvió el identificador de la tarea");
        }
        return nombre;
    }

    @Override
    public EstadoDeClip consultar(ModeloIA modelo, String credencial, String idTarea) {
        JsonNode respuesta = googleCloud.llamar(modelo, credencial, region(modelo), "fetchPredictOperation",
                Map.of("operationName", idTarea), NOMBRE);
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
        return new Listo(Base64.getDecoder().decode(base64), video.path("mimeType").asString("video/mp4"), null);
    }

    /**
     * Verifica la credencial sin crear un clip, que costaría. Con la cuenta de servicio pide un token. Con una clave de
     * API consulta una tarea que no existe: si Google responde que no la encuentra (400/404), la clave y el proyecto
     * sirven; si la rechaza (401/403), no.
     */
    @Override
    public void probar(ModeloIA modelo, String credencial) {
        if (!GoogleCloud.esClaveDeApi(credencial)) {
            googleCloud.verificarCredencial(credencial);
            return;
        }
        String proyecto = GoogleCloud.proyectoDelModelo(modelo);
        if (proyecto == null) {
            throw new ErrorDeProveedor(Tipo.CONFIGURACION_INVALIDA, "Con una clave de API, Veo necesita el ID del "
                    + "proyecto de Google Cloud: agrégalo como \"proyecto\" en los parámetros del modelo");
        }
        String tareaInexistente = "projects/" + proyecto + "/locations/" + region(modelo) + "/publishers/google/models/"
                + modelo.getIdentificadorModelo() + "/operations/00000000-0000-0000-0000-000000000000";
        try {
            googleCloud.llamar(modelo, credencial, region(modelo), "fetchPredictOperation",
                    Map.of("operationName", tareaInexistente), NOMBRE);
        } catch (ErrorDeProveedor e) {
            if (e.getTipo() != Tipo.CONFIGURACION_INVALIDA) {
                throw e;
            }
        }
    }

    private static String region(ModeloIA modelo) {
        return GoogleCloud.parametro(modelo, "region", REGION_POR_DEFECTO);
    }
}
