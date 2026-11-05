package com.autoreel.ia.infraestructura;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
 * Servidor HTTP que responde como la API de interacciones de Gemini (Gemini Omni Flash). Las respuestas de crear y
 * consultar se programan en orden; sin respuestas programadas, crear devuelve una interacción terminada con el video
 * (4 segundos de tokens de video a 720p) y consultar también. Cualquier otra ruta (por ejemplo, el guion) responde 404.
 */
public final class ServidorOmniSimulado implements AutoCloseable {

    public record Peticion(String metodo, String ruta, String clave, String cuerpo) {
    }

    private record Respuesta(int estado, String cuerpo) {
    }

    /** Un MP4 cualquiera: el componedor de las pruebas de integración no lo lee. */
    public static final byte[] CLIP = "clip-omni-de-prueba".getBytes(StandardCharsets.UTF_8);
    /** 4 segundos a 720p (5.792 tokens por segundo). */
    public static final int TOKENS_DE_VIDEO = 4 * 5792;

    private final HttpServer servidor;
    private final Deque<Respuesta> creaciones = new ConcurrentLinkedDeque<>();
    private final Deque<Respuesta> consultas = new ConcurrentLinkedDeque<>();
    private final List<Peticion> peticiones = new CopyOnWriteArrayList<>();
    private final AtomicInteger interacciones = new AtomicInteger();

    public ServidorOmniSimulado() {
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

    public static String terminada(String id) {
        return """
                {"id":"%s","object":"interaction","model":"gemini-omni-1.2-flash","status":"completed",
                 "steps":[{"type":"user_input"},{"type":"model_output","content":[
                   {"type":"video","mime_type":"video/mp4","data":"%s"}]}],
                 "usage":{"total_output_tokens":%d,"output_tokens_by_modality":[{"modality":"video","tokens":%d}]}}"""
                .formatted(id, Base64.getEncoder().encodeToString(CLIP), TOKENS_DE_VIDEO, TOKENS_DE_VIDEO);
    }

    public static String fallida(String id) {
        return """
                {"id":"%s","object":"interaction","status":"failed",
                 "errors":[{"code":"https://ai.google.dev/errors/safety","message":"Bloqueado por seguridad"}]}"""
                .formatted(id);
    }

    public static String enCurso(String id) {
        return "{\"id\":\"" + id + "\",\"object\":\"interaction\",\"status\":\"in_progress\"}";
    }

    public void crearResponde(int estado, String cuerpo) {
        creaciones.add(new Respuesta(estado, cuerpo));
    }

    /** La creación devuelve una interacción terminada sin video (por ejemplo, bloqueada). */
    public void crearFallida() {
        crearResponde(200, fallida("int-fallida-" + interacciones.incrementAndGet()));
    }

    /** La creación queda en curso (modo en segundo plano); hay que consultarla. */
    public void crearEnCurso() {
        crearResponde(200, enCurso("int-curso-" + interacciones.incrementAndGet()));
    }

    public void consultaResponde(int estado, String cuerpo) {
        consultas.add(new Respuesta(estado, cuerpo));
    }

    public List<Peticion> creacionesRecibidas() {
        return peticiones.stream().filter(p -> p.metodo().equals("POST") && p.ruta().equals("/v1beta/interactions"))
                .toList();
    }

    public List<Peticion> peticiones() {
        return new ArrayList<>(peticiones);
    }

    public void reiniciar() {
        creaciones.clear();
        consultas.clear();
        peticiones.clear();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String metodo = intercambio.getRequestMethod();
        String ruta = intercambio.getRequestURI().getPath();
        String cuerpo;
        try (InputStream entrada = intercambio.getRequestBody()) {
            cuerpo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
        peticiones.add(new Peticion(metodo, ruta, intercambio.getRequestHeaders().getFirst("x-goog-api-key"), cuerpo));
        Respuesta respuesta;
        if (metodo.equals("POST") && ruta.equals("/v1beta/interactions")) {
            Respuesta programada = creaciones.poll();
            respuesta = programada != null ? programada
                    : new Respuesta(200, terminada("int-" + interacciones.incrementAndGet()));
        } else if (metodo.equals("GET") && ruta.startsWith("/v1beta/interactions/")) {
            Respuesta programada = consultas.poll();
            respuesta = programada != null ? programada
                    : new Respuesta(200, terminada(ruta.substring(ruta.lastIndexOf('/') + 1)));
        } else {
            respuesta = new Respuesta(404, "{\"error\":{\"code\":\"not_found\",\"message\":\"sin ruta\"}}");
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
