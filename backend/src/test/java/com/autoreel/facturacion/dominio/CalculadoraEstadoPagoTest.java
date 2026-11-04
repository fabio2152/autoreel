package com.autoreel.facturacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculadoraEstadoPagoTest {

    private static final LocalDate FECHA_PAGO = LocalDate.of(2026, 1, 15);

    /** Caso V34: fecha de pago el 15 y 5 días de aviso. */
    @ParameterizedTest
    @CsvSource({
            "2026-01-09, AL_DIA",
            "2026-01-10, POR_VENCER",
            "2026-01-14, POR_VENCER",
            "2026-01-15, VENCE_HOY",
            "2026-01-16, VENCIDO",
            "2026-02-20, VENCIDO"
    })
    void calculaElEstadoSegunLosDiasQueFaltan(LocalDate hoy, EstadoPago esperado) {
        assertThat(CalculadoraEstadoPago.calcular(FECHA_PAGO, 5, hoy)).isEqualTo(esperado);
    }
}
