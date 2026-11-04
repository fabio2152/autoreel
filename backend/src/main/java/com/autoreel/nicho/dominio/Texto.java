package com.autoreel.nicho.dominio;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Reglas de texto compartidas por el validador del guion, el guion de respaldo y la edición del storyboard.
 * El frontend replica {@link #contarPalabras} con los mismos casos de prueba.
 */
public final class Texto {

    private Texto() {
    }

    /**
     * Para comparar: minúsculas, sin tildes ni diéresis (la "ñ" se conserva) y todo lo que no sea letra o número
     * como espacio.
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("[\\u0301\\u0308]", "");
        return Normalizer.normalize(sinTildes, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }

    public static List<String> tokens(String texto) {
        String normalizado = normalizar(texto);
        return normalizado.isEmpty() ? List.of() : Arrays.asList(normalizado.split(" "));
    }

    /** Si el texto contiene la frase como secuencia de palabras completas ("sin azúcar", "el mejor"). */
    public static boolean contieneFrase(String texto, String frase) {
        List<String> palabras = tokens(texto);
        List<String> buscada = tokens(frase);
        if (buscada.isEmpty() || buscada.size() > palabras.size()) {
            return false;
        }
        for (int i = 0; i + buscada.size() <= palabras.size(); i++) {
            if (palabras.subList(i, i + buscada.size()).equals(buscada)) {
                return true;
            }
        }
        return false;
    }

    /** Palabras en pantalla: lo separado por espacios que tenga al menos una letra o un número ("S/ 65" = 2). */
    public static int contarPalabras(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(texto.strip().split("\\s+"))
                .filter(palabra -> palabra.codePoints().anyMatch(Character::isLetterOrDigit))
                .count();
    }

    /**
     * Si el texto tiene algo que las tipografías del video no pueden dibujar: emojis, saltos de línea, caracteres de
     * control o letras fuera del latín básico. Es una lista blanca: el latín de U+0020 a U+00FF (incluye á, ñ, ¿, ¡)
     * y la puntuación tipográfica que traen las tres fuentes.
     */
    public static boolean tieneCaracteresNoDibujables(String texto) {
        return texto.codePoints().anyMatch(c -> !esDibujable(c));
    }

    /** El texto sin los caracteres que no se pueden dibujar (para datos del perfil o del catálogo en el cierre). */
    public static String soloDibujable(String texto) {
        if (texto == null) {
            return null;
        }
        StringBuilder limpio = new StringBuilder();
        texto.codePoints().filter(Texto::esDibujable).forEach(limpio::appendCodePoint);
        return limpio.toString().replaceAll("\\s+", " ").strip();
    }

    private static boolean esDibujable(int c) {
        return (c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xFF) || PUNTUACION_TIPOGRAFICA.indexOf(c) >= 0;
    }

    private static final String PUNTUACION_TIPOGRAFICA = "‘’“”–—…€";
}
