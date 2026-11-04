package com.autoreel.video.infraestructura;

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
    /** Los textos de los planos empiezan en la mitad inferior, sobre la zona de la descripción. */
    static final int Y_TEXTO_PLANO = 1100;
    static final double FUNDIDO_AL_CIERRE = 0.4;
    static final double FUNDIDO_DE_MUSICA = 1.5;

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
        List<String> entradas = new ArrayList<>();
        List<String> filtros = new ArrayList<>();
        int total = 0;
        int indice = 0;
        List<PlanoAComponer> planos = composicion.planos().stream()
                .sorted(Comparator.comparingInt(PlanoAComponer::orden)).toList();
        for (int i = 0; i < planos.size(); i++) {
            PlanoAComponer plano = planos.get(i);
            String cadena;
            if (plano.clip() != null) {
                String archivo = "clip" + plano.orden() + ".mp4";
                Files.write(carpeta.resolve(archivo), plano.clip());
                entradas.addAll(List.of("-i", archivo));
                cadena = "[" + indice + ":v]scale=" + ANCHO + ":" + ALTO + ":force_original_aspect_ratio=increase,"
                        + "crop=" + ANCHO + ":" + ALTO + ",setsar=1,fps=" + CUADROS_POR_SEGUNDO + ","
                        + "tpad=stop_mode=clone:stop_duration=" + plano.segundos() + ","
                        + "trim=duration=" + plano.segundos() + ",setpts=PTS-STARTPTS,format=yuv420p";
            } else {
                String archivo = "foto" + plano.orden() + ".jpg";
                Files.write(carpeta.resolve(archivo), plano.foto());
                entradas.addAll(List.of("-i", archivo));
                cadena = "[" + indice + ":v]" + acercamiento(plano.segundos(), 0.12);
            }
            indice++;
            cadena += textosDelPlano(carpeta, diseno, plano);
            if (i == planos.size() - 1) {
                cadena += ",fade=t=out:st=" + decimal(plano.segundos() - FUNDIDO_AL_CIERRE) + ":d="
                        + decimal(FUNDIDO_AL_CIERRE);
            }
            filtros.add(cadena + "[v" + i + "]");
            total += plano.segundos();
        }

        CierreAComponer cierre = composicion.cierre();
        Files.write(carpeta.resolve("cierre.jpg"), cierre.fondo());
        entradas.addAll(List.of("-i", "cierre.jpg"));
        String fondo = "[" + indice + ":v]" + acercamiento(cierre.segundos(), cierre.fondoEsFoto() ? 0.05 : 0)
                + (cierre.fondoEsFoto() ? ",colorchannelmixer=rr=0.6:gg=0.6:bb=0.6" : "");
        indice++;
        if (cierre.logo() != null) {
            Files.write(carpeta.resolve("logo.png"), cierre.logo());
            entradas.addAll(List.of("-i", "logo.png"));
            filtros.add("[" + indice + ":v]scale=w=360:h=360:force_original_aspect_ratio=decrease,format=rgba[logo]");
            filtros.add(fondo + "[fondo]");
            fondo = "[fondo][logo]overlay=x=" + CENTRO_X + "-w/2:y=470-h/2,format=yuv420p";
            indice++;
        }
        filtros.add(fondo + textosDelCierre(carpeta, diseno, cierre) + ",fade=t=in:st=0:d="
                + decimal(FUNDIDO_AL_CIERRE) + "[v" + planos.size() + "]");
        total += cierre.segundos();

        StringBuilder union = new StringBuilder();
        for (int i = 0; i <= planos.size(); i++) {
            union.append("[v").append(i).append("]");
        }
        filtros.add(union + "concat=n=" + (planos.size() + 1) + ":v=1:a=0,format=yuv420p[v]");

        if (composicion.musica() != null) {
            Files.write(carpeta.resolve("musica.m4a"), composicion.musica());
            entradas.addAll(List.of("-i", "musica.m4a"));
        } else {
            entradas.addAll(List.of("-f", "lavfi", "-t", String.valueOf(total), "-i",
                    "anullsrc=r=44100:cl=stereo"));
        }
        filtros.add("[" + indice + ":a]aresample=44100,apad,atrim=duration=" + total + ",asetpts=PTS-STARTPTS,"
                + "afade=t=out:st=" + decimal(total - FUNDIDO_DE_MUSICA) + ":d=" + decimal(FUNDIDO_DE_MUSICA) + "[a]");

        List<String> comando = new ArrayList<>(List.of(propiedades.ruta(), "-nostdin", "-hide_banner", "-nostats",
                "-loglevel", "error", "-y"));
        comando.addAll(entradas);
        comando.addAll(List.of("-filter_complex", String.join(";", filtros),
                "-map", "[v]", "-map", "[a]",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-r", String.valueOf(CUADROS_POR_SEGUNDO),
                "-c:a", "aac", "-b:a", "128k", "-ar", "44100",
                "-movflags", "+faststart", "-threads", String.valueOf(propiedades.hilos()),
                "-t", String.valueOf(total), "final.mp4"));
        return comando;
    }

    /** Una imagen fija como video, con un acercamiento lento (0 = sin movimiento). */
    private static String acercamiento(int segundos, double zoom) {
        int cuadros = segundos * CUADROS_POR_SEGUNDO;
        return "scale=" + (ANCHO * 2) + ":" + (ALTO * 2) + ",setsar=1,"
                + "zoompan=z='1+" + decimal(zoom) + "*on/" + cuadros + "':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
                + ":d=" + cuadros + ":s=" + ANCHO + "x" + ALTO + ":fps=" + CUADROS_POR_SEGUNDO
                + ",trim=duration=" + segundos + ",setpts=PTS-STARTPTS,format=yuv420p";
    }

    private String textosDelPlano(Path carpeta, DisenoDeTextos diseno, PlanoAComponer plano) throws IOException {
        String texto = Texto.soloDibujable(plano.texto());
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        DisenoDeTextos.Bloque bloque = diseno.repartir(texto, ANCHO_UTIL - 40, 3, 76, 68, 60, 52);
        StringBuilder filtros = new StringBuilder();
        int y = Y_TEXTO_PLANO;
        for (int i = 0; i < bloque.lineas().size(); i++) {
            String archivo = "plano" + plano.orden() + "-" + i + ".txt";
            escribirTexto(carpeta, archivo, bloque.lineas().get(i));
            filtros.append(",drawtext=fontfile=fuente.ttf:textfile=").append(archivo)
                    .append(":expansion=none:fontcolor=white:fontsize=").append(bloque.tamano())
                    .append(":x=").append(CENTRO_X).append("-text_w/2:y=").append(y)
                    .append(":box=1:boxcolor=black@0.45:boxborderw=18");
            y += Math.round(bloque.tamano() * 1.45f);
        }
        return filtros.toString();
    }

    private String textosDelCierre(Path carpeta, DisenoDeTextos diseno, CierreAComponer cierre) throws IOException {
        record Linea(String texto, int maximoLineas, int[] tamanos) {
        }
        List<Linea> lineas = new ArrayList<>();
        agregar(lineas, cierre.precio(), texto -> new Linea(texto, 2, new int[] {96, 84, 72, 60}));
        agregar(lineas, cierre.llamado(), texto -> new Linea(texto, 2, new int[] {68, 60, 52}));
        String ubicacion = Texto.soloDibujable(cierre.ubicacion());
        String llamado = Texto.soloDibujable(cierre.llamado());
        // "Te esperamos en Miraflores" ya dice el distrito.
        if (ubicacion != null && !ubicacion.isEmpty() && (llamado == null || !llamado.contains(ubicacion))) {
            lineas.add(new Linea(ubicacion, 1, new int[] {56, 48}));
        }
        agregar(lineas, cierre.whatsapp(), texto -> new Linea(texto, 1, new int[] {56, 48}));

        StringBuilder filtros = new StringBuilder();
        int y = 760;
        int numero = 0;
        for (Linea linea : lineas) {
            DisenoDeTextos.Bloque bloque = diseno.repartir(linea.texto(), ANCHO_UTIL, linea.maximoLineas(),
                    linea.tamanos());
            for (String texto : bloque.lineas()) {
                String archivo = "cierre-" + numero++ + ".txt";
                escribirTexto(carpeta, archivo, texto);
                filtros.append(",drawtext=fontfile=fuente.ttf:textfile=").append(archivo)
                        .append(":expansion=none:fontcolor=white:fontsize=").append(bloque.tamano())
                        .append(":x=").append(CENTRO_X).append("-text_w/2:y=").append(y)
                        .append(":borderw=3:bordercolor=black@0.6:shadowx=2:shadowy=2:shadowcolor=black@0.4");
                y += Math.round(bloque.tamano() * 1.3f);
            }
            y += 36;
        }
        return filtros.toString();
    }

    private static <T> void agregar(List<T> lineas, String texto, java.util.function.Function<String, T> crear) {
        String limpio = Texto.soloDibujable(texto);
        if (limpio != null && !limpio.isEmpty()) {
            lineas.add(crear.apply(limpio));
        }
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
