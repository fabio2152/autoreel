package com.autoreel.video.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** V19 y la decisión del usuario: el precio va tal cual; si pasa de 40 caracteres se propone recortado. */
class PrecioDelCierreTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULO", value = {
            "S/ 65|S/ 65",
            "'  2x1 hasta el domingo  '|2x1 hasta el domingo",
            "'   '|NULO",
            "NULO|NULO",
            "S/ 65 la torta entera para compartir en familia este fin de semana|S/ 65 la torta entera para compartir",
            "S/ 65 la torta entera y llévate un postre|S/ 65 la torta entera y llévate",
            "Precioespecialsinespaciosparaningunlugardelmundo|Precioespecialsinespaciosparaningunlugar"
    })
    void recortaEnElLimiteDeUnaPalabraSinConectoresAlFinal(String escrito, String esperado) {
        assertThat(ServicioGuion.precioDelCierre(escrito, 40)).isEqualTo(esperado);
    }
}
