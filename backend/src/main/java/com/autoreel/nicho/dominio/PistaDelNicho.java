package com.autoreel.nicho.dominio;

/**
 * Pista de música que trae el paquete de nicho (sección 7.4) para un estilo. Al arrancar se sube al almacenamiento
 * y se registra en la biblioteca de música.
 *
 * @param archivo nombre del archivo en la carpeta {@code musica/} del paquete.
 */
public record PistaDelNicho(String estilo, String nombre, String archivo, int duracionSegundos, String licencia) {
}
