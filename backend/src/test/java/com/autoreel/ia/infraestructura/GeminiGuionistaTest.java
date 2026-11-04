package com.autoreel.ia.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.EstadoModelo;
import com.autoreel.ia.dominio.Guionista.RespuestaDelModelo;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.TipoModelo;
import com.autoreel.ia.dominio.UnidadCosto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Adaptador GEMINI contra un servidor simulado: forma de la petición, lectura de la respuesta y errores. */
class GeminiGuionistaTest {

    private static final String ESQUEMA = "{\"type\":\"object\"}";

    private final JsonMapper json = JsonMapper.builder().build();
    private final ServidorGeminiSimulado servidor = new ServidorGeminiSimulado();
    private final GeminiGuionista guionista = new GeminiGuionista(
            new GeminiGuionista.PropiedadesGemini(servidor.url(), Duration.ofSeconds(1), Duration.ofMillis(500)),
            json);

    @AfterEach
    void apagar() {
        servidor.close();
    }

    @Test
    void enviaLaPeticionConElModeloLaClaveYElEsquema() {
        servidor.responderTexto("{\"planos\":[]}");

        RespuestaDelModelo respuesta = guionista.escribir(modelo(), "clave-de-prueba", "Escribe el guion", ESQUEMA);

        assertThat(respuesta.texto()).isEqualTo("{\"planos\":[]}");
        ServidorGeminiSimulado.Peticion peticion = servidor.peticiones().getFirst();
        assertThat(peticion.ruta()).isEqualTo("/v1beta/models/gemini-de-prueba:generateContent");
        assertThat(peticion.clave()).isEqualTo("clave-de-prueba");
        JsonNode cuerpo = json.readTree(peticion.cuerpo());
        assertThat(cuerpo.at("/contents/0/role").asString()).isEqualTo("user");
        assertThat(cuerpo.at("/contents/0/parts/0/text").asString()).isEqualTo("Escribe el guion");
        assertThat(cuerpo.at("/generationConfig/responseMimeType").asString()).isEqualTo("application/json");
        assertThat(cuerpo.at("/generationConfig/responseJsonSchema/type").asString()).isEqualTo("object");
        assertThat(cuerpo.at("/generationConfig/temperature").asDouble()).isEqualTo(0.4);
    }

    @Test
    void ignoraLasPartesDePensamientoYUneLasDemas() {
        servidor.responder(200, """
                {"candidates":[{"content":{"parts":[{"text":"pensando...","thought":true},{"text":"{\\"a\\":"},
                {"text":"1}"}]},"finishReason":"STOP"}]}""");

        assertThat(guionista.escribir(modelo(), "clave", "x", ESQUEMA).texto()).isEqualTo("{\"a\":1}");
    }

    @Test
    void unaRespuestaBloqueadaOCortadaVuelveSinTexto() {
        servidor.responder(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        servidor.responder(200, """
                {"candidates":[{"content":{"parts":[{"text":"{\\"pla"}]},"finishReason":"MAX_TOKENS"}]}""");
        servidor.responder(200, "{\"candidates\":[]}");

        assertThat(guionista.escribir(modelo(), "clave", "x", ESQUEMA).problema()).contains("SAFETY");
        assertThat(guionista.escribir(modelo(), "clave", "x", ESQUEMA).problema()).contains("MAX_TOKENS");
        assertThat(guionista.escribir(modelo(), "clave", "x", ESQUEMA).texto()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "401, CREDENCIAL_INVALIDA",
            "403, CREDENCIAL_INVALIDA",
            "400, CONFIGURACION_INVALIDA",
            "404, CONFIGURACION_INVALIDA",
            "429, NO_DISPONIBLE",
            "500, NO_DISPONIBLE",
            "503, NO_DISPONIBLE"
    })
    void clasificaLosErroresSinMostrarLaClave(int estado, ErrorDeProveedor.Tipo tipo) {
        servidor.responder(estado, "{\"error\":{\"code\":" + estado + "}}");

        assertThatThrownBy(() -> guionista.escribir(modelo(), "clave-secreta", "x", ESQUEMA))
                .isInstanceOfSatisfying(ErrorDeProveedor.class, error -> {
                    assertThat(error.getTipo()).isEqualTo(tipo);
                    assertThat(error.getMessage()).doesNotContain("clave-secreta");
                });
    }

    @Test
    void siNoRespondeATiempoNoEstaDisponible() {
        servidor.responderTarde(Duration.ofSeconds(2));

        assertThatThrownBy(() -> guionista.escribir(modelo(), "clave", "x", ESQUEMA))
                .isInstanceOfSatisfying(ErrorDeProveedor.class,
                        error -> assertThat(error.getTipo()).isEqualTo(ErrorDeProveedor.Tipo.NO_DISPONIBLE));
    }

    private static ModeloIA modelo() {
        ModeloIA modelo = BeanUtils.instantiateClass(ModeloIA.class);
        ReflectionTestUtils.setField(modelo, "nombre", "Gemini de prueba");
        ReflectionTestUtils.setField(modelo, "proveedor", "GOOGLE");
        ReflectionTestUtils.setField(modelo, "tipo", TipoModelo.TEXTO);
        ReflectionTestUtils.setField(modelo, "adaptador", "GEMINI");
        ReflectionTestUtils.setField(modelo, "identificadorModelo", "gemini-de-prueba");
        ReflectionTestUtils.setField(modelo, "parametros", Map.of("temperatura", 0.4));
        ReflectionTestUtils.setField(modelo, "costoReferencial", new BigDecimal("0.01"));
        ReflectionTestUtils.setField(modelo, "unidadCosto", UnidadCosto.POR_SOLICITUD);
        ReflectionTestUtils.setField(modelo, "nombreSecreto", "autoreel/gemini");
        ReflectionTestUtils.setField(modelo, "estado", EstadoModelo.ACTIVO);
        return modelo;
    }
}
