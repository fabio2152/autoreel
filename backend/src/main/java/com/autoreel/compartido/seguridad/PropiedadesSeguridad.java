package com.autoreel.compartido.seguridad;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param jwtSecreto   clave HMAC en base64 (al menos 32 bytes), desde la variable JWT_SECRET.
 * @param sesionHoras  duración de la sesión (RN-08: 8 horas).
 * @param cookieSegura marca la cookie como Secure; solo se desactiva en el perfil dev.
 */
@Validated
@ConfigurationProperties("autoreel.seguridad")
public record PropiedadesSeguridad(@NotBlank String jwtSecreto, @Min(1) @Max(24) int sesionHoras,
        boolean cookieSegura) {
}
