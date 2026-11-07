package com.autoreel.almacenamiento.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoreel.Imagenes;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import org.junit.jupiter.api.Test;

class VerificadorDeImagenesTest {

    private final VerificadorDeImagenes verificador = new VerificadorDeImagenes();

    @Test
    void aceptaUnaFotoVerticalDe1080() {
        ImagenVerificada imagen = verificador.verificar(Imagenes.jpg(1080, 1920), PropositoArchivo.FOTO_PRODUCTO,
                "image/jpeg");

        assertThat(imagen.ancho()).isEqualTo(1080);
        assertThat(imagen.alto()).isEqualTo(1920);
        assertThat(imagen.horizontal()).isFalse();
    }

    /** V12 con el mínimo de 720 px en el lado menor (decisión del usuario, 2026-11-06). */
    @Test
    void v12RechazaUnaFotoDeMenosDe720PixelesEnSuLadoMenor() {
        assertThatThrownBy(() -> verificador.verificar(Imagenes.jpg(719, 1280), PropositoArchivo.FOTO_PRODUCTO,
                "image/jpeg"))
                .extracting("codigo").isEqualTo("FOTO_RESOLUCION_INSUFICIENTE");
        assertThat(verificador.verificar(Imagenes.jpg(720, 1280), PropositoArchivo.FOTO_PRODUCTO, "image/jpeg")
                .ancho()).isEqualTo(720);
    }

    @Test
    void aceptaUnaFotoHorizontalConAviso() {
        ImagenVerificada imagen = verificador.verificar(Imagenes.png(1920, 1080, false),
                PropositoArchivo.FOTO_PRODUCTO, "image/png");

        assertThat(imagen.horizontal()).isTrue();
    }

    @Test
    void aplicaLaOrientacionDeLaCamara() {
        // Píxeles guardados de lado (1920 × 1080) que la cámara pide girar 90°: la foto se ve vertical.
        ImagenVerificada imagen = verificador.verificar(Imagenes.jpgConOrientacion(1920, 1080, 6),
                PropositoArchivo.FOTO_PRODUCTO, "image/jpeg");

        assertThat(imagen.ancho()).isEqualTo(1080);
        assertThat(imagen.alto()).isEqualTo(1920);
        assertThat(imagen.horizontal()).isFalse();
    }

    @Test
    void rechazaUnContenidoDistintoDelTipoDeclarado() {
        assertThatThrownBy(() -> verificador.verificar(Imagenes.png(1080, 1920, false),
                PropositoArchivo.FOTO_PRODUCTO, "image/jpeg"))
                .extracting("codigo").isEqualTo("ARCHIVO_INVALIDO");
        assertThatThrownBy(() -> verificador.verificar("no soy una imagen".getBytes(),
                PropositoArchivo.FOTO_PRODUCTO, "image/jpeg"))
                .extracting("codigo").isEqualTo("ARCHIVO_INVALIDO");
    }

    @Test
    void rechazaImagenesDemasiadoGrandes() {
        assertThatThrownBy(() -> verificador.verificar(Imagenes.png(9000, 1200, false),
                PropositoArchivo.FOTO_PRODUCTO, "image/png"))
                .extracting("codigo").isEqualTo("IMAGEN_DEMASIADO_GRANDE");
    }

    @Test
    void elLogoDebeSerPng() {
        assertThatThrownBy(() -> verificador.verificar(Imagenes.jpg(500, 500), PropositoArchivo.LOGO, "image/jpeg"))
                .extracting("codigo").isEqualTo("LOGO_DEBE_SER_PNG");
    }

    @Test
    void marcaElLogoSinTransparencia() {
        assertThat(verificador.verificar(Imagenes.png(500, 500, true), PropositoArchivo.LOGO, "image/png")
                .sinTransparencia()).isFalse();
        assertThat(verificador.verificar(Imagenes.png(500, 500, false), PropositoArchivo.LOGO, "image/png")
                .sinTransparencia()).isTrue();
        assertThat(verificador.verificar(Imagenes.pngRgbaOpaco(500, 500), PropositoArchivo.LOGO, "image/png")
                .sinTransparencia()).isTrue();
    }

    @Test
    void aceptaUnLogoDePaletaConTransparencia() {
        assertThat(verificador.verificar(Imagenes.pngDePaleta(500, 500, true), PropositoArchivo.LOGO, "image/png")
                .sinTransparencia()).isFalse();
        assertThat(verificador.verificar(Imagenes.pngDePaleta(500, 500, false), PropositoArchivo.LOGO, "image/png")
                .sinTransparencia()).isTrue();
    }

    @Test
    void rechazaUnPngAnimadoAunqueUnTextoDigaIdat() {
        assertThatThrownBy(() -> verificador.verificar(Imagenes.pngAnimado(1080, 1920),
                PropositoArchivo.FOTO_PRODUCTO, "image/png"))
                .extracting("codigo").isEqualTo("IMAGEN_ANIMADA");
    }

    @Test
    void rechazaUnLogoMuyPequeno() {
        assertThatThrownBy(() -> verificador.verificar(Imagenes.png(120, 120, true), PropositoArchivo.LOGO,
                "image/png"))
                .extracting("codigo").isEqualTo("LOGO_MUY_PEQUENO");
    }
}
