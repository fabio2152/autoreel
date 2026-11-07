package com.autoreel.facturacion.dominio;

/** Cómo pagó el negocio (sección 9.1). El cobro es manual: el administrador lo registra. */
public enum MedioPago {
    TRANSFERENCIA,
    YAPE,
    PLIN,
    EFECTIVO,
    OTRO
}
