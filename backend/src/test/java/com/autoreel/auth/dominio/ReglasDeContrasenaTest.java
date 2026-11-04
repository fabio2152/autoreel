package com.autoreel.auth.dominio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReglasDeContrasenaTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcdefg1", "MiClave2026", "ñandú2026x"})
    void aceptaContrasenasValidas(String contrasena) {
        assertThatCode(() -> ReglasDeContrasena.validar(contrasena, contrasena)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "Abcdef1, Abcdef1, CONTRASENA_MUY_CORTA",
            "SoloLetras, SoloLetras, CONTRASENA_SIN_NUMERO",
            "12345678, 12345678, CONTRASENA_SIN_LETRA",
            "MiClave2026, MiClave2027, CONTRASENAS_NO_COINCIDEN"
    })
    void rechazaContrasenasInvalidas(String nueva, String confirmacion, String codigo) {
        assertThatThrownBy(() -> ReglasDeContrasena.validar(nueva, confirmacion))
                .isInstanceOf(ExcepcionDeNegocio.class)
                .extracting("codigo").isEqualTo(codigo);
    }

    @ParameterizedTest
    @ValueSource(ints = {65, 100})
    void rechazaContrasenasDemasiadoLargas(int longitud) {
        String larga = "a1".repeat(longitud).substring(0, longitud);
        assertThatThrownBy(() -> ReglasDeContrasena.validar(larga, larga))
                .extracting("codigo").isEqualTo("CONTRASENA_MUY_LARGA");
    }
}
