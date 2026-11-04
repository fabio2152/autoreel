package com.autoreel.video.dominio;

/** Estados del video (sección 9.2). En la fase 4 se usan GUION_PENDIENTE, STORYBOARD, DESCARTADO y ERROR. */
public enum EstadoVideo {
    GUION_PENDIENTE,
    STORYBOARD,
    GENERANDO,
    COMPONIENDO,
    LISTO,
    APROBADO,
    PROGRAMADO,
    PUBLICADO,
    ERROR,
    DESCARTADO
}
