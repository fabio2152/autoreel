package com.autoreel.almacenamiento.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba el adaptador S3 contra SeaweedFS real (el mismo de docker compose): URLs firmadas que el navegador usaría,
 * con el tipo y el tamaño exactos, y la copia que solo ocurre si el archivo no cambió.
 */
@Testcontainers
class S3AlmacenTest {

    private static final String CLAVE_ACCESO = "pruebas";
    private static final String CLAVE_SECRETA = "secreto-de-pruebas";

    @Container
    static final GenericContainer<?> SEAWEEDFS = new GenericContainer<>("chrislusf/seaweedfs:4.48")
            .withCommand("server", "-s3", "-dir=/data")
            .withEnv("AWS_ACCESS_KEY_ID", CLAVE_ACCESO)
            .withEnv("AWS_SECRET_ACCESS_KEY", CLAVE_SECRETA)
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCode(403).withStartupTimeout(Duration.ofMinutes(2)));

    private static S3Almacen almacen;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void crearAlmacen() {
        String endpoint = "http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(8333);
        almacen = new S3Almacen(new PropiedadesAlmacenamiento("s3", "autoreel-pruebas", "us-east-1", endpoint, null,
                CLAVE_ACCESO, CLAVE_SECRETA, true));
    }

    @AfterAll
    static void cerrar() {
        almacen.close();
    }

    @Test
    void elNavegadorSubeConLaUrlFirmadaYElBackendLoLee() throws Exception {
        byte[] contenido = "una foto".getBytes();
        String url = almacen.urlDeSubida("subidas/prueba-1.jpg", "image/jpeg", contenido.length, Duration.ofMinutes(5));

        assertThat(subir(url, "image/jpeg", contenido)).isEqualTo(200);

        var leido = almacen.leer("subidas/prueba-1.jpg", 1024).orElseThrow();
        assertThat(leido.contenido()).isEqualTo(contenido);
        assertThat(leido.tamano()).isEqualTo(contenido.length);
        assertThat(almacen.leer("subidas/no-existe.jpg", 1024)).isEmpty();
    }

    @Test
    void laUrlFirmadaNoSirveConOtroTipoNiOtroTamano() throws Exception {
        String url = almacen.urlDeSubida("subidas/prueba-2.jpg", "image/jpeg", 8, Duration.ofMinutes(5));

        assertThat(subir(url, "image/png", "12345678".getBytes())).isEqualTo(403);
        assertThat(subir(url, "image/jpeg", "123456789".getBytes())).isEqualTo(403);
    }

    @Test
    void noLeeElContenidoDeUnArchivoDemasiadoGrande() {
        almacen.guardar("subidas/grande.jpg", new byte[2048], "image/jpeg");

        var leido = almacen.leer("subidas/grande.jpg", 1024).orElseThrow();
        assertThat(leido.contenido()).isNull();
        assertThat(leido.tamano()).isEqualTo(2048);
    }

    @Test
    void copiaSoloSiElArchivoNoCambio() throws Exception {
        almacen.guardar("subidas/prueba-3.jpg", "original".getBytes(), "image/jpeg");
        String etiqueta = almacen.leer("subidas/prueba-3.jpg", 1024).orElseThrow().etiqueta();
        almacen.guardar("subidas/prueba-3.jpg", "reemplazo".getBytes(), "image/jpeg");

        assertThat(almacen.copiarSiNoCambio("subidas/prueba-3.jpg", "definitiva/prueba-3.jpg", etiqueta)).isFalse();

        String actual = almacen.leer("subidas/prueba-3.jpg", 1024).orElseThrow().etiqueta();
        assertThat(almacen.copiarSiNoCambio("subidas/prueba-3.jpg", "definitiva/prueba-3.jpg", actual)).isTrue();
        assertThat(almacen.leer("definitiva/prueba-3.jpg", 1024).orElseThrow().contenido())
                .isEqualTo("reemplazo".getBytes());

        String lectura = almacen.urlDeDescarga("definitiva/prueba-3.jpg", Duration.ofMinutes(5));
        assertThat(http.send(HttpRequest.newBuilder(URI.create(lectura)).build(),
                HttpResponse.BodyHandlers.ofByteArray()).body()).isEqualTo("reemplazo".getBytes());
    }

    private int subir(String url, String tipo, byte[] contenido) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", tipo)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(contenido)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
