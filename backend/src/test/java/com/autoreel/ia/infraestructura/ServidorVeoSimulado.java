package com.autoreel.ia.infraestructura;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor HTTP que responde como Veo en Google Cloud (Vertex AI) y como el endpoint de tokens de Google. Las
 * respuestas de crear y consultar tareas se programan en orden; sin respuestas programadas, crear devuelve una tarea
 * nueva y consultar responde que el clip está listo.
 */
public final class ServidorVeoSimulado implements AutoCloseable {

    public record Peticion(String ruta, String autorizacion, String cuerpo) {
    }

    private record Respuesta(int estado, String cuerpo) {
    }

    /** Un MP4 cualquiera: el componedor de las pruebas de integración no lo lee. */
    public static final byte[] CLIP = "clip-de-prueba".getBytes(StandardCharsets.UTF_8);

    private final HttpServer servidor;
    private final Deque<Respuesta> creaciones = new ConcurrentLinkedDeque<>();
    private final Deque<Respuesta> consultas = new ConcurrentLinkedDeque<>();
    private final List<Peticion> peticiones = new CopyOnWriteArrayList<>();
    private final AtomicInteger tareas = new AtomicInteger();
    private final AtomicInteger tokens = new AtomicInteger();
    private final String cuentaDeServicio;

    public ServidorVeoSimulado() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            KeyPair claves = generador.generateKeyPair();
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                            .encodeToString(claves.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            cuentaDeServicio = """
                    {"type":"service_account","project_id":"proyecto-prueba","private_key_id":"clave-1",
                     "private_key":"%s","client_email":"veo@proyecto-prueba.iam.gserviceaccount.com",
                     "token_uri":"https://oauth2.googleapis.com/token"}"""
                    .formatted(pem.replace("\n", "\\n"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public String urlToken() {
        return url() + "/token";
    }

    /** El JSON de una cuenta de servicio con una clave RSA real, generada para la prueba. */
    public String cuentaDeServicio() {
        return cuentaDeServicio;
    }

    public void crearResponde(int estado, String cuerpo) {
        creaciones.add(new Respuesta(estado, cuerpo));
    }

    public void consultaResponde(int estado, String cuerpo) {
        consultas.add(new Respuesta(estado, cuerpo));
    }

    public void consultaEnCurso() {
        consultaResponde(200, "{\"name\":\"op\",\"done\":false}");
    }

    public void consultaLista() {
        consultaResponde(200, listo());
    }

    public void consultaFallida() {
        consultaResponde(200, "{\"name\":\"op\",\"done\":true,\"response\":{\"raiMediaFilteredCount\":1,"
                + "\"raiMediaFilteredReasons\":[\"filtrado\"]}}");
    }

    public List<Peticion> peticiones() {
        return new ArrayList<>(peticiones);
    }

    public List<Peticion> creacionesRecibidas() {
        return peticiones.stream().filter(p -> p.ruta().endsWith(":predictLongRunning")).toList();
    }

    public int tokensEntregados() {
        return tokens.get();
    }

    public void reiniciar() {
        creaciones.clear();
        consultas.clear();
        peticiones.clear();
    }

    private static String listo() {
        return "{\"name\":\"op\",\"done\":true,\"response\":{\"@type\":\"type.googleapis.com/cloud.ai.large_models"
                + ".vision.GenerateVideoResponse\",\"raiMediaFilteredCount\":0,\"videos\":[{\"bytesBase64Encoded\":\""
                + Base64.getEncoder().encodeToString(CLIP) + "\",\"mimeType\":\"video/mp4\"}]}}";
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        String cuerpo;
        try (InputStream entrada = intercambio.getRequestBody()) {
            cuerpo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        peticiones.add(new Peticion(ruta, intercambio.getRequestHeaders().getFirst("Authorization") != null
                ? intercambio.getRequestHeaders().getFirst("Authorization")
                : "clave " + intercambio.getRequestHeaders().getFirst("x-goog-api-key"), cuerpo));
        Respuesta respuesta;
        if (ruta.equals("/token")) {
            tokens.incrementAndGet();
            respuesta = cuerpo.contains("assertion=")
                    ? new Respuesta(200, "{\"access_token\":\"token-" + tokens.get() + "\",\"expires_in\":3600}")
                    : new Respuesta(400, "{\"error\":\"invalid_grant\"}");
        } else if (ruta.endsWith(":predictLongRunning")) {
            Respuesta programada = creaciones.poll();
            respuesta = programada != null ? programada : new Respuesta(200,
                    "{\"name\":\"projects/proyecto-prueba/locations/us-central1/publishers/google/models/veo/"
                            + "operations/tarea-" + tareas.incrementAndGet() + "\"}");
        } else if (ruta.endsWith(":fetchPredictOperation")) {
            Respuesta programada = consultas.poll();
            respuesta = programada != null ? programada : new Respuesta(200, listo());
        } else {
            respuesta = new Respuesta(404, "{}");
        }
        byte[] bytes = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(respuesta.estado(), bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
