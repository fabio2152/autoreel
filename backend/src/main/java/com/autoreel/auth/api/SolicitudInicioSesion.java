package com.autoreel.auth.api;

import jakarta.validation.constraints.NotBlank;

public record SolicitudInicioSesion(
        @NotBlank(message = "Escribe tu correo.") String correo,
        @NotBlank(message = "Escribe tu contraseña.") String contrasena) {
}
