package com.autoreel.ia.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.autoreel.RelojAjustable;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.ia.dominio.AlmacenDeSecretos.Origen;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.InvalidRequestException;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/** {@link SecretosDeAws} contra un Secrets Manager simulado en memoria. */
class SecretosDeAwsTest {

    private final SecretsManagerSimulado aws = new SecretsManagerSimulado();
    private final RelojAjustable reloj = new RelojAjustable(ZoneOffset.UTC);
    private final SecretosDeAws secretos = new SecretosDeAws(aws, reloj);

    @Test
    void unSecretoQueNoExisteEstaVacioYSinOrigen() {
        assertThat(secretos.obtener("autoreel/gemini")).isEmpty();
        assertThat(secretos.origen("autoreel/gemini")).isEqualTo(Origen.NINGUNO);
    }

    @Test
    void unSecretoEnBlancoCuentaComoVacio() {
        aws.valores.put("autoreel/gemini", "  ");
        assertThat(secretos.obtener("autoreel/gemini")).isEmpty();
    }

    @Test
    void guardarCreaElSecretoSiNoExisteYDespuesLoReemplaza() {
        secretos.guardar("autoreel/gemini", "clave-1");
        assertThat(aws.creados).isEqualTo(1);
        assertThat(aws.valores).containsEntry("autoreel/gemini", "clave-1");
        assertThat(secretos.obtener("autoreel/gemini")).contains("clave-1");
        assertThat(secretos.origen("autoreel/gemini")).isEqualTo(Origen.PANEL);

        secretos.guardar("autoreel/gemini", "clave-2");
        assertThat(aws.creados).isEqualTo(1);
        assertThat(secretos.obtener("autoreel/gemini")).contains("clave-2");
    }

    @Test
    void elJsonDeLaCuentaDeServicioSeEntregaTalCual() {
        String json = "{\"type\": \"service_account\",\n \"client_email\": \"a@b.iam.gserviceaccount.com\"}";
        aws.valores.put("autoreel/google-cloud", json);
        assertThat(secretos.obtener("autoreel/google-cloud")).contains(json);
    }

    @Test
    void borrarEliminaElSecretoSinRecuperacion() {
        aws.valores.put("autoreel/gemini", "clave");
        assertThat(secretos.obtener("autoreel/gemini")).contains("clave");

        secretos.borrar("autoreel/gemini");

        assertThat(aws.borradosSinRecuperacion).isEqualTo(1);
        assertThat(aws.valores).doesNotContainKey("autoreel/gemini");
        assertThat(secretos.obtener("autoreel/gemini")).isEmpty();
        assertThat(secretos.origen("autoreel/gemini")).isEqualTo(Origen.NINGUNO);
        // Borrar uno que no existe no falla.
        secretos.borrar("autoreel/otro");
    }

    @Test
    void guardaElValorUnMinutoAntesDeVolverAConsultar() {
        aws.valores.put("autoreel/gemini", "clave-1");
        assertThat(secretos.obtener("autoreel/gemini")).contains("clave-1");
        aws.valores.put("autoreel/gemini", "clave-2");
        assertThat(secretos.obtener("autoreel/gemini")).contains("clave-1");
        assertThat(aws.lecturas).isEqualTo(1);

        reloj.avanzar(Duration.ofSeconds(61));

        assertThat(secretos.obtener("autoreel/gemini")).contains("clave-2");
        assertThat(aws.lecturas).isEqualTo(2);
    }

    @Test
    void siAwsFallaSeSigueUsandoElValorAnterior() {
        aws.valores.put("autoreel/gemini", "clave");
        assertThat(secretos.obtener("autoreel/gemini")).contains("clave");
        reloj.avanzar(Duration.ofSeconds(61));
        aws.sinPermiso = true;

        assertThat(secretos.obtener("autoreel/gemini")).contains("clave");
    }

    @Test
    void guardarMientrasSeBorraDaUnErrorClaro() {
        aws.borrandose.add("autoreel/gemini");

        assertThatThrownBy(() -> secretos.guardar("autoreel/gemini", "clave"))
                .isInstanceOf(ExcepcionDeNegocio.class).hasMessageContaining("se está borrando");
        assertThat(secretos.obtener("autoreel/gemini")).isEmpty();
    }

    /** Lo mínimo de Secrets Manager que usa {@link SecretosDeAws}. */
    private static final class SecretsManagerSimulado implements SecretsManagerClient {

        final Map<String, String> valores = new HashMap<>();
        int lecturas;
        int creados;
        final Set<String> borrandose = new HashSet<>();
        int borradosSinRecuperacion;
        boolean sinPermiso;

        @Override
        public GetSecretValueResponse getSecretValue(GetSecretValueRequest pedido) {
            lecturas++;
            if (sinPermiso) {
                throw SecretsManagerException.builder().message("AccessDenied").statusCode(403).build();
            }
            if (borrandose.contains(pedido.secretId())) {
                throw seEstaBorrando();
            }
            String valor = valores.get(pedido.secretId());
            if (valor == null) {
                throw noExiste();
            }
            return GetSecretValueResponse.builder().name(pedido.secretId()).secretString(valor).build();
        }

        @Override
        public PutSecretValueResponse putSecretValue(PutSecretValueRequest pedido) {
            if (!valores.containsKey(pedido.secretId())) {
                throw noExiste();
            }
            valores.put(pedido.secretId(), pedido.secretString());
            return PutSecretValueResponse.builder().build();
        }

        @Override
        public CreateSecretResponse createSecret(CreateSecretRequest pedido) {
            if (borrandose.contains(pedido.name())) {
                throw seEstaBorrando();
            }
            creados++;
            valores.put(pedido.name(), pedido.secretString());
            return CreateSecretResponse.builder().name(pedido.name()).build();
        }

        @Override
        public DeleteSecretResponse deleteSecret(DeleteSecretRequest pedido) {
            if (valores.remove(pedido.secretId()) == null) {
                throw noExiste();
            }
            if (Boolean.TRUE.equals(pedido.forceDeleteWithoutRecovery())) {
                borradosSinRecuperacion++;
            }
            return DeleteSecretResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "secretsmanager";
        }

        @Override
        public void close() {
        }

        private static InvalidRequestException seEstaBorrando() {
            return InvalidRequestException.builder().message("The secret is scheduled for deletion.").build();
        }

        private static ResourceNotFoundException noExiste() {
            return ResourceNotFoundException.builder().message("Secrets Manager can't find the specified secret.")
                    .build();
        }
    }
}
