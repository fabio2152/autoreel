package com.autoreel.catalogo.api;

import java.util.List;
import java.util.Map;

import com.autoreel.catalogo.dominio.TipoFoto;

/**
 * Datos de un producto. {@code fotos} lleva la clave de la foto del producto en {@code FOTO_1} (la única): una subida
 * nueva, la misma que ya tenía o ninguna (el producto queda incompleto). Al editar, si no viene, se quita. Otra clave
 * (por ejemplo {@code FOTO_2}) no existe y se rechaza con 400.
 */
public record SolicitudProducto(String nombre, String descripcion, List<String> temporadas,
        Map<TipoFoto, String> fotos) {
}
