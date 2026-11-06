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
 * Llamadas a los modelos de Google en Google Cloud (Vertex AI, hoy "Gemini Enterprise Agent Platform") con una
 * cuenta de servicio. La comparten los adaptadores de Google Cloud: Veo (video) y Gemini (texto). La credencial es el
 * JSON de la cuenta de servicio (RN-55); el proyecto sale de ese JSON y la ubicación, de los parámetros del modelo.
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
        CuentaDeServicio cuenta = tokens.cuenta(credencial);
        String base = UBICACION_GLOBAL.equals(ubicacion) ? propiedades.urlGlobal()
                : propiedades.url().replace("{region}", ubicacion);
        String url = base + "/v1/projects/" + cuenta.proyecto() + "/locations/" + ubicacion
                + "/publishers/google/models/" + modelo.getIdentificadorModelo() + ":" + metodo;
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
                        "Google Cloud rechazó la cuenta de servicio o le faltan permisos (" + codigo + ")", e);
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

    /** Un parámetro del modelo como texto, o el valor por defecto. */
    static String parametro(ModeloIA modelo, String nombre, String porDefecto) {
        Object valor = modelo.getParametros() == null ? null : modelo.getParametros().get(nombre);
        return valor == null ? porDefecto : String.valueOf(valor);
    }
}
