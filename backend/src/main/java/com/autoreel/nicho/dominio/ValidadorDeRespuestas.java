package com.autoreel.nicho.dominio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.autoreel.compartido.error.ExcepcionDeNegocio;

/**
 * Valida las respuestas del onboarding contra las preguntas del paquete de nicho. Las respuestas se guardan como
 * JSON: texto para TEXTO y TELEFONO, lista de valores para SELECCION_MULTIPLE. Si la pregunta admite "Otros", la lista
 * puede llevar además un texto libre corto, que se guarda tal cual entre los valores.
 */
public final class ValidadorDeRespuestas {

    private static final Pattern CELULAR_PERUANO = Pattern.compile("^(?:\\+?51)?(9\\d{8})$");

    private ValidadorDeRespuestas() {
    }

    /**
     * Revisa el formato de cada respuesta y la devuelve limpia: textos sin espacios a los lados, selecciones sin
     * repetidos y teléfonos como +519XXXXXXXX. Las respuestas vacías se quitan. No exige las obligatorias.
     */
    public static Map<String, Object> normalizar(List<Pregunta> preguntas, Map<String, Object> respuestas) {
        Map<String, Object> limpias = new LinkedHashMap<>();
        if (respuestas == null) {
            return limpias;
        }
        respuestas.forEach((id, valor) -> {
            Pregunta pregunta = preguntas.stream().filter(p -> p.id().equals(id)).findFirst()
                    .orElseThrow(() -> invalida("No reconocemos la respuesta \"" + id + "\"."));
            Object limpio = normalizarValor(pregunta, valor);
            if (limpio != null) {
                limpias.put(id, limpio);
            }
        });
        return limpias;
    }

    /** Exige las preguntas obligatorias, incluidas las que dependen de otra respuesta (por ejemplo, WhatsApp). */
    public static void exigirCompletas(List<Pregunta> preguntas, Map<String, Object> respuestas) {
        for (Pregunta pregunta : preguntas) {
            if (esObligatoria(pregunta, respuestas) && !respuestas.containsKey(pregunta.id())) {
                throw new ExcepcionDeNegocio("PERFIL_INCOMPLETO", "Falta responder: " + pregunta.texto());
            }
        }
    }

    private static boolean esObligatoria(Pregunta pregunta, Map<String, Object> respuestas) {
        if (pregunta.obligatoria()) {
            return true;
        }
        Pregunta.Condicion condicion = pregunta.obligatoriaSi();
        return condicion != null && respuestas.get(condicion.pregunta()) instanceof Collection<?> valores
                && valores.contains(condicion.contiene());
    }

    private static Object normalizarValor(Pregunta pregunta, Object valor) {
        if (valor == null) {
            return null;
        }
        return switch (pregunta.tipo()) {
            case TEXTO -> texto(pregunta, valor);
            case TELEFONO -> telefono(pregunta, valor);
            case SELECCION_MULTIPLE -> seleccion(pregunta, valor);
        };
    }

    private static String texto(Pregunta pregunta, Object valor) {
        if (!(valor instanceof String texto)) {
            throw invalida("La respuesta a \"" + pregunta.texto() + "\" debe ser un texto.");
        }
        String limpio = texto.strip();
        if (pregunta.maxLongitud() != null && limpio.length() > pregunta.maxLongitud()) {
            throw invalida("La respuesta a \"" + pregunta.texto() + "\" puede tener como máximo "
                    + pregunta.maxLongitud() + " caracteres.");
        }
        return limpio.isEmpty() ? null : limpio;
    }

    private static String telefono(Pregunta pregunta, Object valor) {
        String texto = texto(pregunta, valor);
        if (texto == null) {
            return null;
        }
        var coincidencia = CELULAR_PERUANO.matcher(texto.replaceAll("[\\s-]", ""));
        if (!coincidencia.matches()) {
            throw invalida("Escribe un celular de 9 dígitos que empiece con 9.");
        }
        return "+51" + coincidencia.group(1);
    }

    private static List<String> seleccion(Pregunta pregunta, Object valor) {
        if (!(valor instanceof Collection<?> valores)) {
            throw invalida("La respuesta a \"" + pregunta.texto() + "\" debe ser una lista de opciones.");
        }
        LinkedHashSet<String> elegidos = new LinkedHashSet<>();
        boolean conOtro = false;
        for (Object elegido : valores) {
            if (!(elegido instanceof String opcion)) {
                throw invalida("Elegiste una opción que no existe en \"" + pregunta.texto() + "\".");
            }
            if (pregunta.admiteValor(opcion)) {
                elegidos.add(opcion);
                continue;
            }
            String otro = opcion.strip().replaceAll("\s+", " ");
            if (!pregunta.admiteOtro()) {
                throw invalida("Elegiste una opción que no existe en \"" + pregunta.texto() + "\".");
            }
            if (otro.isEmpty()) {
                continue;
            }
            if (conOtro) {
                throw invalida("En \"" + pregunta.texto() + "\" puedes escribir una sola opción en \"Otros\".");
            }
            if (otro.length() > pregunta.maxOtro()) {
                throw invalida("En \"" + pregunta.texto() + "\", \"Otros\" puede tener como máximo "
                        + pregunta.maxOtro() + " caracteres: escribe una característica, no una oración.");
            }
            conOtro = true;
            elegidos.add(otro);
        }
        return elegidos.isEmpty() ? null : new ArrayList<>(elegidos);
    }

    private static ExcepcionDeNegocio invalida(String mensaje) {
        return new ExcepcionDeNegocio("RESPUESTA_INVALIDA", mensaje);
    }
}
