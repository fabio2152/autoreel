package com.autoreel.ia.infraestructura;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor HTTP que responde como la API de Gemini, con respuestas programadas en orden. Guarda las peticiones
 * recibidas para revisarlas. Sin respuestas programadas, responde 500.
 */
public final class ServidorGeminiSimulado implements AutoCloseable {

    public record Peticion(String ruta, String clave, String cuerpo) {
    }

    private record Respuesta(int estado, String cuerpo, Duration demora) {
    }

    private final HttpServer servidor;
    private final Deque<Respuesta> respuestas = new ConcurrentLinkedDeque<>();
    private final List<Peticion> peticiones = new CopyOnWriteArrayList<>();

    public ServidorGeminiSimulado() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** Respuesta 200 con el texto como única parte del primer candidato. */
    public void responderTexto(String texto) {
        responder(200, cuerpoConTexto(texto));
    }

    public void responder(int estado, String cuerpo) {
        respuestas.add(new Respuesta(estado, cuerpo, Duration.ZERO));
    }

    public void responderTarde(Duration demora) {
        respuestas.add(new Respuesta(200, cuerpoConTexto("{}"), demora));
    }

    public List<Peticion> peticiones() {
        return new ArrayList<>(peticiones);
    }

    public void reiniciar() {
        respuestas.clear();
        peticiones.clear();
    }

    public static String cuerpoConTexto(String texto) {
        String escapado = texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":900,"candidatesTokenCount":200,"totalTokenCount":1100}}"""
                .formatted(escapado);
    }

    private void atender(HttpExchange intercambio) throws IOException {
        try (InputStream entrada = intercambio.getRequestBody()) {
            peticiones.add(new Peticion(intercambio.getRequestURI().getPath(),
                    intercambio.getRequestHeaders().getFirst("x-goog-api-key"),
                    new String(entrada.readAllBytes(), StandardCharsets.UTF_8)));
        }
        Respuesta respuesta = respuestas.poll();
        if (respuesta == null) {
            respuesta = new Respuesta(500, "{\"error\":{\"code\":500}}", Duration.ZERO);
        }
        if (!respuesta.demora().isZero()) {
            try {
                Thread.sleep(respuesta.demora());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        try {
            intercambio.sendResponseHeaders(respuesta.estado(), bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        } catch (IOException e) {
            // El cliente se fue por tiempo de espera.
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
