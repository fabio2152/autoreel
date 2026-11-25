package com.autoreel.catalogo.dominio;

/**
 * Las fotos de cada producto (sección 7.2, cambio del 2026-11-25): la primera es obligatoria y las otras dos son
 * opcionales, de cualquier ángulo (entero, detalle, porción o en su ambiente). La IA las ve todas y decide cuál usa en
 * cada toma del video.
 */
public enum TipoFoto {
    /** La foto principal, obligatoria: la que mejor muestra el producto. */
    FOTO_1,
    /** Opcional. */
    FOTO_2,
    /** Opcional. */
    FOTO_3;

    /** Número de la foto (1 a 3), como la ve la IA. */
    public int numero() {
        return ordinal() + 1;
    }

    public static java.util.Optional<TipoFoto> deNumero(int numero) {
        return numero >= 1 && numero <= values().length ? java.util.Optional.of(values()[numero - 1])
                : java.util.Optional.empty();
    }
}
