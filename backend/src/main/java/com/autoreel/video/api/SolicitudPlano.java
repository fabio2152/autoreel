package com.autoreel.video.api;

/**
 * Cambio de un plano del storyboard. En las tomas se cambian el texto, las correcciones (lo que el negocio no quiere ver
 * en la toma, hasta 300 caracteres) o ambos; en el cierre, el precio, la frase o ambos ({@code ""} quita el que se
 * envía).
 */
public record SolicitudPlano(String texto, String correcciones, String precio, String frase) {
}
