package com.autoreel.auth.api;

import jakarta.validation.constraints.NotBlank;

/**
 * @param contrasenaActual obligatoria salvo en el primer cambio, cuando el usuario entró con la contraseña temporal.
 */
public record SolicitudCambioContrasena(
        String contrasenaActual,
        @NotBlank(message = "Escribe tu contraseña nueva.") String nueva,
        @NotBlank(message = "Confirma tu contraseña nueva.") String confirmacion) {
}
