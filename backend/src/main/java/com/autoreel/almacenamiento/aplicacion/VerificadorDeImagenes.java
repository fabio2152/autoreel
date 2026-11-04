package com.autoreel.almacenamiento.aplicacion;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import org.springframework.stereotype.Component;

/**
 * Revisa el contenido real de una imagen subida (no su extensión ni el tipo que dijo el navegador): formato,
 * medidas y, en el logo, transparencia. Lee las medidas sin decodificar la imagen entera.
 */
@Component
public class VerificadorDeImagenes {

    /** RN-11: el lado menor de las fotos debe tener al menos 1080 px. */
    public static final int LADO_MENOR_FOTO = 1080;
    /** Límites contra imágenes que pesan poco pero ocupan mucha memoria al abrirse. */
    static final int LADO_MAYOR_MAXIMO = 8000;
    static final long PIXELES_MAXIMOS = 40_000_000L;
    static final int LADO_MENOR_LOGO = 200;
    static final int LADO_MAYOR_LOGO = 4000;

    private static final Map<String, String> TIPO_POR_FORMATO =
            Map.of("png", "image/png", "jpeg", "image/jpeg", "jpg", "image/jpeg", "webp", "image/webp");

    public VerificadorDeImagenes() {
        ImageIO.setUseCache(false);
        ImageIO.scanForPlugins();
        if (!ImageIO.getImageReadersByMIMEType("image/webp").hasNext()) {
            throw new IllegalStateException("Falta el lector de imágenes WebP (TwelveMonkeys imageio-webp).");
        }
    }

    public ImagenVerificada verificar(byte[] contenido, PropositoArchivo proposito, String tipoDeclarado) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw invalido();
            }
            ImageReader lector = lectores.next();
            try {
                String tipoReal = TIPO_POR_FORMATO.get(lector.getFormatName().toLowerCase(Locale.ROOT));
                if (tipoReal == null || !tipoReal.equals(tipoDeclarado) || !proposito.admite(tipoReal)) {
                    throw proposito == PropositoArchivo.LOGO ? logoNoPng() : invalido();
                }
                if (esAnimada(contenido, tipoReal)) {
                    throw new ExcepcionDeNegocio("IMAGEN_ANIMADA", "Las imágenes animadas no se admiten. Sube una foto fija.");
                }
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                validarMedidas(ancho, alto, proposito);
                if (tipoReal.equals("image/jpeg") && girada90(contenido)) {
                    int temporal = ancho;
                    ancho = alto;
                    alto = temporal;
                }
                boolean sinTransparencia = proposito == PropositoArchivo.LOGO && !tieneTransparencia(lector);
                return new ImagenVerificada(ancho, alto, ancho > alto, sinTransparencia);
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ExcepcionDeNegocio negocio) {
                throw negocio;
            }
            throw invalido();
        }
    }

    private static void validarMedidas(int ancho, int alto, PropositoArchivo proposito) {
        int menor = Math.min(ancho, alto);
        int mayor = Math.max(ancho, alto);
        if (mayor > LADO_MAYOR_MAXIMO || (long) ancho * alto > PIXELES_MAXIMOS
                || (proposito == PropositoArchivo.LOGO && mayor > LADO_MAYOR_LOGO)) {
            throw new ExcepcionDeNegocio("IMAGEN_DEMASIADO_GRANDE",
                    "La imagen mide " + ancho + " × " + alto + " px y es demasiado grande. Usa una de hasta "
                            + (proposito == PropositoArchivo.LOGO ? LADO_MAYOR_LOGO : LADO_MAYOR_MAXIMO) + " px por lado.");
        }
        if (proposito == PropositoArchivo.LOGO && menor < LADO_MENOR_LOGO) {
            throw new ExcepcionDeNegocio("LOGO_MUY_PEQUENO",
                    "Tu logo mide " + ancho + " × " + alto + " px. Necesitamos al menos " + LADO_MENOR_LOGO
                            + " px en el lado más corto para que se vea nítido.");
        }
        if (proposito.esFoto() && menor < LADO_MENOR_FOTO) {
            throw new ExcepcionDeNegocio("FOTO_RESOLUCION_INSUFICIENTE",
                    "Tu foto mide " + ancho + " × " + alto + " px. Necesitamos al menos " + LADO_MENOR_FOTO
                            + " px en el lado más corto. Tómala con la cámara del celular, sin recortarla.");
        }
    }

    /** WebP animado (bandera de animación en el bloque VP8X) o PNG animado (bloque acTL antes de los datos). */
    private static boolean esAnimada(byte[] contenido, String tipo) {
        if (tipo.equals("image/webp")) {
            return contenido.length > 20 && new String(contenido, 12, 4, StandardCharsets.US_ASCII).equals("VP8X")
                    && (contenido[20] & 0x02) != 0;
        }
        if (tipo.equals("image/png")) {
            String inicio = new String(contenido, 0, Math.min(contenido.length, 64 * 1024), StandardCharsets.ISO_8859_1);
            int datos = inicio.indexOf("IDAT");
            int animacion = inicio.indexOf("acTL");
            return animacion >= 0 && (datos < 0 || animacion < datos);
        }
        return false;
    }

    /** Orientación EXIF 5 a 8: la cámara guardó la foto girada 90°, así que se ve con ancho y alto invertidos. */
    private static boolean girada90(byte[] contenido) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            List<JPEGSegment> segmentos = JPEGSegmentUtil.readSegments(entrada, JPEG.APP1, "Exif");
            if (segmentos.isEmpty()) {
                return false;
            }
            InputStream datos = segmentos.getFirst().data();
            datos.read(); // relleno que sigue a "Exif\0"
            try (ImageInputStream exif = ImageIO.createImageInputStream(datos)) {
                Directory directorio = new TIFFReader().read(exif);
                Entry orientacion = directorio.getEntryById(TIFF.TAG_ORIENTATION);
                return orientacion != null && orientacion.getValue() instanceof Number valor
                        && valor.intValue() >= 5 && valor.intValue() <= 8;
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean tieneTransparencia(ImageReader lector) throws IOException {
        var tipos = lector.getImageTypes(0);
        if (tipos.hasNext() && !tipos.next().getColorModel().hasAlpha()) {
            return false;
        }
        BufferedImage imagen = lector.read(0);
        if (!imagen.getColorModel().hasAlpha()) {
            return false;
        }
        var alfa = imagen.getAlphaRaster();
        int[] fila = new int[imagen.getWidth()];
        for (int y = 0; y < imagen.getHeight(); y++) {
            alfa.getSamples(0, y, imagen.getWidth(), 1, 0, fila);
            for (int valor : fila) {
                if (valor < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ExcepcionDeNegocio invalido() {
        return new ExcepcionDeNegocio("ARCHIVO_INVALIDO",
                "El archivo no es una imagen válida o no coincide con su tipo. Sube una foto JPG, PNG o WebP.");
    }

    private static ExcepcionDeNegocio logoNoPng() {
        return new ExcepcionDeNegocio("LOGO_DEBE_SER_PNG", "El logo debe ser una imagen PNG.");
    }
}
