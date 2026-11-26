package com.autoreel.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El negocio olvidó su contraseña y le pide una temporal al administrador.
 *
 * @param contacto WhatsApp opcional para que el administrador le envíe la contraseña temporal.
 */
public record SolicitudReinicioContrasena(
        @Size(max = 20, message = "El WhatsApp puede tener hasta 20 caracteres.")
        @Pattern(regexp = "[0-9+() -]*", message = "Escribe el WhatsApp solo con números.") String contacto,
        @NotBlank(message = "Escribe un mensaje para el administrador.")
        @Size(max = 200, message = "El mensaje puede tener hasta 200 caracteres.") String mensaje) {
}
