package com.autoreel.ia.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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

/** Adaptador VEO (Vertex AI) contra un servidor simulado: token, forma de las peticiones, respuestas y errores. */
class VeoGeneradorDeClipsTest {

    private static final SolicitudDeClip SOLICITUD = new SolicitudDeClip(new byte[] {1, 2, 3}, "image/jpeg",
            "Slow push-in on the cake", "people, hands, text", 4);

    private final JsonMapper json = JsonMapper.builder().build();
    private final ServidorVeoSimulado servidor = new ServidorVeoSimulado();
    private final VeoGeneradorDeClips veo = new VeoGeneradorDeClips(new GoogleCloud(
            new GoogleCloud.PropiedadesGoogleCloud(servidor.url(), servidor.url(), servidor.urlToken(),
                    Duration.ofSeconds(1), Duration.ofSeconds(2)), json, Clock.systemUTC()));

    @AfterEach
    void apagar() {
        servidor.close();
    }

    @Test
    void creaLaTareaConLaFotoYLosParametrosDelModelo() {
        String tarea = veo.iniciar(modelo(), servidor.cuentaDeServicio(), SOLICITUD);

        assertThat(tarea).contains("operations/tarea-");
        ServidorVeoSimulado.Peticion peticion = servidor.creacionesRecibidas().getFirst();
        assertThat(peticion.ruta()).isEqualTo("/v1/projects/proyecto-prueba/locations/us-central1/publishers/"
                + "google/models/veo-3.1-lite-generate-001:predictLongRunning");
        assertThat(peticion.autorizacion()).isEqualTo("Bearer token-1");
        JsonNode cuerpo = json.readTree(peticion.cuerpo());
        assertThat(cuerpo.at("/instances/0/prompt").asString()).isEqualTo("Slow push-in on the cake");
        assertThat(cuerpo.at("/instances/0/image/bytesBase64Encoded").asString()).isEqualTo("AQID");
        assertThat(cuerpo.at("/instances/0/image/mimeType").asString()).isEqualTo("image/jpeg");
        assertThat(cuerpo.at("/parameters/aspectRatio").asString()).isEqualTo("9:16");
        assertThat(cuerpo.at("/parameters/durationSeconds").asInt()).isEqualTo(4);
        assertThat(cuerpo.at("/parameters/negativePrompt").asString()).isEqualTo("people, hands, text");
        assertThat(cuerpo.at("/parameters/personGeneration").asString()).isEqualTo("dont_allow");
        assertThat(cuerpo.at("/parameters/generateAudio").asBoolean(true)).isFalse();
        assertThat(cuerpo.at("/parameters/resizeMode").asString()).isEqualTo("crop");
    }

    @Test
    void elTokenSePideUnaVezYSeReutiliza() {
        veo.iniciar(modelo(), servidor.cuentaDeServicio(), SOLICITUD);
        veo.iniciar(modelo(), servidor.cuentaDeServicio(), SOLICITUD);

        assertThat(servidor.tokensEntregados()).isEqualTo(1);
    }

    @Test
    void consultaEnCursoListoYFallido() {
        servidor.consultaEnCurso();
        servidor.consultaLista();
        servidor.consultaFallida();

        assertThat(veo.consultar(modelo(), servidor.cuentaDeServicio(), "tarea")).isInstanceOf(EnCurso.class);
        assertThat(veo.consultar(modelo(), servidor.cuentaDeServicio(), "tarea"))
                .isInstanceOfSatisfying(Listo.class, listo -> {
                    assertThat(listo.video()).isEqualTo(ServidorVeoSimulado.CLIP);
                    assertThat(listo.tipoContenido()).isEqualTo("video/mp4");
                });
        assertThat(veo.consultar(modelo(), servidor.cuentaDeServicio(), "tarea"))
                .isInstanceOfSatisfying(Fallido.class, fallido -> assertThat(fallido.motivo()).contains("filtro"));
        JsonNode consulta = json.readTree(servidor.peticiones().getLast().cuerpo());
        assertThat(consulta.path("operationName").asString()).isEqualTo("tarea");
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
    void clasificaLosErrores(int estado, ErrorDeProveedor.Tipo tipo) {
        servidor.crearResponde(estado, "{\"error\":{\"code\":" + estado + "}}");

        assertThatThrownBy(() -> veo.iniciar(modelo(), servidor.cuentaDeServicio(), SOLICITUD))
                .isInstanceOfSatisfying(ErrorDeProveedor.class, error -> {
                    assertThat(error.getTipo()).isEqualTo(tipo);
                    assertThat(error.getMessage()).doesNotContain("PRIVATE KEY");
                });
    }

    @Test
    void unaCredencialQueNoEsUnaCuentaDeServicioEsInvalida() {
        assertThatThrownBy(() -> veo.iniciar(modelo(), "clave-de-gemini", SOLICITUD))
                .isInstanceOfSatisfying(ErrorDeProveedor.class,
                        error -> assertThat(error.getTipo()).isEqualTo(ErrorDeProveedor.Tipo.CREDENCIAL_INVALIDA));
        assertThat(servidor.creacionesRecibidas()).isEmpty();
    }

    private static ModeloIA modelo() {
        ModeloIA modelo = BeanUtils.instantiateClass(ModeloIA.class);
        ReflectionTestUtils.setField(modelo, "nombre", "Veo 3.1 Lite");
        ReflectionTestUtils.setField(modelo, "proveedor", "GOOGLE");
        ReflectionTestUtils.setField(modelo, "tipo", TipoModelo.VIDEO);
        ReflectionTestUtils.setField(modelo, "adaptador", "VEO");
        ReflectionTestUtils.setField(modelo, "identificadorModelo", "veo-3.1-lite-generate-001");
        ReflectionTestUtils.setField(modelo, "parametros", Map.of("region", "us-central1", "relacionAspecto", "9:16",
                "resolucion", "720p", "generarAudio", false));
        ReflectionTestUtils.setField(modelo, "costoReferencial", new BigDecimal("0.05"));
        ReflectionTestUtils.setField(modelo, "unidadCosto", UnidadCosto.POR_SEGUNDO);
        ReflectionTestUtils.setField(modelo, "nombreSecreto", "autoreel/google-cloud");
        ReflectionTestUtils.setField(modelo, "estado", EstadoModelo.ACTIVO);
        return modelo;
    }
}
