package com.autoreel.ia.infraestructura;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.autoreel.ia.dominio.AlmacenDeSecretos;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Secretos de desarrollo, en este orden de prioridad:
 * <ol>
 * <li>Los que el administrador pegó en Ajustes → Integraciones, guardados en {@code autoreel.archivo-secretos-panel}
 * (un JSON fuera del repositorio, ignorado por git).</li>
 * <li>{@code autoreel.secretos}: el valor tomado de una variable de entorno (por ejemplo, la clave de Gemini).</li>
 * <li>{@code autoreel.secretos-en-archivo}: la ruta de un archivo con el secreto (por ejemplo, el JSON de la cuenta
 * de servicio de Google Cloud, que tiene saltos de línea y no cabe bien en el {@code .env}).</li>
 * </ol>
 * En producción se reemplaza por AWS Secrets Manager (fase 8). Nunca se registra un valor en el log.
 */
@Component
public class SecretosDeConfiguracion implements ProveedorDeSecretos, AlmacenDeSecretos {

    private static final Logger log = LoggerFactory.getLogger(SecretosDeConfiguracion.class);

    @ConfigurationProperties("autoreel")
    public record PropiedadesSecretos(Map<String, String> secretos, Map<String, String> secretosEnArchivo,
            String archivoSecretosPanel) {

        public PropiedadesSecretos {
            secretos = secretos == null ? Map.of() : Map.copyOf(secretos);
            secretosEnArchivo = secretosEnArchivo == null ? Map.of() : Map.copyOf(secretosEnArchivo);
        }
    }

    private final PropiedadesSecretos propiedades;
    private final ObjectMapper json;
    private final Path archivoPanel;
    private Map<String, String> delPanel;

    public SecretosDeConfiguracion(PropiedadesSecretos propiedades, ObjectMapper json) {
        this.propiedades = propiedades;
        this.json = json;
        this.archivoPanel = propiedades.archivoSecretosPanel() == null || propiedades.archivoSecretosPanel().isBlank()
                ? null : Path.of(propiedades.archivoSecretosPanel().strip()).toAbsolutePath();
        this.delPanel = leerPanel();
    }

    @Override
    public synchronized Optional<String> obtener(String nombreSecreto) {
        return Optional.ofNullable(delPanel.get(nombreSecreto)).filter(valor -> !valor.isBlank())
                .or(() -> deConfiguracion(nombreSecreto));
    }

    @Override
    public synchronized void guardar(String nombreSecreto, String valor) {
        exigirArchivo();
        Map<String, String> nuevos = new TreeMap<>(delPanel);
        nuevos.put(nombreSecreto, valor);
        escribir(nuevos);
        log.info("Se guardó desde el panel el secreto {}", nombreSecreto);
    }

    @Override
    public synchronized void borrar(String nombreSecreto) {
        if (!delPanel.containsKey(nombreSecreto)) {
            return;
        }
        exigirArchivo();
        Map<String, String> nuevos = new TreeMap<>(delPanel);
        nuevos.remove(nombreSecreto);
        escribir(nuevos);
        log.info("Se borró del panel el secreto {}", nombreSecreto);
    }

    @Override
    public synchronized Origen origen(String nombreSecreto) {
        if (delPanel.containsKey(nombreSecreto)) {
            return Origen.PANEL;
        }
        return deConfiguracion(nombreSecreto).isPresent() ? Origen.CONFIGURACION : Origen.NINGUNO;
    }

    private Optional<String> deConfiguracion(String nombreSecreto) {
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

    private Map<String, String> leerPanel() {
        if (archivoPanel == null || !Files.isRegularFile(archivoPanel)) {
            return Map.of();
        }
        try {
            return Map.copyOf(json.readValue(Files.readString(archivoPanel, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, String>>() {
                    }));
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo leer el archivo de secretos del panel {}; se ignora", archivoPanel);
            return Map.of();
        }
    }

    /** Escribe en un archivo temporal y lo mueve: nunca queda un archivo a medio escribir. */
    private void escribir(Map<String, String> nuevos) {
        try {
            Files.createDirectories(archivoPanel.getParent());
            Path temporal = archivoPanel.resolveSibling(archivoPanel.getFileName() + ".tmp");
            Files.writeString(temporal, json.writerWithDefaultPrettyPrinter().writeValueAsString(nuevos),
                    StandardCharsets.UTF_8);
            Files.move(temporal, archivoPanel, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            delPanel = Map.copyOf(nuevos);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo de secretos del panel", e);
        }
    }

    private void exigirArchivo() {
        if (archivoPanel == null) {
            throw new IllegalStateException("No está configurado autoreel.archivo-secretos-panel");
        }
    }
}
