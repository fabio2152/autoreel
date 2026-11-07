package com.autoreel.facturacion.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** RF-10: datos de un plan. Las opciones de producción son las fijas del MVP (sección 8.6). */
public record SolicitudPlan(
        @NotBlank(message = "Escribe el nombre del plan.")
        @Size(max = 40, message = "El nombre puede tener como máximo 40 caracteres.") String nombre,
        @Size(max = 300, message = "La descripción puede tener como máximo 300 caracteres.") String descripcion,
        @Min(value = 1, message = "El plan debe permitir al menos 1 video por día.")
        @Max(value = 20, message = "El plan puede permitir como máximo 20 videos por día.") int videosPorDia,
        @Min(value = 0, message = "Las regeneraciones no pueden ser negativas.")
        @Max(value = 20, message = "El plan puede permitir como máximo 20 regeneraciones por video.")
        int regeneracionesPorVideo,
        @NotNull(message = "Elige el modelo de texto.") Long modeloTextoId,
        @NotNull(message = "Elige el modelo de video.") Long modeloVideoId,
        @PositiveOrZero(message = "El precio de referencia no puede ser negativo.")
        @Digits(integer = 8, fraction = 2, message = "El precio admite hasta dos decimales.") BigDecimal precioReferencial,
        boolean activo) {
}
