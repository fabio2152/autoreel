package com.autoreel.compartido.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/** {@link SecretosDeArranque}: del JSON del secreto a propiedades de Spring, sin llamar a AWS. */
class SecretosDeArranqueTest {

    private final SecretosDeArranque procesador = new SecretosDeArranque(Supplier<Log>::get);

    @Test
    void lasClavesDelJsonQuedanComoPropiedades() {
        StandardEnvironment entorno = new StandardEnvironment();
        String secreto = """
                {"DB_USER": "autoreel", "DB_PASSWORD": "p\\"a,ss", "JWT_SECRET": "abc=", "PUERTO": 5432, "NADA": null}
                """;

        procesador.cargar(entorno, List.of("autoreel/app"), id -> secreto);

        assertThat(entorno.getProperty("DB_USER")).isEqualTo("autoreel");
        assertThat(entorno.getProperty("DB_PASSWORD")).isEqualTo("p\"a,ss");
        assertThat(entorno.getProperty("JWT_SECRET")).isEqualTo("abc=");
        assertThat(entorno.getProperty("PUERTO")).isEqualTo("5432");
        assertThat(entorno.containsProperty("NADA")).isFalse();
        // Resuelve los marcadores de application.yml.
        assertThat(entorno.resolvePlaceholders("${DB_USER}")).isEqualTo("autoreel");
    }

    @Test
    void lasVariablesDeEntornoTienenPrioridad() {
        StandardEnvironment entorno = new StandardEnvironment();
        entorno.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("DB_USER", "del-entorno")));

        procesador.cargar(entorno, List.of("autoreel/app"), id -> "{\"DB_USER\": \"del-secreto\"}");

        assertThat(entorno.getProperty("DB_USER")).isEqualTo("del-entorno");
    }

    @Test
    void sinSecretosDeArranqueNoHaceNada() {
        StandardEnvironment entorno = new StandardEnvironment();
        int fuentes = entorno.getPropertySources().size();

        procesador.postProcessEnvironment(entorno, null);

        assertThat(entorno.getPropertySources().size()).isEqualTo(fuentes);
    }

    @Test
    void unSecretoQueNoSePuedeLeerDetieneElArranque() {
        StandardEnvironment entorno = new StandardEnvironment();

        assertThatThrownBy(() -> procesador.cargar(entorno, List.of("autoreel/app"), id -> {
            throw ResourceNotFoundException.builder().message("no existe").build();
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("autoreel/app");
    }

    @Test
    void unSecretoQueNoEsJsonFallaSinMostrarSuValor() {
        StandardEnvironment entorno = new StandardEnvironment();

        assertThatThrownBy(() -> procesador.cargar(entorno, List.of("autoreel/app"), id -> "clave-secreta-123"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("autoreel/app")
                .hasMessageNotContaining("clave-secreta-123");
    }
}
