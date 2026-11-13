package com.autoreel.video.api;

import com.autoreel.catalogo.dominio.TipoFoto;

/**
 * Cambio de un plano del storyboard. En los planos 1 a 3 se cambia el texto, la foto o ambos; en el cierre, el precio,
 * la frase o ambos ({@code ""} quita el que se envía).
 */
public record SolicitudPlano(String texto, FotoElegida foto, String precio, String frase) {

    /** Otra foto del producto ({@code tipo}) o una recién subida ({@code clave}); solo una de las dos. */
    public record FotoElegida(TipoFoto tipo, String clave) {
    }
}
