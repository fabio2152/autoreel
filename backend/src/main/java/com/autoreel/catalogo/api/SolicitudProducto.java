package com.autoreel.catalogo.api;

import java.util.List;
import java.util.Map;

import com.autoreel.catalogo.dominio.TipoFoto;

/**
 * Datos de un producto. {@code fotos} lleva la clave de cada foto: una subida nueva, la misma que ya tenía o
 * ninguna (el producto queda incompleto). Al editar, una foto que no viene se quita.
 */
public record SolicitudProducto(String nombre, String descripcion, List<String> temporadas,
        Map<TipoFoto, String> fotos) {
}
