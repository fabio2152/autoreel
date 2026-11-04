package com.autoreel.video.aplicacion;

/**
 * Pasos del chat (sección 7.3) que el guion usa. Son el contrato entre {@code chat.json} y {@code guion.md} de cada
 * paquete de nicho: todo nicho pregunta el tipo de video, el producto, lo especial, el precio y algo más.
 */
final class RespuestasDelChat {

    static final String TIPO_VIDEO = "tipoVideo";
    static final String PRODUCTO = "producto";
    static final String ESPECIAL = "especial";
    static final String PRECIO = "precio";
    static final String EXTRA = "extra";

    private RespuestasDelChat() {
    }
}
