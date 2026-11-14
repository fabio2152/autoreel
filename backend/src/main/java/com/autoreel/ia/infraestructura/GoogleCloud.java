package com.autoreel.ia.infraestructura;

import java.time.Clock;
import java.time.Duration;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ErrorDeProveedor.Tipo;
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
 * Llamadas a los modelos de Google en Google Cloud (Vertex AI, hoy "Gemini Enterprise Agent Platform"). La comparten
 * los adaptadores de Google Cloud: Veo (video) y Gemini (texto). La credencial (RN-55) puede ser:
 * <ul>
 * <li>el JSON de una cuenta de servicio: el proyecto sale de ese JSON y se pide un token;</li>
 * <li>una clave de API de Google Cloud (2026-11-14): va en {@code x-goog-api-key} y el proyecto sale del parámetro
 * {@code proyecto} del modelo. Sin proyecto se usa el modo exprés, que sirve para Gemini pero no para Veo.</li>
 * </ul>
 * La ubicación sale de los parámetros del modelo.
 */
@Component
public class GoogleCloud {

    static final String UBICACION_GLOBAL = "global";

    /**
     * @param url        base de la API regional, con {region}; en las pruebas, un servidor simulado.
     * @param urlGlobal  base de la API para la ubicación global.
     * @param urlToken   endpoint de tokens de Google.
     */
    @ConfigurationProperties("autoreel.ia.google-cloud")
    public record PropiedadesGoogleCloud(String url, String urlGlobal, String urlToken, Duration tiempoConexion,
            Duration tiempoLectura) {
    }

    private final RestClient cliente;
    private final TokenDeGoogle tokens;
    private final PropiedadesGoogleCloud propiedades;
    private final ObjectMapper json;

    public GoogleCloud(PropiedadesGoogleCloud propiedades, ObjectMapper json, Clock reloj) {
        var fabrica = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(propiedades.tiempoConexion()).build());
        fabrica.setReadTimeout(propiedades.tiempoLectura());
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
        this.tokens = new TokenDeGoogle(cliente, propiedades.urlToken(), json, reloj);
        this.propiedades = propiedades;
        this.json = json;
    }

    /**
     * {@code POST .../projects/{proyecto}/locations/{ubicacion}/publishers/google/models/{modelo}:{metodo}}.
     *
     * @param nombreProveedor para los mensajes de error ("Veo", "Gemini en Google Cloud").
     * @throws ErrorDeProveedor con el tipo según la respuesta; nunca incluye la credencial.
     */
    public JsonNode llamar(ModeloIA modelo, String credencial, String ubicacion, String metodo, Object cuerpo,
            String nombreProveedor) {
        boolean conClave = esClaveDeApi(credencial);
        CuentaDeServicio cuenta = conClave ? null : tokens.cuenta(credencial);
        String proyecto = conClave ? proyectoDelModelo(modelo) : cuenta.proyecto();
        String base = UBICACION_GLOBAL.equals(ubicacion) || proyecto == null ? propiedades.urlGlobal()
                : propiedades.url().replace("{region}", ubicacion);
        // Con clave de API y sin proyecto se usa el modo exprés (sin proyecto en la ruta): sirve para Gemini, no para Veo.
        String url = base + "/v1" + (proyecto == null ? "" : "/projects/" + proyecto + "/locations/" + ubicacion)
                + "/publishers/google/models/" + modelo.getIdentificadorModelo() + ":" + metodo;
        try {
            var peticion = cliente.post().uri(url);
            peticion = conClave ? peticion.header("x-goog-api-key", credencial.strip())
                    : peticion.header("Authorization", "Bearer " + tokens.token(cuenta));
            return peticion
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(cuerpo))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            if (codigo == 401 || codigo == 403) {
                if (cuenta != null) {
                    tokens.olvidar(cuenta);
                }
                throw new ErrorDeProveedor(Tipo.CREDENCIAL_INVALIDA, conClave
                        ? "Google Cloud rechazó la clave de API o el proyecto \"" + proyecto
                                + "\" (revisa el parámetro proyecto y que la API de Vertex AI esté habilitada) (" + codigo + ")"
                        : "Google Cloud rechazó la cuenta de servicio o le faltan permisos (" + codigo + ")", e);
            }
            if (codigo == 400 && conClave && proyecto == null
                    && e.getResponseBodyAsString().contains("RESOURCE_PROJECT_INVALID")) {
                throw new ErrorDeProveedor(Tipo.CONFIGURACION_INVALIDA, nombreProveedor
                        + " necesita el ID del proyecto de Google Cloud: agrégalo como \"proyecto\" en los parámetros del modelo",
                        e);
            }
            if (codigo == 400 || codigo == 404) {
                throw new ErrorDeProveedor(Tipo.CONFIGURACION_INVALIDA, nombreProveedor
                        + " rechazó la petición; revisa el modelo, su ubicación y sus parámetros (" + codigo + ")", e);
            }
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE, nombreProveedor + " no está disponible (" + codigo + ")", e);
        } catch (ResourceAccessException e) {
            throw new ErrorDeProveedor(Tipo.NO_DISPONIBLE,
                    nombreProveedor + " no respondió a tiempo o no se pudo conectar", e);
        }
    }

    /**
     * Lee la cuenta de servicio y pide un token: si Google lo entrega, la credencial sirve.
     *
     * @throws ErrorDeProveedor si la credencial no es válida o Google no responde.
     */
    public void verificarCredencial(String credencial) {
        tokens.token(tokens.cuenta(credencial));
    }

    /** Una clave de API de Google Cloud (por ejemplo, las que empiezan con "AQ." o "AIza"); si no, es el JSON. */
    static boolean esClaveDeApi(String credencial) {
        return credencial != null && !credencial.strip().startsWith("{");
    }

    /** El ID del proyecto para usar con una clave de API: parámetro {@code proyecto} del modelo; null si no está. */
    static String proyectoDelModelo(ModeloIA modelo) {
        String proyecto = parametro(modelo, "proyecto", "").strip();
        return proyecto.isEmpty() ? null : proyecto;
    }

    /** Un parámetro del modelo como texto, o el valor por defecto. */
    static String parametro(ModeloIA modelo, String nombre, String porDefecto) {
        Object valor = modelo.getParametros() == null ? null : modelo.getParametros().get(nombre);
        return valor == null ? porDefecto : String.valueOf(valor);
    }
}
