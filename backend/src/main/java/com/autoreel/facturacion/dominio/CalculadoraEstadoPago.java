package com.autoreel.facturacion.dominio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * RN-43: el estado de pago depende de cuántos días faltan para la próxima fecha de pago, según el día de Lima.
 */
public final class CalculadoraEstadoPago {

    private CalculadoraEstadoPago() {
    }

    public static EstadoPago calcular(LocalDate proximaFechaPago, int diasAviso, LocalDate hoy) {
        long diasFaltantes = ChronoUnit.DAYS.between(hoy, proximaFechaPago);
        if (diasFaltantes < 0) {
            return EstadoPago.VENCIDO;
        }
        if (diasFaltantes == 0) {
            return EstadoPago.VENCE_HOY;
        }
        if (diasFaltantes <= diasAviso) {
            return EstadoPago.POR_VENCER;
        }
        return EstadoPago.AL_DIA;
    }
}
