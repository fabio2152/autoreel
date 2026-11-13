package com.autoreel.ia.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.EstadoModelo;
import com.autoreel.ia.dominio.GeneradorDeClips.EnCurso;
import com.autoreel.ia.dominio.GeneradorDeClips.Fallido;
import com.autoreel.ia.dominio.GeneradorDeClips.Listo;
import com.autoreel.ia.dominio.GeneradorDeClips.SolicitudDeClip;
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

/** Adaptador GEMINI_OMNI (AI Studio) contra un servidor simulado: petición, modo síncrono y en segundo plano, errores. */
class OmniGeneradorDeClipsTest {

    private static final SolicitudDeClip SOLICITUD = new SolicitudDeClip(new byte[] {1, 2, 3}, "image/jpeg",
            "Slow push-in on the cake.", "people, hands, text", 4);

    private final JsonMapper json = JsonMapper.builder().build();
    private final ServidorOmniSimulado servidor = new ServidorOmniSimulado();
    private final OmniGeneradorDeClips omni = new OmniGeneradorDeClips(
            new GeminiGuionista.PropiedadesGemini(servidor.url(), Duration.ofSeconds(1), Duration.ofSeconds(1)),
            new OmniGeneradorDeClips.PropiedadesOmni(Duration.ofSeconds(2)), json);

    @AfterEach
    void apagar() {
        servidor.close();
    }

    @Test
    void enviaLaFotoElMovimientoYElFormatoVertical() {
        omni.iniciar(modelo(false), "clave-de-prueba", SOLICITUD);

        ServidorOmniSimulado.Peticion peticion = servidor.creacionesRecibidas().getFirst();
        assertThat(peticion.clave()).isEqualTo("clave-de-prueba");
        JsonNode cuerpo = json.readTree(peticion.cuerpo());
        assertThat(cuerpo.path("model").asString()).isEqualTo("gemini-omni-1.2-flash");
        assertThat(cuerpo.at("/input/0/type").asString()).isEqualTo("image");
        assertThat(cuerpo.at("/input/0/data").asString()).isEqualTo("AQID");
        assertThat(cuerpo.at("/input/0/mime_type").asString()).isEqualTo("image/jpeg");
        assertThat(cuerpo.at("/input/1/text").asString())
                .isEqualTo("Slow push-in on the cake. Avoid: people, hands, text.");
        assertThat(cuerpo.at("/response_format/type").asString()).isEqualTo("video");
        assertThat(cuerpo.at("/response_format/aspect_ratio").asString()).isEqualTo("9:16");
        assertThat(cuerpo.at("/response_format/resolution").asString()).isEqualTo("720p");
        assertThat(cuerpo.at("/response_format/duration").asString()).isEqualTo("4s");
        assertThat(cuerpo.path("background").asBoolean(true)).isFalse();
        assertThat(cuerpo.path("store").asBoolean(false)).isTrue();
    }

    @Test
    void enModoSincronoElVideoDeLaCreacionSeEntregaEnLaConsultaSinVolverAPedirlo() {
        String id = omni.iniciar(modelo(false), "clave", SOLICITUD);

        assertThat(omni.consultar(modelo(false), "clave", id)).isInstanceOfSatisfying(Listo.class, listo -> {
            assertThat(listo.video()).isEqualTo(ServidorOmniSimulado.CLIP);
            assertThat(listo.tipoContenido()).isEqualTo("video/mp4");
            assertThat(listo.segundosGenerados()).isCloseTo(4.0, within(0.001));
        });
        assertThat(servidor.peticiones()).hasSize(1);
    }

    @Test
    void enSegundoPlanoSeConsultaHastaQueTermina() {
        servidor.crearEnCurso();
        String id = omni.iniciar(modelo(true), "clave", SOLICITUD);
        servidor.consultaResponde(200, ServidorOmniSimulado.enCurso(id));

        assertThat(json.readTree(servidor.creacionesRecibidas().getFirst().cuerpo()).path("background").asBoolean())
                .isTrue();
        assertThat(omni.consultar(modelo(true), "clave", id)).isInstanceOf(EnCurso.class);
        assertThat(omni.consultar(modelo(true), "clave", id)).isInstanceOf(Listo.class);
        assertThat(servidor.peticiones().getLast().ruta()).isEqualTo("/v1beta/interactions/" + id);
    }

    @Test
    void unaInteraccionFallidaOSinVideoEsUnClipFallido() {
        servidor.crearFallida();
        String id = omni.iniciar(modelo(false), "clave", SOLICITUD);
        assertThat(omni.consultar(modelo(false), "clave", id))
                .isInstanceOfSatisfying(Fallido.class, f -> assertThat(f.motivo()).contains("Bloqueado"));

        servidor.crearResponde(200, "{\"id\":\"vacia\",\"status\":\"completed\",\"steps\":[]}");
        String vacia = omni.iniciar(modelo(false), "clave", SOLICITUD);
        assertThat(omni.consultar(modelo(false), "clave", vacia)).isInstanceOf(Fallido.class);
    }

    @ParameterizedTest
    @CsvSource({
            "401, CREDENCIAL_INVALIDA",
            "403, CREDENCIAL_INVALIDA",
            "400, CONFIGURACION_INVALIDA",
            "404, CONFIGURACION_INVALIDA",
            "429, NO_DISPONIBLE",
            "503, NO_DISPONIBLE"
    })
    void clasificaLosErroresSinMostrarLaClave(int estado, ErrorDeProveedor.Tipo tipo) {
        servidor.crearResponde(estado, "{\"error\":{\"code\":\"x\",\"message\":\"y\"}}");

        assertThatThrownBy(() -> omni.iniciar(modelo(false), "clave-secreta", SOLICITUD))
                .isInstanceOfSatisfying(ErrorDeProveedor.class, error -> {
                    assertThat(error.getTipo()).isEqualTo(tipo);
                    assertThat(error.getMessage()).doesNotContain("clave-secreta");
                });
    }

    private static ModeloIA modelo(boolean segundoPlano) {
        ModeloIA modelo = BeanUtils.instantiateClass(ModeloIA.class);
        Map<String, Object> parametros = new HashMap<>(Map.of("relacionAspecto", "9:16", "resolucion", "720p",
                "duracionClipSegundos", 4, "tokensPorSegundo", 5792));
        parametros.put("segundoPlano", segundoPlano);
        ReflectionTestUtils.setField(modelo, "nombre", "Gemini Omni Flash");
        ReflectionTestUtils.setField(modelo, "proveedor", "GOOGLE");
        ReflectionTestUtils.setField(modelo, "tipo", TipoModelo.VIDEO);
        ReflectionTestUtils.setField(modelo, "adaptador", "GEMINI_OMNI");
        ReflectionTestUtils.setField(modelo, "identificadorModelo", "gemini-omni-1.2-flash");
        ReflectionTestUtils.setField(modelo, "parametros", parametros);
        ReflectionTestUtils.setField(modelo, "costoReferencial", new BigDecimal("0.07"));
        ReflectionTestUtils.setField(modelo, "unidadCosto", UnidadCosto.POR_SEGUNDO);
        ReflectionTestUtils.setField(modelo, "nombreSecreto", "autoreel/gemini");
        ReflectionTestUtils.setField(modelo, "estado", EstadoModelo.ACTIVO);
        return modelo;
    }
}
