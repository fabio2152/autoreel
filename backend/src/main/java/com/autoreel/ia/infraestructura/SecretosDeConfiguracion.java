package com.autoreel.ia.infraestructura;

import java.util.Map;
import java.util.Optional;

import com.autoreel.ia.dominio.ProveedorDeSecretos;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Secretos leídos de la configuración ({@code autoreel.secretos}), que a su vez los toma de variables de entorno.
 * Es la implementación de desarrollo; en producción se reemplaza por Secrets Manager (fase 8).
 */
@Component
public class SecretosDeConfiguracion implements ProveedorDeSecretos {

    @ConfigurationProperties("autoreel")
    public record PropiedadesSecretos(Map<String, String> secretos) {

        public PropiedadesSecretos {
            secretos = secretos == null ? Map.of() : Map.copyOf(secretos);
        }
    }

    private final PropiedadesSecretos propiedades;

    public SecretosDeConfiguracion(PropiedadesSecretos propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public Optional<String> obtener(String nombreSecreto) {
        return Optional.ofNullable(propiedades.secretos().get(nombreSecreto)).filter(valor -> !valor.isBlank());
    }
}
