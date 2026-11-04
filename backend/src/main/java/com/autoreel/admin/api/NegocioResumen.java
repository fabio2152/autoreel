package com.autoreel.admin.api;

import java.time.Instant;
import java.time.LocalDate;

import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.negocio.dominio.EstadoNegocio;

/**
 * Fila de la lista de negocios (RF-02). {@code videosDelMes} queda vacío hasta la fase 5, cuando existan los videos.
 */
public record NegocioResumen(Long id, String nombre, String correo, String nicho, String plan,
        LocalDate proximaFechaPago, EstadoPago estadoPago, Integer videosDelMes, EstadoNegocio estado,
        Instant ultimoAcceso) {
}
