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

    /** PNG de paleta (como los que dejan TinyPNG o pngquant), con o sin un color transparente. */
    public static byte[] pngDePaleta(int ancho, int alto, boolean conTransparencia) {
        byte[] rojos = {(byte) 0xBE, (byte) 0xFF};
        byte[] verdes = {0x12, (byte) 0xFF};
        byte[] azules = {0x3C, (byte) 0xFF};
        byte[] alfas = {(byte) 0xFF, (byte) (conTransparencia ? 0 : 0xFF)};
        var paleta = new java.awt.image.IndexColorModel(1, 2, rojos, verdes, azules, alfas);
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_BYTE_BINARY, paleta);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ancho, alto);
        g.setColor(new Color(0xBE123C));
        g.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
        g.dispose();
        return codificar(imagen, "png");
    }

    /**
     * PNG animado (APNG): agrega un bloque acTL antes de los datos, precedido de un bloque de texto que contiene la
     * palabra "IDAT" para confundir a una búsqueda ingenua.
     */
    public static byte[] pngAnimado(int ancho, int alto) {
        byte[] png = png(ancho, alto, false);
        int finIhdr = 8 + 25;
        byte[] texto = bloque("tEXt", "Comment\0IDAT".getBytes());
        byte[] animacion = bloque("acTL", new byte[] {0, 0, 0, 1, 0, 0, 0, 0});
        byte[] resultado = new byte[png.length + texto.length + animacion.length];
        System.arraycopy(png, 0, resultado, 0, finIhdr);
        System.arraycopy(texto, 0, resultado, finIhdr, texto.length);
        System.arraycopy(animacion, 0, resultado, finIhdr + texto.length, animacion.length);
        System.arraycopy(png, finIhdr, resultado, finIhdr + texto.length + animacion.length, png.length - finIhdr);
        return resultado;
    }

    private static byte[] bloque(String tipo, byte[] datos) {
        var crc = new java.util.zip.CRC32();
        crc.update(tipo.getBytes());
        crc.update(datos);
        var salida = java.nio.ByteBuffer.allocate(12 + datos.length);
        salida.putInt(datos.length).put(tipo.getBytes()).put(datos).putInt((int) crc.getValue());
        return salida.array();
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
