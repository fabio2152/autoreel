package com.autoreel.video.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import com.autoreel.nicho.pasteleria.PaquetePasteleria;
import com.autoreel.video.dominio.ComponedorDeVideo.CierreAComponer;
import com.autoreel.video.dominio.ComponedorDeVideo.Composicion;
import com.autoreel.video.dominio.ComponedorDeVideo.ErrorDeComposicion;
import com.autoreel.video.dominio.ComponedorDeVideo.PlanoAComponer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * V23 con FFmpeg real: el video final dura 15 segundos, es vertical de 1080×1920 a 30 cuadros, H.264 y tiene música
 * (AAC). Usa {@code FFMPEG_PATH} y {@code FFPROBE_PATH} si están definidos; si no, los del PATH.
 */
class FfmpegComponedorTest {

    static final String FFMPEG = entorno("FFMPEG_PATH", "ffmpeg");
    static final String FFPROBE = entorno("FFPROBE_PATH", "ffprobe");
    /** Textos con todo lo que podría romper el filtro drawtext. */
    static final String TEXTO_DIFICIL = "¿20% \\ off? ¡Sí: \"ñam\" y 'más'!";

    static PaquetePasteleria paquete;
    static byte[] clip;

    @TempDir
    Path carpeta;

    private final FfmpegComponedor componedor = new FfmpegComponedor(
            new FfmpegComponedor.PropiedadesFfmpeg(FFMPEG, Duration.ofMinutes(2), 2));
    private final PreparadorDeFotos fotos = new PreparadorDeFotos();

    @BeforeAll
    static void preparar(@TempDir Path temporal) throws Exception {
        paquete = new PaquetePasteleria(JsonMapper.builder().build());
        // Un clip como los de Veo: 720×1280, 24 cuadros, 4 segundos y con audio (que se descarta).
        Path archivo = temporal.resolve("clip.mp4");
        ejecutar(FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
                "testsrc2=s=720x1280:r=24:d=4", "-f", "lavfi", "-i", "sine=d=4", "-shortest",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", archivo.toString());
        clip = Files.readAllBytes(archivo);
    }

    @Test
    void v23ConClipsRespaldoLogoYMusica() throws Exception {
        Composicion composicion = new Composicion(List.of(
                new PlanoAComponer(1, 3, clip, null, "Un antojo de fresas frescas"),
                new PlanoAComponer(2, 4, clip, null, TEXTO_DIFICIL),
                new PlanoAComponer(3, 4, null, fotos.vertical(foto(1600, 1200)),
                        "Cada porción con fresas de Huaral y crema batida")),
                new CierreAComponer(4, fotos.vertical(foto(1920, 1080)), true, logo(), "S/ 65 la torta entera",
                        "Te esperamos en San Isidro", "San Isidro", "+51 987 654 321"),
                paquete.leerPista(paquete.pistas().getFirst()), paquete.tipografia("CASERO"));

        JsonNode info = sondear(componedor.componer(composicion));

        assertThat(info.at("/format/duration").asDouble()).isCloseTo(15.0, within(0.1));
        JsonNode video = flujo(info, "video");
        assertThat(video.path("codec_name").asString()).isEqualTo("h264");
        assertThat(video.path("width").asInt()).isEqualTo(1080);
        assertThat(video.path("height").asInt()).isEqualTo(1920);
        assertThat(video.path("r_frame_rate").asString()).isEqualTo("30/1");
        assertThat(flujo(info, "audio").path("codec_name").asString()).isEqualTo("aac");
    }

    @Test
    void todoConRespaldoSinMusicaSinLogoYConDegradado() throws Exception {
        byte[] foto = fotos.vertical(foto(1080, 1920));
        Composicion composicion = new Composicion(List.of(
                new PlanoAComponer(1, 3, null, foto, "Recién salido del horno"),
                new PlanoAComponer(2, 4, null, foto, "Alfajores de Maicena"),
                new PlanoAComponer(3, 4, null, foto, "Rellenos de manjar")),
                new CierreAComponer(4, fotos.degradado("#be123c", "#fde68a"), false, null, null,
                        "Pide por WhatsApp", "Miraflores", null),
                null, paquete.tipografia("ELEGANTE"));

        JsonNode info = sondear(componedor.componer(composicion));

        assertThat(info.at("/format/duration").asDouble()).isCloseTo(15.0, within(0.1));
        assertThat(flujo(info, "audio").path("codec_name").asString()).isEqualTo("aac");
    }

    @Test
    void unFfmpegInexistenteEsUnErrorDeComposicion() {
        FfmpegComponedor sinFfmpeg = new FfmpegComponedor(
                new FfmpegComponedor.PropiedadesFfmpeg("no-existe-ffmpeg", Duration.ofSeconds(5), 1));
        byte[] foto = fotos.vertical(foto(1080, 1920));
        Composicion composicion = new Composicion(List.of(new PlanoAComponer(1, 3, null, foto, "Hola")),
                new CierreAComponer(4, foto, true, null, null, null, null, null), null, paquete.tipografia("CASERO"));

        assertThatThrownBy(() -> sinFfmpeg.componer(composicion)).isInstanceOf(ErrorDeComposicion.class);
    }

    @Test
    void laFotoGiradaPorLaCamaraQuedaDerecha() {
        BufferedImage horizontal = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage girada = PreparadorDeFotos.orientar(horizontal, 6);
        assertThat(girada.getWidth()).isEqualTo(300);
        assertThat(girada.getHeight()).isEqualTo(400);
        assertThat(PreparadorDeFotos.orientar(horizontal, 3).getWidth()).isEqualTo(400);
    }

    private JsonNode sondear(byte[] mp4) throws Exception {
        Path archivo = carpeta.resolve("final.mp4");
        Files.write(archivo, mp4);
        String salida = ejecutar(FFPROBE, "-v", "error", "-show_format", "-show_streams", "-of", "json",
                archivo.toString());
        return JsonMapper.builder().build().readTree(salida);
    }

    private static JsonNode flujo(JsonNode info, String tipo) {
        for (JsonNode flujo : info.path("streams")) {
            if (flujo.path("codec_type").asString().equals(tipo)) {
                return flujo;
            }
        }
        throw new AssertionError("El video no tiene flujo de " + tipo);
    }

    private static byte[] foto(int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = imagen.createGraphics();
        lienzo.setColor(new Color(0xF4A261));
        lienzo.fillRect(0, 0, ancho, alto);
        lienzo.setColor(new Color(0x9D0208));
        lienzo.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
        lienzo.dispose();
        return codificar(imagen, "jpg");
    }

    private static byte[] logo() {
        BufferedImage imagen = new BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D lienzo = imagen.createGraphics();
        lienzo.setColor(new Color(0xBE123C));
        lienzo.fillRoundRect(0, 0, 500, 300, 80, 80);
        lienzo.dispose();
        return codificar(imagen, "png");
    }

    private static byte[] codificar(BufferedImage imagen, String formato) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, formato, salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String ejecutar(String... comando) throws Exception {
        Process proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
        String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(proceso.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(proceso.exitValue()).as(salida).isZero();
        return salida;
    }

    private static String entorno(String variable, String porDefecto) {
        String valor = System.getenv(variable);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}
