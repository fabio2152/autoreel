package com.autoreel.admin.api;

import java.time.Instant;

/**
 * Se muestra una sola vez (RN-02): la base de datos solo guarda su hash.
 */
public record ContrasenaTemporal(Long negocioId, String correo, String contrasenaTemporal, String enlace,
        Instant vence) {
}
