package com.autoreel.chat.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.autoreel.chat.dominio.EvaluadorDeRespuesta.Aceptada;
import com.autoreel.chat.dominio.EvaluadorDeRespuesta.Rechazada;
import com.autoreel.nicho.dominio.GuionChat;
import com.autoreel.nicho.pasteleria.PaquetePasteleria;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Reglas de la sección 7.3 con el guion real del paquete Pastelería. */
class EvaluadorDeRespuestaTest {

    private final GuionChat guion = new PaquetePasteleria(JsonMapper.builder().build()).guionDelChat();
    private final Map<String, Object> estrella = Map.of("tipoVideo", "PRODUCTO_ESTRELLA");
    private final Map<String, Object> promocion = Map.of("tipoVideo", "PROMOCION");

    @ParameterizedTest
    @ValueSource(strings = {"¿Cuánto cuesta el video?", "es rico? sí"})
    void v16UnaPreguntaRecibeElMensajeFijo(String texto) {
        assertThat(EvaluadorDeRespuesta.evaluar(guion, guion.paso("especial"), estrella, texto))
                .isEqualTo(new Rechazada(guion.mensajes().pregunta()));
    }

    @Test
    void unaRespuestaObligatoriaMuyCortaPideMasDetalle() {
        assertThat(EvaluadorDeRespuesta.evaluar(guion, guion.paso("especial"), estrella, " ok "))
                .isEqualTo(new Rechazada(guion.mensajes().muyCorta()));
        assertThat(EvaluadorDeRespuesta.evaluar(guion, guion.paso("especial"), estrella, "Fresas frescas"))
                .isEqualTo(new Aceptada("Fresas frescas"));
    }

    @Test
    void masDe500CaracteresPideResumir() {
        assertThat(EvaluadorDeRespuesta.evaluar(guion, guion.paso("extra"), estrella, "a".repeat(501)))
                .isEqualTo(new Rechazada(guion.mensajes().muyLarga()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "No.", "  NADA ", "no hay", "Sin precio", "-"})
    void unNoEnUnaPreguntaOpcionalSeGuardaVacio(String texto) {
        assertThat(EvaluadorDeRespuesta.evaluar(guion, guion.paso("precio"), estrella, texto))
                .isEqualTo(new Aceptada(null));
    }

    @Test
    void v17EnUnaPromocionElPrecioEsObligatorio() {
        assertThat(EvaluadorDeRespuesta.evaluar(guion, guion.paso("precio"), promocion, "no"))
                .isEqualTo(new Rechazada(guion.mensajes().precioObligatorio()));
        assertThat(EvaluadorDeRespuesta.omitir(guion, guion.paso("precio"), promocion))
                .isEqualTo(new Rechazada(guion.mensajes().precioObligatorio()));
        assertThat(EvaluadorDeRespuesta.evaluar(guion, guion.paso("precio"), promocion, "S/ 65"))
                .isEqualTo(new Aceptada("S/ 65"));
    }

    @Test
    void omitirUnaPreguntaOpcionalLaDejaVacia() {
        assertThat(EvaluadorDeRespuesta.omitir(guion, guion.paso("extra"), estrella)).isEqualTo(new Aceptada(null));
    }
}
