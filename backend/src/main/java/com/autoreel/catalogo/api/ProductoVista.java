package com.autoreel.catalogo.api;

import java.time.Instant;
import java.util.List;

import com.autoreel.catalogo.dominio.EstadoProducto;
import com.autoreel.catalogo.dominio.TipoFoto;

/**
 * Producto tal como lo ve el frontend.
 *
 * @param completo tiene sus tres fotos y puede usarse en un video (RN-12).
 * @param avisos   avisos de la última modificación (por ejemplo, FOTO_HORIZONTAL).
 */
public record ProductoVista(Long id, String nombre, String descripcion, List<String> temporadas, EstadoProducto estado,
        boolean completo, List<Foto> fotos, Instant fechaCreacion, List<String> avisos) {

    /** @param horizontal se recortará al formato vertical. */
    public record Foto(TipoFoto tipo, String clave, String url, int ancho, int alto, boolean horizontal) {
    }
}
