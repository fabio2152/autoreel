package com.autoreel.ia.infraestructura;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.ia.dominio.AlmacenDeSecretos;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.InvalidRequestException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * Secretos de los proveedores de IA en AWS Secrets Manager (perfil {@code aws}, ADR 0018). El nombre del secreto es el
 * que guarda el modelo de IA (RN-55), por ejemplo {@code autoreel/gemini}; para {@code autoreel/google-cloud} el valor
 * es el contenido del JSON de la cuenta de servicio, igual que entrega {@link SecretosDeConfiguracion} en desarrollo.
 * <p>
 * En AWS no hay un {@code .env} al que volver: lo que se pega en el panel es la única fuente ({@code PANEL}) y borrar
 * elimina el secreto. Los valores se guardan en memoria un minuto para no llamar a AWS en cada consulta. Las
 * credenciales de AWS salen del rol de la instancia. Nunca se registra un valor en el log, solo el nombre.
 */
@Component
@Profile("aws")
public class SecretosDeAws implements ProveedorDeSecretos, AlmacenDeSecretos, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SecretosDeAws.class);
    static final Duration VIGENCIA_CACHE = Duration.ofSeconds(60);

    private record Leido(Optional<String> valor, Instant cuando) {
    }

    private final SecretsManagerClient cliente;
    private final Clock reloj;
    private final Map<String, Leido> cache = new ConcurrentHashMap<>();

    @Autowired
    public SecretosDeAws(@Value("${autoreel.almacenamiento.region:us-east-1}") String region, Clock reloj) {
        this(SecretsManagerClient.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build()).build(), reloj);
    }

    SecretosDeAws(SecretsManagerClient cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
    }

    @Override
    public Optional<String> obtener(String nombreSecreto) {
        Instant ahora = reloj.instant();
        Leido guardado = cache.get(nombreSecreto);
        if (guardado != null && guardado.cuando().plus(VIGENCIA_CACHE).isAfter(ahora)) {
            return guardado.valor();
        }
        try {
            String valor = cliente.getSecretValue(pedido -> pedido.secretId(nombreSecreto)).secretString();
            Optional<String> resultado = Optional.ofNullable(valor).filter(texto -> !texto.isBlank());
            cache.put(nombreSecreto, new Leido(resultado, ahora));
            return resultado;
        } catch (ResourceNotFoundException | InvalidRequestException e) {
            // No existe, o está marcado para borrarse.
            cache.put(nombreSecreto, new Leido(Optional.empty(), ahora));
            return Optional.empty();
        } catch (SdkException e) {
            // Permisos, red o cifrado: no se guarda en la caché; si había un valor anterior, se sigue usando.
            log.warn("No se pudo leer el secreto {} de Secrets Manager: {}", nombreSecreto, codigo(e));
            return guardado == null ? Optional.empty() : guardado.valor();
        }
    }

    @Override
    public void guardar(String nombreSecreto, String valor) {
        try {
            try {
                cliente.putSecretValue(pedido -> pedido.secretId(nombreSecreto).secretString(valor));
            } catch (ResourceNotFoundException e) {
                crear(nombreSecreto, valor);
            }
        } catch (InvalidRequestException e) {
            // Recién borrado: AWS tarda unos segundos en liberar el nombre.
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "CREDENCIAL_BORRANDOSE", "La credencial "
                    + nombreSecreto + " se está borrando en AWS Secrets Manager. Intenta de nuevo en unos segundos.");
        } catch (SdkException e) {
            throw sinAcceso(nombreSecreto, "guardar", e);
        }
        cache.put(nombreSecreto, new Leido(Optional.of(valor).filter(texto -> !texto.isBlank()), reloj.instant()));
        log.info("Se guardó en Secrets Manager el secreto {}", nombreSecreto);
    }

    @Override
    public void borrar(String nombreSecreto) {
        try {
            cliente.deleteSecret(pedido -> pedido.secretId(nombreSecreto).forceDeleteWithoutRecovery(true));
            log.info("Se borró de Secrets Manager el secreto {}", nombreSecreto);
        } catch (ResourceNotFoundException | InvalidRequestException e) {
            // Ya no existía, o ya se estaba borrando.
        } catch (SdkException e) {
            throw sinAcceso(nombreSecreto, "borrar", e);
        }
        cache.put(nombreSecreto, new Leido(Optional.empty(), reloj.instant()));
    }

    /** En AWS solo existe el valor pegado en el panel (o creado en la consola): {@code PANEL} o {@code NINGUNO}. */
    @Override
    public Origen origen(String nombreSecreto) {
        return obtener(nombreSecreto).isPresent() ? Origen.PANEL : Origen.NINGUNO;
    }

    @Override
    public void close() {
        cliente.close();
    }

    private void crear(String nombreSecreto, String valor) {
        cliente.createSecret(pedido -> pedido.name(nombreSecreto).secretString(valor)
                .description("Credencial de AutoReel pegada en Ajustes → Integraciones"));
        log.info("Se creó en Secrets Manager el secreto {}", nombreSecreto);
    }

    /** Permisos del rol, red o región: el panel muestra un error claro en lugar de un 500 sin explicación. */
    private static ExcepcionDeNegocio sinAcceso(String nombreSecreto, String accion, SdkException e) {
        log.warn("No se pudo {} el secreto {} en Secrets Manager: {}", accion, nombreSecreto, codigo(e));
        return new ExcepcionDeNegocio(HttpStatus.BAD_GATEWAY, "SECRETOS_NO_DISPONIBLES", "No se pudo " + accion
                + " la credencial " + nombreSecreto + " en AWS Secrets Manager. Revisa los permisos del servidor.");
    }

    private static String codigo(SdkException e) {
        return e instanceof AwsServiceException servicio && servicio.awsErrorDetails() != null
                && servicio.awsErrorDetails().errorCode() != null
                ? servicio.awsErrorDetails().errorCode() : e.getClass().getSimpleName();
    }
}
