package com.autoreel.video.infraestructura;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.autoreel.nicho.dominio.Texto;
import com.autoreel.video.dominio.ComponedorDeVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Composición con FFmpeg (secciones 7.4 y 8.3): vertical 1080×1920, 30 cuadros por segundo, H.264 con AAC y la
 * duración exacta de la plantilla (15 s, RN-31).
 * <p>
 * Cada composición trabaja en su propia carpeta temporal, con rutas relativas (sin problemas de unidades ni de
 * caracteres en Windows), y cada texto va en su archivo con {@code expansion=none}, así un precio con "%" o comillas
 * no rompe el filtro. Los textos se dibujan dentro de la zona segura de Instagram, sin tapar los botones laterales.
 */
@Component
public class FfmpegComponedor implements ComponedorDeVideo, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FfmpegComponedor.class);

    static final int ANCHO = PreparadorDeFotos.ANCHO;
    static final int ALTO = PreparadorDeFotos.ALTO;
    static final int CUADROS_POR_SEGUNDO = 30;
    /** Zona segura horizontal: los botones de Reels ocupan el borde derecho. */
    static final int MARGEN_IZQUIERDO = 90;
    static final int BORDE_DERECHO = 900;
    static final int CENTRO_X = (MARGEN_IZQUIERDO + BORDE_DERECHO) / 2;
    static final int ANCHO_UTIL = BORDE_DERECHO - MARGEN_IZQUIERDO;
    /** Borde inferior del texto de los planos: tercio inferior, sin tapar el producto ni la descripción de Instagram. */
    static final int BASE_TEXTO_PLANO = 1470;
    /** Alto de la franja oscura degradada que va bajo el texto de los planos. */
    static final int ALTO_FRANJA = 860;
    /**
     * Fundido entre planos. Los planos con foto duran lo suyo (el clip de IA no alcanza para alargarlos sin congelar el
     * último cuadro) y el cierre, que es un fondo fijo, absorbe las transiciones para que el total siga exacto (RN-31).
     */
    static final double TRANSICION = 0.3;
    /** Los textos aparecen subiendo con un fundido y se desvanecen antes de la transición. */
    static final double RETRASO_DEL_TEXTO = 0.3;
    static final double ENTRADA = 0.35;
    static final double ESCALON = 0.1;
    static final double SALIDA = 0.3;
    static final int SUBIDA = 40;
    /** Centro vertical del bloque del cierre (logo, texto principal, llamado, ubicación y WhatsApp). */
    static final int CENTRO_DEL_CIERRE = 900;
    static final int DIAMETRO_LOGO = 300;
    static final double FUNDIDO_DE_MUSICA = 1.5;
    private static final String CONTORNO =
            ":borderw=2:bordercolor=black@0.35:shadowx=0:shadowy=4:shadowcolor=black@0.55";
    /** Contorno más marcado para el texto de los planos, que va sobre el clip y no sobre un fondo oscurecido. */
    private static final String CONTORNO_DEL_PLANO =
            ":borderw=5:bordercolor=black@0.6:shadowx=0:shadowy=6:shadowcolor=black@0.75";
    /** Opacidad máxima de la franja oscura bajo el texto de los planos. */
    static final float OPACIDAD_FRANJA = 0.8f;
    /**
     * @param ruta          ejecutable de FFmpeg ({@code FFMPEG_PATH}); por defecto, el del PATH.
     * @param tiempoMaximo  tiempo máximo de una composición.
     * @param hilos         hilos de codificación, para no quitarle toda la CPU al resto del backend.
     */
    @ConfigurationProperties("autoreel.ffmpeg")
    public record PropiedadesFfmpeg(String ruta, Duration tiempoMaximo, int hilos) {

        public PropiedadesFfmpeg {
            ruta = ruta == null || ruta.isBlank() ? "ffmpeg" : ruta;
            tiempoMaximo = tiempoMaximo == null ? Duration.ofMinutes(2) : tiempoMaximo;
            hilos = hilos <= 0 ? 2 : hilos;
        }
    }

    private final PropiedadesFfmpeg propiedades;

    public FfmpegComponedor(PropiedadesFfmpeg propiedades) {
        this.propiedades = propiedades;
    }

    /** Registra la versión de FFmpeg al arrancar: las opciones de los filtros cambian entre versiones. */
    @Override
    public void run(ApplicationArguments argumentos) {
        try {
            Process proceso = new ProcessBuilder(propiedades.ruta(), "-hide_banner", "-version")
                    .redirectErrorStream(true).start();
            String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proceso.waitFor(10, TimeUnit.SECONDS);
            log.info("FFmpeg disponible: {}", salida.lines().findFirst().orElse("(sin versión)"));
        } catch (IOException e) {
            log.warn("No se encontró FFmpeg en '{}'; los videos no se podrán componer", propiedades.ruta());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public byte[] componer(Composicion composicion) {
        Path carpeta;
        try {
            carpeta = Files.createTempDirectory("autoreel-composicion-");
        } catch (IOException e) {
            throw new ErrorDeComposicion("No se pudo crear la carpeta temporal", e);
        }
        try {
            List<String> comando = preparar(carpeta, composicion);
            ejecutar(carpeta, comando);
            return Files.readAllBytes(carpeta.resolve("final.mp4"));
        } catch (IOException e) {
            throw new ErrorDeComposicion("No se pudieron preparar los archivos de la composición", e);
        } finally {
            borrar(carpeta);
        }
    }

    /** Escribe los archivos en la carpeta y arma la línea de comandos. */
    List<String> preparar(Path carpeta, Composicion composicion) throws IOException {
        Files.write(carpeta.resolve("fuente.ttf"), composicion.tipografia());
        DisenoDeTextos diseno = new DisenoDeTextos(composicion.tipografia());
        Entradas entradas = new Entradas();
        List<String> filtros = new ArrayList<>();
        List<PlanoAComponer> planos = composicion.planos().stream()
                .sorted(Comparator.comparingInt(PlanoAComponer::orden)).toList();
        CierreAComponer cierre = composicion.cierre();
        int total = cierre.segundos();

        Files.write(carpeta.resolve("franja.png"), GraficosDelVideo.franjaInferior(ANCHO, ALTO_FRANJA, OPACIDAD_FRANJA));
        StringBuilder franjas = new StringBuilder("[" + entradas.agregar("-i", "franja.png") + ":v]format=rgba,split="
                + planos.size());
        for (int i = 0; i < planos.size(); i++) {
            franjas.append("[franja").append(i).append("]");
        }
        filtros.add(franjas.toString());

        // Cada plano dura lo suyo; el siguiente entra fundiéndose sobre su último tramo.
        String ultimoArchivo = null;
        for (int i = 0; i < planos.size(); i++) {
            PlanoAComponer plano = planos.get(i);
            double largo = plano.segundos();
            String origen;
            if (plano.clip() != null) {
                ultimoArchivo = "clip" + plano.orden() + ".mp4";
                Files.write(carpeta.resolve(ultimoArchivo), plano.clip());
                origen = "[" + entradas.agregar("-i", ultimoArchivo) + ":v]" + clip(largo);
            } else {
                ultimoArchivo = "foto" + plano.orden() + ".jpg";
                Files.write(carpeta.resolve(ultimoArchivo), plano.foto());
                origen = "[" + entradas.agregar("-i", ultimoArchivo) + ":v]" + acercamiento(largo, 0.12);
            }
            filtros.add(origen + "[base" + i + "]");
            filtros.add("[base" + i + "][franja" + i + "]overlay=x=0:y=" + (ALTO - ALTO_FRANJA)
                    + textosDelPlano(carpeta, diseno, plano) + segmento() + "[v" + i + "]");
            total += plano.segundos();
        }

        // El cierre dura lo suyo más una transición por plano: así el total sigue siendo el de la plantilla.
        double largoDelCierre = cierre.segundos() + planos.size() * TRANSICION;
        filtros.addAll(cierre(carpeta, diseno, cierre, largoDelCierre, entradas, ultimoArchivo,
                "v" + planos.size()));

        // Fundidos encadenados: cada uno empieza una transición antes del final de lo que va del video.
        String anterior = "v0";
        double inicio = 0;
        for (int i = 1; i <= planos.size(); i++) {
            inicio += planos.get(i - 1).segundos() - TRANSICION;
            String salida = i == planos.size() ? "v" : "x" + i;
            filtros.add("[" + anterior + "][v" + i + "]xfade=transition=fade:duration=" + decimal(TRANSICION)
                    + ":offset=" + decimal(inicio) + (i == planos.size() ? ",format=yuv420p" : "") + "[" + salida
                    + "]");
            anterior = salida;
        }

        int musica;
        if (composicion.musica() != null) {
            Files.write(carpeta.resolve("musica.m4a"), composicion.musica());
            musica = entradas.agregar("-i", "musica.m4a");
        } else {
            musica = entradas.agregar("-f", "lavfi", "-t", String.valueOf(total), "-i", "anullsrc=r=44100:cl=stereo");
        }
        filtros.add("[" + musica + ":a]aresample=44100,apad,atrim=duration=" + total + ",asetpts=PTS-STARTPTS,"
                + "afade=t=out:st=" + decimal(total - FUNDIDO_DE_MUSICA) + ":d=" + decimal(FUNDIDO_DE_MUSICA) + "[a]");

        List<String> comando = new ArrayList<>(List.of(propiedades.ruta(), "-nostdin", "-hide_banner", "-nostats",
                "-loglevel", "error", "-y"));
        comando.addAll(entradas.argumentos);
        comando.addAll(List.of("-filter_complex", String.join(";", filtros),
                "-map", "[v]", "-map", "[a]",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
                "-r", String.valueOf(CUADROS_POR_SEGUNDO),
                "-c:a", "aac", "-b:a", "128k", "-ar", "44100",
                "-movflags", "+faststart", "-threads", String.valueOf(propiedades.hilos()),
                "-t", String.valueOf(total), "final.mp4"));
        return comando;
    }

    /** Las entradas de FFmpeg, numeradas en el orden en que se agregan. */
    private static final class Entradas {

        private final List<String> argumentos = new ArrayList<>();
        private int cantidad;

        /** @return el número de la entrada, para referirse a ella en el filtro. */
        int agregar(String... opciones) {
            argumentos.addAll(List.of(opciones));
            return cantidad++;
        }
    }

    /** El clip de IA (720p) llevado a 1080×1920 con un escalado nítido, y alargado con su último cuadro si falta. */
    private static String clip(double segundos) {
        return "scale=" + ANCHO + ":" + ALTO + ":force_original_aspect_ratio=increase:flags=lanczos,"
                + "crop=" + ANCHO + ":" + ALTO + ",setsar=1,unsharp=5:5:0.5:5:5:0,fps=" + CUADROS_POR_SEGUNDO + ","
                + "tpad=stop_mode=clone:stop_duration=" + decimal(segundos) + ","
                + "trim=duration=" + decimal(segundos) + ",setpts=PTS-STARTPTS,format=yuv420p";
    }

    /** Una imagen fija como video, con un acercamiento lento (0 = sin movimiento). */
    private static String acercamiento(double segundos, double zoom) {
        long cuadros = Math.round(segundos * CUADROS_POR_SEGUNDO);
        return "scale=" + (ANCHO * 2) + ":" + (ALTO * 2) + ",setsar=1,"
                + "zoompan=z='1+" + decimal(zoom) + "*on/" + cuadros + "':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
                + ":d=" + cuadros + ":s=" + ANCHO + "x" + ALTO + ":fps=" + CUADROS_POR_SEGUNDO
                + ",trim=duration=" + decimal(segundos) + ",setpts=PTS-STARTPTS,format=yuv420p";
    }

    /** Mismo formato y base de tiempo en todos los tramos: xfade lo exige. */
    private static String segmento() {
        return ",setsar=1,fps=" + CUADROS_POR_SEGUNDO + ",format=yuv420p";
    }

    /** Texto del plano en el tercio inferior, centrado y creciendo hacia arriba desde la base. */
    private String textosDelPlano(Path carpeta, DisenoDeTextos diseno, PlanoAComponer plano) throws IOException {
        String texto = Texto.soloDibujable(plano.texto());
        if (vacio(texto)) {
            return "";
        }
        DisenoDeTextos.Bloque bloque = diseno.repartir(texto, ANCHO_UTIL, 3, 96, 88, 80, 72, 64);
        int alto = diseno.altoDeLinea(bloque.tamano());
        int y = BASE_TEXTO_PLANO - alto * bloque.lineas().size();
        StringBuilder filtros = new StringBuilder();
        for (int i = 0; i < bloque.lineas().size(); i++) {
            String archivo = "plano" + plano.orden() + "-" + i + ".txt";
            escribirTexto(carpeta, archivo, bloque.lineas().get(i));
            filtros.append(texto(archivo, bloque.tamano(), "white", y, RETRASO_DEL_TEXTO + i * ESCALON,
                    plano.segundos() - TRANSICION)).append(CONTORNO_DEL_PLANO);
            y += alto;
        }
        return filtros.toString();
    }

    /**
     * El cierre: de fondo, la foto del local o el último cuadro del último plano desenfocado; encima, la insignia del
     * logo, el texto principal, el llamado en una píldora del color de marca y la ubicación y el WhatsApp, que
     * entran escalonados.
     *
     * @param segundos      lo que dura el tramo del cierre, con las transiciones que absorbe.
     * @param ultimoArchivo el clip o la foto del último plano, para el fondo sin foto del local.
     */
    private List<String> cierre(Path carpeta, DisenoDeTextos diseno, CierreAComponer cierre, double segundos,
            Entradas entradas, String ultimoArchivo, String salida) throws IOException {
        List<String> filtros = new ArrayList<>();
        if (cierre.fotoDelLocal() != null) {
            Files.write(carpeta.resolve("local.jpg"), cierre.fotoDelLocal());
            filtros.add("[" + entradas.agregar("-i", "local.jpg") + ":v]" + acercamiento(segundos, 0.05)
                    + ",colorchannelmixer=rr=0.55:gg=0.55:bb=0.55[fondo]");
        } else {
            // Un solo cuadro (el del final del último plano), desenfocado y oscurecido, durante todo el cierre.
            int entrada = ultimoArchivo.endsWith(".mp4") ? entradas.agregar("-sseof", "-0.3", "-i", ultimoArchivo)
                    : entradas.agregar("-i", ultimoArchivo);
            filtros.add("[" + entrada + ":v]scale=" + ANCHO + ":" + ALTO + ":force_original_aspect_ratio=increase,"
                    + "crop=" + ANCHO + ":" + ALTO + ",setsar=1,trim=end_frame=1,gblur=sigma=36,"
                    + "colorchannelmixer=rr=0.5:gg=0.5:bb=0.5,vignette,fps=" + CUADROS_POR_SEGUNDO + ","
                    + "tpad=stop_mode=clone:stop_duration=" + decimal(segundos) + ",trim=duration="
                    + decimal(segundos) + ","
                    + "setpts=PTS-STARTPTS,format=yuv420p[fondo]");
        }
        String actual = "fondo";

        // Medidas de cada pieza, para centrar el bloque entero en la pantalla.
        String principal = Texto.soloDibujable(cierre.textoPrincipal());
        DisenoDeTextos.Bloque bloquePrincipal = vacio(principal) ? null
                : diseno.repartir(principal, ANCHO_UTIL, 2, 112, 100, 88, 76);
        String llamado = Texto.soloDibujable(cierre.llamado());
        int rellenoX = 44;
        int rellenoY = 20;
        DisenoDeTextos.Bloque bloqueLlamado = vacio(llamado) ? null
                : diseno.repartir(llamado, ANCHO_UTIL - 2 * rellenoX, 2, 62, 56, 50);
        List<String> pequenas = new ArrayList<>();
        String ubicacion = Texto.soloDibujable(cierre.ubicacion());
        // "Te esperamos en Miraflores" ya dice el distrito.
        if (!vacio(ubicacion) && (llamado == null || !llamado.contains(ubicacion))) {
            pequenas.add(ubicacion);
        }
        String whatsapp = Texto.soloDibujable(cierre.whatsapp());
        if (!vacio(whatsapp)) {
            pequenas.add(whatsapp);
        }
        int tamanoPequeno = pequenas.stream()
                .mapToInt(texto -> diseno.repartir(texto, ANCHO_UTIL, 1, 50, 44).tamano()).min().orElse(50);

        int altoLogo = cierre.logo() == null ? 0 : DIAMETRO_LOGO + 56;
        int altoPrincipal = bloquePrincipal == null ? 0
                : diseno.altoDeLinea(bloquePrincipal.tamano()) * bloquePrincipal.lineas().size() + 44;
        int altoPildora = bloqueLlamado == null ? 0
                : diseno.altoDeLinea(bloqueLlamado.tamano()) * bloqueLlamado.lineas().size() + 2 * rellenoY;
        int altoPequenas = pequenas.isEmpty() ? 0 : 40 + (diseno.altoDeLinea(tamanoPequeno) + 6) * pequenas.size();
        int y = Math.max(260, CENTRO_DEL_CIERRE - (altoLogo + altoPrincipal + altoPildora + altoPequenas) / 2);

        if (cierre.logo() != null) {
            Files.write(carpeta.resolve("insignia.png"), GraficosDelVideo.insignia(cierre.logo(), DIAMETRO_LOGO));
            int lado = DIAMETRO_LOGO + 2 * GraficosDelVideo.MARGEN_DE_SOMBRA;
            int entrada = entradas.agregar("-loop", "1", "-framerate", String.valueOf(CUADROS_POR_SEGUNDO),
                    "-t", decimal(segundos), "-i", "insignia.png");
            double aparece = 0.2;
            filtros.add("[" + entrada + ":v]format=rgba,fade=t=in:st=" + decimal(aparece) + ":d=0.50:alpha=1,"
                    + "scale=w='" + lado + "*(0.8+0.2*" + avance(aparece, 0.5) + ")':h=-1:eval=frame[logo]");
            filtros.add("[" + actual + "][logo]overlay=x=" + CENTRO_X + "-w/2:y=" + (y + DIAMETRO_LOGO / 2)
                    + "-h/2[conlogo]");
            actual = "conlogo";
            y += altoLogo;
        }

        StringBuilder textos = new StringBuilder();
        int numero = 0;
        if (bloquePrincipal != null) {
            int alto = diseno.altoDeLinea(bloquePrincipal.tamano());
            for (int i = 0; i < bloquePrincipal.lineas().size(); i++) {
                String archivo = "cierre-" + numero++ + ".txt";
                escribirTexto(carpeta, archivo, bloquePrincipal.lineas().get(i));
                textos.append(texto(archivo, bloquePrincipal.tamano(), "white", y, 0.45 + i * ESCALON, 0))
                        .append(":borderw=3:bordercolor=black@0.4:shadowx=0:shadowy=6:shadowcolor=black@0.6");
                y += alto;
            }
            y += 44;
        }
        if (bloqueLlamado != null) {
            Color marca = GraficosDelVideo.color(cierre.colorDeMarca());
            int alto = diseno.altoDeLinea(bloqueLlamado.tamano());
            int ancho = bloqueLlamado.lineas().stream()
                    .mapToInt(linea -> diseno.ancho(linea, bloqueLlamado.tamano())).max().orElse(0) + 2 * rellenoX;
            Files.write(carpeta.resolve("pildora.png"), GraficosDelVideo.pildora(ancho, altoPildora, marca));
            int entrada = entradas.agregar("-loop", "1", "-framerate", String.valueOf(CUADROS_POR_SEGUNDO),
                    "-t", decimal(segundos), "-i", "pildora.png");
            double aparece = 0.75;
            filtros.add("[" + entrada + ":v]format=rgba,fade=t=in:st=" + decimal(aparece) + ":d="
                    + decimal(ENTRADA) + ":alpha=1[pildora]");
            filtros.add("[" + actual + "][pildora]overlay=x=" + CENTRO_X + "-w/2:y='" + y + "+" + SUBIDA
                    + "*pow(1-" + avance(aparece, ENTRADA) + "\\,2)'[conpildora]");
            actual = "conpildora";
            String colorDelTexto = GraficosDelVideo.colorDeTextoSobre(marca);
            int yTexto = y + rellenoY;
            for (String linea : bloqueLlamado.lineas()) {
                String archivo = "cierre-" + numero++ + ".txt";
                escribirTexto(carpeta, archivo, linea);
                textos.append(texto(archivo, bloqueLlamado.tamano(), colorDelTexto, yTexto, aparece, 0));
                yTexto += alto;
            }
            y += altoPildora;
        }
        if (!pequenas.isEmpty()) {
            y += 40;
            int alto = diseno.altoDeLinea(tamanoPequeno);
            for (int i = 0; i < pequenas.size(); i++) {
                String archivo = "cierre-" + numero++ + ".txt";
                escribirTexto(carpeta, archivo, pequenas.get(i));
                textos.append(texto(archivo, tamanoPequeno, "white@0.92", y, 0.95 + i * ESCALON, 0))
                        .append(CONTORNO);
                y += alto + 6;
            }
        }
        filtros.add("[" + actual + "]format=yuv420p" + textos + segmento() + "[" + salida + "]");
        return filtros;
    }

    /**
     * Una línea de texto animada: entra con un fundido mientras sube {@link #SUBIDA} píxeles y, si tiene fin, se
     * desvanece antes de él.
     *
     * @param y   borde superior de la línea, según las medidas de la tipografía ({@code y_align=font}).
     * @param fin segundo en que el texto ya no se ve; 0 si se queda hasta el final.
     */
    private static String texto(String archivo, int tamano, String color, int y, double inicio, double fin) {
        String aparicion = avance(inicio, ENTRADA);
        String alfa = fin > 0 ? "min(" + aparicion + "\\,clip((" + decimal(fin) + "-t)/" + decimal(SALIDA)
                + "\\,0\\,1))" : aparicion;
        return ",drawtext=fontfile=fuente.ttf:textfile=" + archivo + ":expansion=none:fontcolor=" + color
                + ":fontsize=" + tamano + ":x=" + CENTRO_X + "-text_w/2:y_align=font"
                + ":y='" + y + "+" + SUBIDA + "*pow(1-" + aparicion + "\\,2)':alpha='" + alfa + "'";
    }

    /** De 0 a 1 entre {@code inicio} y {@code inicio + duracion}, con frenado suave al final. */
    private static String avance(double inicio, double duracion) {
        return "(1-pow(1-clip((t-" + decimal(inicio) + ")/" + decimal(duracion) + "\\,0\\,1)\\,3))";
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isEmpty();
    }

    /** UTF-8 sin BOM y sin salto de línea al final: drawtext dibujaría el salto. */
    private static void escribirTexto(Path carpeta, String archivo, String texto) throws IOException {
        Files.writeString(carpeta.resolve(archivo), texto, StandardCharsets.UTF_8);
    }

    private void ejecutar(Path carpeta, List<String> comando) {
        Path registro = carpeta.resolve("ffmpeg.log");
        Process proceso;
        try {
            proceso = new ProcessBuilder(comando).directory(carpeta.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(registro.toFile())
                    .start();
        } catch (IOException e) {
            throw new ErrorDeComposicion("No se pudo ejecutar FFmpeg en '" + propiedades.ruta() + "'", e);
        }
        try {
            if (!proceso.waitFor(propiedades.tiempoMaximo().toMillis(), TimeUnit.MILLISECONDS)) {
                proceso.destroyForcibly();
                proceso.waitFor(10, TimeUnit.SECONDS);
                throw new ErrorDeComposicion("FFmpeg tardó más de " + propiedades.tiempoMaximo().toSeconds() + " s");
            }
        } catch (InterruptedException e) {
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ErrorDeComposicion("La composición se interrumpió", e);
        }
        if (proceso.exitValue() != 0) {
            throw new ErrorDeComposicion("FFmpeg terminó con error: " + ultimasLineas(registro));
        }
    }

    private static String ultimasLineas(Path registro) {
        try {
            List<String> lineas = Files.readAllLines(registro, StandardCharsets.UTF_8);
            return String.join(" | ", lineas.subList(Math.max(0, lineas.size() - 3), lineas.size()));
        } catch (IOException | UncheckedIOException e) {
            return "(sin registro)";
        }
    }

    private static void borrar(Path carpeta) {
        try (Stream<Path> archivos = Files.walk(carpeta)) {
            archivos.sorted(Comparator.reverseOrder()).forEach(archivo -> {
                try {
                    Files.deleteIfExists(archivo);
                } catch (IOException e) {
                    log.warn("No se pudo borrar {}", archivo);
                }
            });
        } catch (IOException e) {
            log.warn("No se pudo borrar la carpeta temporal {}", carpeta);
        }
    }

    private static String decimal(double valor) {
        return String.format(Locale.ROOT, "%.2f", valor);
    }
}
