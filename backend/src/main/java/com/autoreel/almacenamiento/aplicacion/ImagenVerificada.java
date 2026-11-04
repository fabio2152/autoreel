package com.autoreel.almacenamiento.aplicacion;

/**
 * Medidas de una imagen tal como se ve (con la orientación de la cámara aplicada).
 *
 * @param horizontal       se acepta con aviso: se recortará al formato vertical (RN-11).
 * @param sinTransparencia solo para el logo: es PNG pero sin fondo transparente.
 */
public record ImagenVerificada(int ancho, int alto, boolean horizontal, boolean sinTransparencia) {
}
