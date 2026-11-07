package com.autoreel.facturacion.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculadoraFechaPagoTest {

    /** Caso V35 y RN-42: un mes después, conservando el día; si el mes no lo tiene, el último día. */
    @ParameterizedTest
    @CsvSource({
            "2026-01-15, 15, 2026-02-15",
            "2027-01-31, 31, 2027-02-28",
            "2028-01-31, 31, 2028-02-29",
            "2027-02-28, 31, 2027-03-31",
            "2026-04-30, 31, 2026-05-31",
            "2026-12-15, 15, 2027-01-15"
    })
    void avanzaUnMesConservandoElDia(LocalDate fecha, int diaPago, LocalDate esperada) {
        assertThat(CalculadoraFechaPago.siguiente(fecha, diaPago)).isEqualTo(esperada);
    }
}
