package com.autoreel.video.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.TipoModelo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Cuántos segundos de clip se le piden al proveedor según lo que dura cada toma (guion flexible). */
class SegundosDelClipTest {

    /** Veo admite clips de 4, 6 u 8 segundos: se pide la duración más corta que cubre la toma. */
    @ParameterizedTest
    @CsvSource({"3, 4", "4, 4", "5, 6", "6, 6", "8, 8", "10, 8"})
    void conVariasDuracionesEligeLaMasCortaQueCubreLaToma(int segundosDelPlano, int esperados) {
        ModeloIA veo = modelo(Map.of("duracionesClip", List.of(8, 4, 6)));

        assertThat(ServicioGeneracion.segundosDelClip(veo, segundosDelPlano)).isEqualTo(esperados);
    }

    /** Con recorte inicial (Veo, V13), el clip debe cubrir la toma más el segundo que se salta al componer. */
    @ParameterizedTest
    @CsvSource({"3, 4", "5, 6", "6, 8", "8, 8"})
    void conRecorteInicialElClipCubreLaTomaMasElRecorte(int segundosDelPlano, int esperados) {
        ModeloIA veo = modelo(Map.of("duracionesClip", List.of(4, 6, 8), "recorteInicialSegundos", 1.0));

        assertThat(ServicioGeneracion.segundosDelClip(veo, segundosDelPlano)).isEqualTo(esperados);
        assertThat(ServicioGeneracion.recorteInicial(veo)).isEqualTo(1.0);
    }

    @ParameterizedTest
    @CsvSource({"3, 4", "8, 4"})
    void sinListaUsaLaDuracionFijaDelModelo(int segundosDelPlano, int esperados) {
        ModeloIA omni = modelo(Map.of("duracionClipSegundos", 4));

        assertThat(ServicioGeneracion.segundosDelClip(omni, segundosDelPlano)).isEqualTo(esperados);
    }

    private static ModeloIA modelo(Map<String, Object> parametros) {
        return new ModeloIA("Modelo de video", "GOOGLE", TipoModelo.VIDEO, "VEO", "modelo-de-prueba", parametros,
                new BigDecimal("0.05"), "autoreel/prueba");
    }
}
