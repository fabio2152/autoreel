package com.autoreel.video.infraestructura;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import org.springframework.stereotype.Component;

/**
 * Deja una foto lista para el video: aplica la orientación de la cámara (EXIF), la recorta al centro en vertical
 * 9:16 y la guarda como JPEG de 1080×1920 sin metadatos, antes de enviarla al modelo de video. Así el proveedor recibe
 * siempre la foto bien orientada y sin datos de la cámara.
 */
@Component
public class PreparadorDeFotos {

    public static final int ANCHO = 1080;
    public static final int ALTO = 1920;
    /** Lado mayor a partir del cual se lee la foto submuestreada, para no decodificar fotos enormes enteras. */
    private static final int LADO_MAXIMO_DE_LECTURA = 4320;

    public byte[] vertical(byte[] foto) {
        BufferedImage imagen = orientar(leer(foto), orientacion(foto));
        BufferedImage salida = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = salida.createGraphics();
        try {
            lienzo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            lienzo.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // Cubrir: escalar hasta llenar 9:16 y recortar al centro lo que sobra.
            double escala = Math.max((double) ANCHO / imagen.getWidth(), (double) ALTO / imagen.getHeight());
            int ancho = (int) Math.ceil(imagen.getWidth() * escala);
            int alto = (int) Math.ceil(imagen.getHeight() * escala);
            lienzo.drawImage(imagen, (ANCHO - ancho) / 2, (ALTO - alto) / 2, ancho, alto, null);
        } finally {
            lienzo.dispose();
        }
        return jpeg(salida);
    }

    /** Fondo del cierre sin foto del local: degradado vertical con los dos colores de marca (#rrggbb). */
    public byte[] degradado(String colorPrimario, String colorSecundario) {
        BufferedImage salida = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = salida.createGraphics();
        try {
            lienzo.setPaint(new GradientPaint(0, 0, color(colorPrimario, new Color(0xBE123C)), 0, ALTO,
                    color(colorSecundario, new Color(0xFDE68A))));
            lienzo.fillRect(0, 0, ANCHO, ALTO);
        } finally {
            lienzo.dispose();
        }
        return jpeg(salida);
    }

    private static BufferedImage leer(byte[] foto) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(foto))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new IllegalArgumentException("La foto no tiene un formato de imagen conocido");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                int ladoMayor = Math.max(lector.getWidth(0), lector.getHeight(0));
                ImageReadParam parametros = lector.getDefaultReadParam();
                int ladoMenor = Math.min(lector.getWidth(0), lector.getHeight(0));
                // Reducir sin que el lado menor quede por debajo del alto del video (habría que ampliarla después).
                int paso = Math.max(1, Math.min((int) Math.ceil((double) ladoMayor / LADO_MAXIMO_DE_LECTURA),
                        ladoMenor / ALTO));
                if (paso > 1) {
                    parametros.setSourceSubsampling(paso, paso, 0, 0);
                }
                BufferedImage leida = lector.read(0, parametros);
                // Sin transparencia: un PNG con fondo transparente queda sobre blanco.
                BufferedImage rgb = new BufferedImage(leida.getWidth(), leida.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D lienzo = rgb.createGraphics();
                lienzo.setColor(Color.WHITE);
                lienzo.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                lienzo.drawImage(leida, 0, 0, null);
                lienzo.dispose();
                return rgb;
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la foto", e);
        }
    }

    /** Orientación EXIF (1 a 8) de una JPEG; 1 si no tiene. */
    static int orientacion(byte[] foto) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(foto))) {
            List<JPEGSegment> segmentos = JPEGSegmentUtil.readSegments(entrada, JPEG.APP1, "Exif");
            if (segmentos.isEmpty()) {
                return 1;
            }
            InputStream datos = segmentos.getFirst().data();
            datos.read(); // relleno que sigue a "Exif\0"
            try (ImageInputStream exif = ImageIO.createImageInputStream(datos)) {
                Directory directorio = new TIFFReader().read(exif);
                Entry orientacion = directorio.getEntryById(TIFF.TAG_ORIENTATION);
                return orientacion != null && orientacion.getValue() instanceof Number valor
                        && valor.intValue() >= 1 && valor.intValue() <= 8 ? valor.intValue() : 1;
            }
        } catch (IOException | RuntimeException e) {
            return 1;
        }
    }

    /** Aplica la orientación EXIF: cada valor es una matriz (x, y) → (x', y') sobre la imagen guardada. */
    static BufferedImage orientar(BufferedImage imagen, int orientacion) {
        if (orientacion == 1) {
            return imagen;
        }
        int w = imagen.getWidth();
        int h = imagen.getHeight();
        AffineTransform t = switch (orientacion) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        boolean girada = orientacion >= 5;
        BufferedImage salida = new BufferedImage(girada ? h : w, girada ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = salida.createGraphics();
        try {
            lienzo.drawImage(imagen, t, null);
        } finally {
            lienzo.dispose();
        }
        return salida;
    }

    private static byte[] jpeg(BufferedImage imagen) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream destino = new MemoryCacheImageOutputStream(salida)) {
            escritor.setOutput(destino);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.9f);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto", e);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    private static Color color(String hexadecimal, Color porDefecto) {
        if (hexadecimal == null || !hexadecimal.matches("#[0-9a-fA-F]{6}")) {
            return porDefecto;
        }
        return new Color(Integer.parseInt(hexadecimal.substring(1), 16));
    }
}
