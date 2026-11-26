package com.autoreel.admin.api;

import java.time.Instant;
import java.time.LocalDate;

import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.negocio.dominio.EstadoNegocio;

/**
 * Fila de la lista de negocios (RF-02). {@code videosDelMes}: videos generados en el mes de Lima en curso.
 * {@code plan} y {@code modeloVideo} son los que rigen hoy; {@code conBeneficio} indica que vienen de un beneficio,
 * al que le quedan {@code beneficioUsosRestantes} usos (vacío sin beneficio).
 */
public record NegocioResumen(Long id, String nombre, String correo, String nicho, String plan, String modeloVideo,
        boolean conBeneficio, Integer beneficioUsosRestantes, LocalDate proximaFechaPago, EstadoPago estadoPago, Integer videosDelMes,
        EstadoNegocio estado, Instant ultimoAcceso) {
}
