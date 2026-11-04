package com.autoreel.admin.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * RF-03 y RN-41: el plan, el monto mensual y la fecha del primer pago son obligatorios.
 */
public record SolicitudCrearNegocio(
        @NotBlank(message = "Elige el nicho del negocio.") String nicho,
        @NotBlank(message = "Escribe el nombre del negocio.")
        @Size(max = 120, message = "El nombre puede tener como máximo 120 caracteres.") String nombre,
        @NotBlank(message = "Escribe el correo del usuario.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 254, message = "El correo es demasiado largo.") String correo,
        @NotNull(message = "Elige el plan del negocio.") Long planId,
        @NotNull(message = "Escribe el monto mensual acordado.")
        @Positive(message = "El monto mensual debe ser mayor que cero.")
        @Digits(integer = 8, fraction = 2, message = "El monto mensual admite hasta dos decimales.") BigDecimal montoMensual,
        @NotNull(message = "Elige la fecha del primer pago.") LocalDate fechaPrimerPago) {
}
