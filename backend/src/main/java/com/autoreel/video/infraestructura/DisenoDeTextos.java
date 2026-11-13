package com.autoreel.video.infraestructura;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reparte un texto en líneas que caben en el ancho disponible, midiendo con la misma tipografía que usa FFmpeg
 * (drawtext no ajusta ni recorta). Baja el tamaño hasta que entra en el máximo de líneas.
 */
final class DisenoDeTextos {

    /** @param tamano tamaño de la letra en píxeles. */
    record Bloque(List<String> lineas, int tamano) {
    }

    private final Font fuente;

    DisenoDeTextos(byte[] tipografia) {
        try {
            this.fuente = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(tipografia));
        } catch (FontFormatException | IOException e) {
            throw new IllegalArgumentException("La tipografía no es un TTF válido", e);
        }
    }

    /**
     * @param ancho        ancho máximo de una línea, en píxeles.
     * @param maximoLineas cuántas líneas como máximo.
     * @param tamanos      tamaños a probar, de mayor a menor; el último se usa aunque no alcance.
     */
    Bloque repartir(String texto, int ancho, int maximoLineas, int... tamanos) {
        List<String> palabras = Arrays.stream(texto.strip().split("\\s+")).filter(p -> !p.isEmpty()).toList();
        for (int tamano : tamanos) {
            FontMetrics medidas = medidas(tamano);
            List<String> lineas = lineas(palabras, ancho, medidas);
            boolean caben = lineas.size() <= maximoLineas
                    && lineas.stream().allMatch(linea -> medidas.stringWidth(linea) <= ancho);
            if (caben) {
                return new Bloque(lineas, tamano);
            }
        }
        int menor = tamanos[tamanos.length - 1];
        List<String> lineas = lineas(palabras, ancho, medidas(menor));
        if (lineas.size() > maximoLineas) {
            // Lo que no entra se une a la última línea: mejor un texto apretado que uno incompleto.
            List<String> recortadas = new ArrayList<>(lineas.subList(0, maximoLineas - 1));
            recortadas.add(String.join(" ", lineas.subList(maximoLineas - 1, lineas.size())));
            lineas = recortadas;
        }
        // Si aun así una línea no cabe (palabras larguísimas), se achica la letra hasta que entre.
        FontMetrics medidas = medidas(menor);
        int masAncha = lineas.stream().mapToInt(medidas::stringWidth).max().orElse(0);
        int tamano = masAncha > ancho ? Math.max(12, menor * ancho / masAncha) : menor;
        return new Bloque(lineas, tamano);
    }

    /** Ancho en píxeles de una línea con esa letra. */
    int ancho(String linea, int tamano) {
        return medidas(tamano).stringWidth(linea);
    }

    /** Alto de una línea (ascendente más descendente de la tipografía), el que usa drawtext con y_align=font. */
    int altoDeLinea(int tamano) {
        FontMetrics medidas = medidas(tamano);
        return medidas.getAscent() + medidas.getDescent();
    }

    private static List<String> lineas(List<String> palabras, int ancho, FontMetrics medidas) {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : palabras) {
            String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (actual.isEmpty() || medidas.stringWidth(candidata) <= ancho) {
                actual = new StringBuilder(candidata);
            } else {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    private FontMetrics medidas(int tamano) {
        Graphics2D lienzo = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            return lienzo.getFontMetrics(fuente.deriveFont((float) tamano));
        } finally {
            lienzo.dispose();
        }
    }
}
