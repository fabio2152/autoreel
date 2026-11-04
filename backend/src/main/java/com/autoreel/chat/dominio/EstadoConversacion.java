package com.autoreel.chat.dominio;

public enum EstadoConversacion {
    EN_CURSO,
    /** Reunió todas las respuestas: es la entrada del guion del video (fase 4). */
    COMPLETA,
    /** El usuario empezó de nuevo. */
    ABANDONADA
}
