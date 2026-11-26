package com.autoreel.compartido.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Secretos que el backend necesita antes de arrancar (base de datos, JWT y administrador inicial), leídos de AWS
 * Secrets Manager. {@code SECRETOS_DE_ARRANQUE} lista los secretos separados por comas (en AWS, {@code autoreel/app});
 * cada uno es un JSON cuyas claves son nombres de variables, por ejemplo {@code {"DB_USER": "...", "JWT_SECRET": "..."}},
 * y quedan como propiedades con las que se resuelven {@code ${DB_USER}} y las demás de {@code application.yml}.
 * <p>
 * Si {@code SECRETOS_DE_ARRANQUE} está vacía (desarrollo y pruebas), no hace nada. Si un secreto no se puede leer, el
 * backend no arranca. Nunca se registra un valor, solo nombres. Se registra en {@code META-INF/spring.factories}.
 */
public class SecretosDeArranque implements EnvironmentPostProcessor, Ordered {

    static final String VARIABLE = "SECRETOS_DE_ARRANQUE";
    static final String FUENTE = "secretosDeArranque";

    private final Log log;
    private final JsonMapper json = JsonMapper.builder().build();

    public SecretosDeArranque(DeferredLogFactory logs) {
        this.log = logs.getLog(SecretosDeArranque.class);
    }

    /** Después de leer los archivos de configuración, para poder tomar la variable también de ellos. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
        List<String> secretos = secretos(entorno);
        if (secretos.isEmpty()) {
            return;
        }
        Region region = Region.of(entorno.getProperty("AWS_REGION", "us-east-1"));
        try (SecretsManagerClient cliente = SecretsManagerClient.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.builder().build()).build()) {
            cargar(entorno, secretos, id -> cliente.getSecretValue(pedido -> pedido.secretId(id)).secretString());
        }
    }

    /** Lee cada secreto con {@code lector} y agrega sus claves como propiedades, después de las variables de entorno. */
    void cargar(ConfigurableEnvironment entorno, List<String> secretos, Function<String, String> lector) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        for (String id : secretos) {
            String valor;
            try {
                valor = lector.apply(id);
            } catch (SdkException e) {
                throw new IllegalStateException("No se pudo leer el secreto de arranque " + id
                        + " de AWS Secrets Manager (" + e.getClass().getSimpleName() + "). Revisa el nombre, la región"
                        + " y el permiso secretsmanager:GetSecretValue del rol de la instancia.", e);
            }
            Map<String, Object> delSecreto = propiedades(id, valor);
            propiedades.putAll(delSecreto);
            log.info("Se cargaron del secreto " + id + " las propiedades " + delSecreto.keySet());
        }
        MapPropertySource fuente = new MapPropertySource(FUENTE, propiedades);
        // Las variables de entorno del proceso siguen pudiendo reemplazar un valor (por ejemplo, para una prueba).
        if (entorno.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            entorno.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, fuente);
        } else {
            entorno.getPropertySources().addLast(fuente);
        }
    }

    /** Las claves del JSON del secreto como propiedades. El mensaje de error nunca incluye el contenido. */
    Map<String, Object> propiedades(String id, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("El secreto de arranque " + id + " está vacío o no es de texto.");
        }
        JsonNode nodo;
        try {
            nodo = json.readTree(valor);
        } catch (JacksonException e) {
            throw new IllegalStateException("El secreto de arranque " + id
                    + " no es un JSON de pares clave-valor (por ejemplo, {\"DB_USER\": \"...\"}).");
        }
        if (nodo == null || !nodo.isObject()) {
            throw new IllegalStateException("El secreto de arranque " + id
                    + " no es un JSON de pares clave-valor (por ejemplo, {\"DB_USER\": \"...\"}).");
        }
        Map<String, Object> propiedades = new LinkedHashMap<>();
        for (var entrada : nodo.properties()) {
            JsonNode valorPropiedad = entrada.getValue();
            if (!valorPropiedad.isNull() && valorPropiedad.isValueNode()) {
                propiedades.put(entrada.getKey(), valorPropiedad.asString());
            }
        }
        return propiedades;
    }

    private static List<String> secretos(ConfigurableEnvironment entorno) {
        String lista = entorno.getProperty(VARIABLE, "");
        return Arrays.stream(lista.split(",")).map(String::strip).filter(id -> !id.isEmpty()).toList();
    }
}
