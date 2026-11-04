package com.autoreel.chat.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * RN-17: los mensajes enviados con menos de 4 segundos de diferencia forman una sola respuesta. Un grupo se cierra
 * cuando pasan 4 segundos sin mensajes nuevos.
 */
public final class AgrupadorDeMensajes {

    public static final Duration ESPERA = Duration.ofSeconds(4);

    /**
     * @param cerrados    grupos listos para procesarse, en orden.
     * @param abiertos    mensajes del último grupo, que todavía puede crecer.
     * @param cierreEn    cuánto falta para que se cierre el grupo abierto; vacío si no hay ninguno.
     */
    public record Resultado<T>(List<List<T>> cerrados, List<T> abiertos, Duration cierreEn) {
    }

    private AgrupadorDeMensajes() {
    }

    /** Agrupa mensajes ordenados por fecha; {@code fecha} indica cuándo se envió cada uno. */
    public static <T> Resultado<T> agrupar(List<T> mensajes, Function<T, Instant> fecha, Instant ahora) {
        List<List<T>> grupos = new ArrayList<>();
        List<T> actual = new ArrayList<>();
        Instant anterior = null;
        for (T mensaje : mensajes) {
            Instant cuando = fecha.apply(mensaje);
            if (anterior != null && Duration.between(anterior, cuando).compareTo(ESPERA) >= 0) {
                grupos.add(actual);
                actual = new ArrayList<>();
            }
            actual.add(mensaje);
            anterior = cuando;
        }
        if (actual.isEmpty()) {
            return new Resultado<>(grupos, List.of(), null);
        }
        Duration transcurrido = Duration.between(anterior, ahora);
        if (transcurrido.compareTo(ESPERA) >= 0) {
            grupos.add(actual);
            return new Resultado<>(grupos, List.of(), null);
        }
        return new Resultado<>(grupos, actual, ESPERA.minus(transcurrido));
    }
}
