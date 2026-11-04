package com.autoreel.chat.dominio;

import java.util.Map;

import com.autoreel.nicho.dominio.GuionChat;
import com.autoreel.nicho.dominio.GuionChat.PasoChat;

/**
 * Revisa una respuesta de texto ya agrupada (sección 7.3): preguntas del usuario (RN-18), respuestas muy cortas o
 * muy largas y la regla de la promoción (RN-20). Una respuesta negativa en una pregunta opcional se guarda vacía.
 */
public final class EvaluadorDeRespuesta {

    public sealed interface Evaluacion {
    }

    /** @param valor texto que se guarda, o {@code null} si el usuario dijo que no. */
    public record Aceptada(String valor) implements Evaluacion {
    }

    /** @param mensaje mensaje fijo que se muestra antes de repetir la pregunta. */
    public record Rechazada(String mensaje) implements Evaluacion {
    }

    private EvaluadorDeRespuesta() {
    }

    public static Evaluacion evaluar(GuionChat guion, PasoChat paso, Map<String, Object> respuestas, String texto) {
        String limpio = texto.strip();
        if (limpio.contains("?") || limpio.contains("¿")) {
            return new Rechazada(guion.mensajes().pregunta());
        }
        if (limpio.length() > guion.maximoCaracteres()) {
            return new Rechazada(guion.mensajes().muyLarga());
        }
        boolean obligatoria = paso.esObligatoria(respuestas);
        if (guion.esNegativa(limpio)) {
            if (!obligatoria) {
                return new Aceptada(null);
            }
            return new Rechazada(paso.obligatoria() ? guion.mensajes().muyCorta() : guion.mensajes().precioObligatorio());
        }
        if (obligatoria && limpio.length() < guion.minimoCaracteres()) {
            return new Rechazada(guion.mensajes().muyCorta());
        }
        return new Aceptada(limpio);
    }

    /** "No, gracias" sin escribir: solo vale si la pregunta no es obligatoria en este momento. */
    public static Evaluacion omitir(GuionChat guion, PasoChat paso, Map<String, Object> respuestas) {
        if (!paso.esObligatoria(respuestas)) {
            return new Aceptada(null);
        }
        return new Rechazada(paso.obligatoria() ? guion.mensajes().muyCorta() : guion.mensajes().precioObligatorio());
    }
}
