package com.autoreel.video.infraestructura;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Las piezas gráficas que FFmpeg superpone al video, dibujadas con Java2D (drawbox no hace bordes redondeados ni
 * degradados): la franja oscura bajo los textos, la insignia redonda del logo y la píldora del llamado.
 */
final class GraficosDelVideo {

    static final Color COLOR_DE_MARCA_POR_DEFECTO = new Color(0xBE123C);
    /** Margen alrededor de la insignia para su sombra. */
    static final int MARGEN_DE_SOMBRA = 28;

    private GraficosDelVideo() {
    }

    /** Degradado de transparente a negro, para que el texto del tercio inferior se lea sobre cualquier foto. */
    static byte[] franjaInferior(int ancho, int alto, float opacidadMaxima) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lienzo = imagen.createGraphics();
        try {
            for (int y = 0; y < alto; y++) {
                double avance = (double) y / (alto - 1);
                // Sube rápido al principio para que ya oscurezca donde va el texto, a media franja.
                int alfa = (int) Math.round(255 * opacidadMaxima * Math.pow(avance, 0.8));
                lienzo.setColor(new Color(0, 0, 0, alfa));
                lienzo.fillRect(0, y, ancho, 1);
            }
        } finally {
            lienzo.dispose();
        }
        return png(imagen);
    }

    /**
     * El logo dentro de un círculo blanco con sombra suave. Un logo cuadrado y opaco de color llena el círculo; los
     * demás (transparentes, con fondo blanco o alargados) caben enteros dentro.
     *
     * @return un PNG de {@code diametro + 2 * MARGEN_DE_SOMBRA} de lado.
     */
    static byte[] insignia(byte[] logo, int diametro) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(logo));
        if (original == null) {
            throw new IOException("El logo no es una imagen válida");
        }
        BufferedImage circulo = new BufferedImage(diametro, diametro, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lienzo = circulo.createGraphics();
        try {
            calidad(lienzo);
            lienzo.setColor(Color.WHITE);
            lienzo.fill(new Ellipse2D.Double(0, 0, diametro, diametro));
            // Solo pinta donde ya hay círculo: el borde queda suavizado.
            lienzo.setComposite(AlphaComposite.SrcAtop);
            double proporcion = (double) original.getWidth() / original.getHeight();
            boolean llena = proporcion > 0.8 && proporcion < 1.25 && opacoDeColor(original);
            double escala = llena
                    ? Math.max((double) diametro / original.getWidth(), (double) diametro / original.getHeight())
                    : Math.min(0.76 * diametro / original.getWidth(), 0.76 * diametro / original.getHeight());
            int ancho = (int) Math.round(original.getWidth() * escala);
            int alto = (int) Math.round(original.getHeight() * escala);
            lienzo.drawImage(original, (diametro - ancho) / 2, (diametro - alto) / 2, ancho, alto, null);
        } finally {
            lienzo.dispose();
        }

        int lado = diametro + 2 * MARGEN_DE_SOMBRA;
        BufferedImage salida = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D conSombra = salida.createGraphics();
        try {
            calidad(conSombra);
            for (int paso = MARGEN_DE_SOMBRA; paso > 0; paso -= 2) {
                conSombra.setColor(new Color(0, 0, 0, 7));
                conSombra.fill(new Ellipse2D.Double(MARGEN_DE_SOMBRA - paso, MARGEN_DE_SOMBRA - paso + 8,
                        diametro + 2.0 * paso, diametro + 2.0 * paso));
            }
            conSombra.drawImage(circulo, MARGEN_DE_SOMBRA, MARGEN_DE_SOMBRA, null);
        } finally {
            conSombra.dispose();
        }
        return png(salida);
    }

    /** Rectángulo de bordes redondeados del color de marca, detrás del llamado. */
    static byte[] pildora(int ancho, int alto, Color color) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lienzo = imagen.createGraphics();
        try {
            calidad(lienzo);
            lienzo.setColor(color);
            double radio = Math.min(alto, 110);
            lienzo.fill(new RoundRectangle2D.Double(0, 0, ancho, alto, radio, radio));
        } finally {
            lienzo.dispose();
        }
        return png(imagen);
    }

    /** Color #rrggbb; el de por defecto si falta o no es válido. */
    static Color color(String hexadecimal) {
        if (hexadecimal == null || !hexadecimal.matches("#[0-9a-fA-F]{6}")) {
            return COLOR_DE_MARCA_POR_DEFECTO;
        }
        return new Color(Integer.parseInt(hexadecimal.substring(1), 16));
    }

    /** Texto oscuro sobre colores claros (amarillos, pasteles) y blanco sobre los demás. */
    static String colorDeTextoSobre(Color fondo) {
        double luminancia = (0.299 * fondo.getRed() + 0.587 * fondo.getGreen() + 0.114 * fondo.getBlue()) / 255;
        return luminancia > 0.62 ? "0x2B1B17" : "white";
    }

    static String hexadecimal(Color color) {
        return String.format("0x%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** Las cuatro esquinas opacas y no blancas: un logo cuadrado con fondo de color. */
    private static boolean opacoDeColor(BufferedImage imagen) {
        int[][] esquinas = {{0, 0}, {imagen.getWidth() - 1, 0}, {0, imagen.getHeight() - 1},
                {imagen.getWidth() - 1, imagen.getHeight() - 1}};
        for (int[] esquina : esquinas) {
            Color pixel = new Color(imagen.getRGB(esquina[0], esquina[1]), true);
            boolean blanco = pixel.getRed() > 235 && pixel.getGreen() > 235 && pixel.getBlue() > 235;
            if (pixel.getAlpha() < 250 || blanco) {
                return false;
            }
        }
        return true;
    }

    private static void calidad(Graphics2D lienzo) {
        lienzo.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        lienzo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        lienzo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static byte[] png(BufferedImage imagen) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo dibujar la imagen", e);
        }
    }
}
