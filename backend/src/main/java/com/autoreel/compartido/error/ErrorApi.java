package com.autoreel.compartido.error;

/**
 * Forma única de las respuestas de error de la API: un código estable y un mensaje en español listo para mostrar.
 */
public record ErrorApi(String codigo, String mensaje) {
}
