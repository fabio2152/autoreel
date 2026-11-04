package com.autoreel.video.dominio;

/** Por qué el storyboard usa el guion de respaldo (RN-25). Solo lo ve el administrador. */
public enum MotivoRespaldo {
    /** El secreto del modelo no está configurado. */
    SIN_CREDENCIAL,
    CREDENCIAL_INVALIDA,
    CONFIGURACION_INVALIDA,
    PROVEEDOR_NO_DISPONIBLE,
    /** Los tres intentos devolvieron un guion que no pasó el validador. */
    GUION_INVALIDO,
    /** El modelo está inactivo, no es de texto o su adaptador no existe (RN-56). */
    MODELO_INACTIVO,
    /** El proceso anterior se cortó (por ejemplo, un reinicio) y venció su reclamo. */
    PROCESO_INTERRUMPIDO
}
