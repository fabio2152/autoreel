package com.autoreel.facturacion.dominio;

/** Notificaciones del panel del administrador (RN-44, RN-45 y RN-57). */
public enum TipoNotificacion {
    PAGO_POR_VENCER,
    PAGO_HOY,
    PAGO_VENCIDO,
    /** RN-45: a los 7 días de vencido se sugiere suspender; el administrador decide. */
    SUGERENCIA_SUSPENSION,
    INTEGRACION_CON_ERROR
}
