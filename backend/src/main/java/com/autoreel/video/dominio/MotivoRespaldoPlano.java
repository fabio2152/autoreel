package com.autoreel.video.dominio;

/**
 * Por qué un plano se quedó sin clip de IA (sección 8.5). Desde el 2026-11-06 no hay respaldo sin IA (ADR 0020): se usa
 * en el registro de la aplicación y en los videos creados antes, que guardaban el motivo del acercamiento de FFmpeg.
 */
public enum MotivoRespaldoPlano {
    /** El secreto del modelo de video no está configurado. */
    SIN_CREDENCIAL,
    CREDENCIAL_INVALIDA,
    CONFIGURACION_INVALIDA,
    /** El proveedor no respondió en dos intentos. */
    PROVEEDOR_NO_DISPONIBLE,
    /** El proveedor terminó sin clip dos veces (por ejemplo, su filtro de contenido). */
    CLIP_FALLIDO,
    /** Pasaron 10 minutos sin el clip. */
    TIEMPO_AGOTADO,
    /** El modelo está inactivo, no es de video o su adaptador no existe (RN-56). */
    MODELO_INACTIVO
}
