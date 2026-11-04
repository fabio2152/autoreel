package com.autoreel.catalogo.dominio;

/**
 * Las tres fotos obligatorias de cada producto (sección 7.2). Cada una alimenta un plano del video.
 */
public enum TipoFoto {
    /** El producto completo, de frente. */
    ENTERO,
    /** Muy cerca: crema, glaseado o fruta. */
    DETALLE,
    /** Una porción ya cortada, mostrando el relleno. */
    PORCION
}
