package com.autoreel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Imágenes generadas para las pruebas.
 */
public final class Imagenes {

    private Imagenes() {
    }

    public static byte[] jpg(int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        pintar(imagen, new Color(0xF472B6));
        return codificar(imagen, "jpg");
    }

    public static byte[] png(int ancho, int alto, boolean conTransparencia) {
        BufferedImage imagen = new BufferedImage(ancho, alto,
                conTransparencia ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        if (conTransparencia) {
            Graphics2D g = imagen.createGraphics();
            g.setColor(new Color(0xBE123C));
            g.fillOval(0, 0, ancho, alto);
            g.dispose();
        } else {
            pintar(imagen, new Color(0xBE123C));
        }
        return codificar(imagen, "png");
    }

    /** PNG con canal alfa pero sin ningún píxel transparente. */
    public static byte[] pngRgbaOpaco(int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        pintar(imagen, new Color(0xBE123C));
        return codificar(imagen, "png");
    }

    /**
     * JPEG con la etiqueta EXIF de orientación: la cámara guardó los píxeles de lado y pide girarlos al mostrarlos.
     */
    public static byte[] jpgConOrientacion(int ancho, int alto, int orientacion) {
        byte[] jpg = jpg(ancho, alto);
        byte[] exif = {
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8,          // cabecera TIFF (big endian), primer IFD en el byte 8
                0, 1,                                  // una entrada
                0x01, 0x12, 0, 3, 0, 0, 0, 1,          // etiqueta 274 (Orientation), tipo SHORT, 1 valor
                0, (byte) orientacion, 0, 0,           // valor
                0, 0, 0, 0                             // sin más IFD
        };
        int largo = exif.length + 2;
        byte[] resultado = new byte[jpg.length + exif.length + 4];
        resultado[0] = jpg[0];
        resultado[1] = jpg[1];
        resultado[2] = (byte) 0xFF;
        resultado[3] = (byte) 0xE1;
        resultado[4] = (byte) (largo >> 8);
        resultado[5] = (byte) largo;
        System.arraycopy(exif, 0, resultado, 6, exif.length);
        System.arraycopy(jpg, 2, resultado, 6 + exif.length, jpg.length - 2);
        return resultado;
    }

    private static void pintar(BufferedImage imagen, Color color) {
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
        g.dispose();
    }

    private static byte[] codificar(BufferedImage imagen, String formato) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, formato, salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
