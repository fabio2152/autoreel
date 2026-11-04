package com.autoreel.almacenamiento.dominio;

import java.util.Map;
import java.util.Set;

/**
 * Para qué se sube un archivo. Cada propósito define los tipos y el tamaño admitidos (RN-10, RN-11) y la carpeta
 * donde se guarda.
 */
public enum PropositoArchivo {

    /** RN-10: PNG de hasta 5 MB. */
    LOGO("logo", Set.of("image/png"), 5),
    /** RN-11: JPG, PNG o WebP de hasta 10 MB, con el lado menor de al menos 1080 px. */
    FOTO_PRODUCTO("foto_producto", Set.of("image/jpeg", "image/png", "image/webp"), 10),
    /** Fondo del plano de cierre (1080 × 1920): mismas reglas que las fotos de productos. */
    FOTO_LOCAL("foto_local", Set.of("image/jpeg", "image/png", "image/webp"), 10);

    private static final Map<String, String> EXTENSIONES =
            Map.of("image/png", "png", "image/jpeg", "jpg", "image/webp", "webp");

    private final String carpeta;
    private final Set<String> tiposAdmitidos;
    private final int megabytesMaximos;

    PropositoArchivo(String carpeta, Set<String> tiposAdmitidos, int megabytesMaximos) {
        this.carpeta = carpeta;
        this.tiposAdmitidos = tiposAdmitidos;
        this.megabytesMaximos = megabytesMaximos;
    }

    public String carpeta() {
        return carpeta;
    }

    public boolean admite(String tipoContenido) {
        return tiposAdmitidos.contains(tipoContenido);
    }

    public long bytesMaximos() {
        return megabytesMaximos * 1024L * 1024L;
    }

    public int megabytesMaximos() {
        return megabytesMaximos;
    }

    public boolean esFoto() {
        return this != LOGO;
    }

    public static String extension(String tipoContenido) {
        return EXTENSIONES.get(tipoContenido);
    }

    public static String tipoPorExtension(String extension) {
        return EXTENSIONES.entrySet().stream().filter(e -> e.getValue().equals(extension))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
}
