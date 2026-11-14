package com.autoreel.ia.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.EstadoModelo;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.TipoModelo;
import com.autoreel.ia.dominio.UnidadCosto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador GEMINI_CLOUD (Gemini en Google Cloud con la cuenta de servicio) contra un servidor simulado: token, ruta
 * global de {@code generateContent}, cuerpo y lectura de la respuesta.
 */
class GeminiCloudGuionistaTest {

    private final JsonMapper json = JsonMapper.builder().build();
    /** Reutiliza la cuenta de servicio con clave RSA real del servidor de Veo; su servidor no se usa. */
    private final ServidorVeoSimulado cuentas = new ServidorVeoSimulado();
    private final List<String[]> peticiones = new CopyOnWriteArrayList<>();
    private final AtomicInteger estadoRespuesta = new AtomicInteger(200);
    private final HttpServer servidor;
    private final GeminiCloudGuionista guionista;

    GeminiCloudGuionistaTest() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            String cuerpo;
            try (InputStream entrada = intercambio.getRequestBody()) {
                cuerpo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            }
            String ruta = intercambio.getRequestURI().getPath();
            peticiones.add(new String[] {ruta, intercambio.getRequestHeaders().getFirst("Authorization"), cuerpo});
            String respuesta = ruta.equals("/token") ? "{\"access_token\":\"token-1\",\"expires_in\":3600}"
                    : ServidorGeminiSimulado.cuerpoConTexto("{\"planos\":[]}");
            int estado = ruta.equals("/token") ? 200 : estadoRespuesta.get();
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort();
        guionista = new GeminiCloudGuionista(new GoogleCloud(new GoogleCloud.PropiedadesGoogleCloud(url, url,
                url + "/token", Duration.ofSeconds(1), Duration.ofSeconds(2)), json, Clock.systemUTC()), json);
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        cuentas.close();
    }

    @Test
    void llamaAGenerateContentEnLaUbicacionGlobalConElToken() {
        var respuesta = guionista.escribir(modelo(), cuentas.cuentaDeServicio(), "Escribe el guion",
                "{\"type\":\"object\"}");

        assertThat(respuesta.texto()).isEqualTo("{\"planos\":[]}");
        String[] llamada = peticiones.stream().filter(p -> !p[0].equals("/token")).findFirst().orElseThrow();
        assertThat(llamada[0]).isEqualTo("/v1/projects/proyecto-prueba/locations/global/publishers/google/models/"
                + "gemini-3.8-flash:generateContent");
        assertThat(llamada[1]).isEqualTo("Bearer token-1");
        JsonNode cuerpo = json.readTree(llamada[2]);
        assertThat(cuerpo.at("/contents/0/parts/0/text").asString()).isEqualTo("Escribe el guion");
        assertThat(cuerpo.at("/generationConfig/responseMimeType").asString()).isEqualTo("application/json");
        assertThat(cuerpo.at("/generationConfig/responseJsonSchema/type").asString()).isEqualTo("object");
    }

    @Test
    void sinPermisosEsCredencialInvalida() {
        estadoRespuesta.set(403);

        assertThatThrownBy(() -> guionista.escribir(modelo(), cuentas.cuentaDeServicio(), "x", "{}"))
                .isInstanceOfSatisfying(ErrorDeProveedor.class,
                        error -> assertThat(error.getTipo()).isEqualTo(ErrorDeProveedor.Tipo.CREDENCIAL_INVALIDA));
    }

    @Test
    void unJsonQueNoEsUnaCuentaDeServicioEsInvalido() {
        assertThatThrownBy(() -> guionista.escribir(modelo(), "{\"type\":\"otra-cosa\"}", "x", "{}"))
                .isInstanceOfSatisfying(ErrorDeProveedor.class,
                        error -> assertThat(error.getTipo()).isEqualTo(ErrorDeProveedor.Tipo.CREDENCIAL_INVALIDA));
    }

    private static ModeloIA modelo() {
        ModeloIA modelo = BeanUtils.instantiateClass(ModeloIA.class);
        ReflectionTestUtils.setField(modelo, "nombre", "Gemini (Google Cloud)");
        ReflectionTestUtils.setField(modelo, "proveedor", "GOOGLE");
        ReflectionTestUtils.setField(modelo, "tipo", TipoModelo.TEXTO);
        ReflectionTestUtils.setField(modelo, "adaptador", "GEMINI_CLOUD");
        ReflectionTestUtils.setField(modelo, "identificadorModelo", "gemini-3.8-flash");
        ReflectionTestUtils.setField(modelo, "parametros", Map.of("ubicacion", "global", "temperatura", 0.7));
        ReflectionTestUtils.setField(modelo, "costoReferencial", new BigDecimal("0.01"));
        ReflectionTestUtils.setField(modelo, "unidadCosto", UnidadCosto.POR_SOLICITUD);
        ReflectionTestUtils.setField(modelo, "nombreSecreto", "autoreel/google-cloud");
        ReflectionTestUtils.setField(modelo, "estado", EstadoModelo.ACTIVO);
        return modelo;
    }
}
