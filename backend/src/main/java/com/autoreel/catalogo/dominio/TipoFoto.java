package com.autoreel.catalogo.dominio;

/**
 * La foto de cada producto (sección 7.2, cambio del 2026-11-25): una sola, obligatoria para usarlo en un video. La IA
 * trabaja mejor con una sola foto, y cada toma del video empieza en ella. Se conserva como enum para mantener el
 * contrato de la API ({@code {"FOTO_1": clave}}) y el campo {@code foto} del guion.
 */
public enum TipoFoto {
    /** La foto del producto: la que mejor lo muestra. */
    FOTO_1;

    /** Número de la foto, como la ve la IA (siempre 1). */
    public int numero() {
        return ordinal() + 1;
    }

    public static java.util.Optional<TipoFoto> deNumero(int numero) {
        return numero >= 1 && numero <= values().length ? java.util.Optional.of(values()[numero - 1])
                : java.util.Optional.empty();
    }
}
