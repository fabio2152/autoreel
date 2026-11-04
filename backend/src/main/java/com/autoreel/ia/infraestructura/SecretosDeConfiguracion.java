package com.autoreel.ia.infraestructura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.autoreel.ia.dominio.ProveedorDeSecretos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Secretos leídos de la configuración, que a su vez los toma de variables de entorno. Es la implementación de
 * desarrollo; en producción se reemplaza por Secrets Manager (fase 8).
 * <ul>
 * <li>{@code autoreel.secretos}: el valor del secreto (por ejemplo, la clave de Gemini).</li>
 * <li>{@code autoreel.secretos-en-archivo}: la ruta de un archivo con el secreto (por ejemplo, el JSON de la cuenta
 * de servicio de Google Cloud, que tiene saltos de línea y no cabe bien en el {@code .env}).</li>
 * </ul>
 */
@Component
public class SecretosDeConfiguracion implements ProveedorDeSecretos {

    private static final Logger log = LoggerFactory.getLogger(SecretosDeConfiguracion.class);

    @ConfigurationProperties("autoreel")
    public record PropiedadesSecretos(Map<String, String> secretos, Map<String, String> secretosEnArchivo) {

        public PropiedadesSecretos {
            secretos = secretos == null ? Map.of() : Map.copyOf(secretos);
            secretosEnArchivo = secretosEnArchivo == null ? Map.of() : Map.copyOf(secretosEnArchivo);
        }
    }

    private final PropiedadesSecretos propiedades;

    public SecretosDeConfiguracion(PropiedadesSecretos propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public Optional<String> obtener(String nombreSecreto) {
        Optional<String> directo = Optional.ofNullable(propiedades.secretos().get(nombreSecreto))
                .filter(valor -> !valor.isBlank());
        if (directo.isPresent()) {
            return directo;
        }
        String ruta = propiedades.secretosEnArchivo().get(nombreSecreto);
        if (ruta == null || ruta.isBlank()) {
            return Optional.empty();
        }
        Path archivo = Path.of(ruta.strip());
        if (!Files.isRegularFile(archivo)) {
            log.warn("El archivo del secreto {} no existe: {}", nombreSecreto, archivo);
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(archivo, StandardCharsets.UTF_8)).filter(valor -> !valor.isBlank());
        } catch (IOException e) {
            log.warn("No se pudo leer el archivo del secreto {}: {}", nombreSecreto, archivo);
            return Optional.empty();
        }
    }
}
