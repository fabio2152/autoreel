package com.autoreel.video.dominio;

import java.util.List;

/**
 * Puerto de la composición del video final (secciones 7.4 y 8.3): une los planos, dibuja los textos y el cierre,
 * agrega la música y exporta el MP4 de 15 segundos. Hoy lo implementa FFmpeg.
 */
public interface ComponedorDeVideo {

    /**
     * @return el MP4 final.
     * @throws ErrorDeComposicion si no se pudo componer.
     */
    byte[] componer(Composicion composicion);

    /**
     * Todo lo que necesita la composición, ya leído del almacenamiento.
     *
     * @param musica     la pista (puede faltar: el video sale con audio en silencio).
     * @param tipografia el TTF del estilo del negocio.
     */
    record Composicion(List<PlanoAComponer> planos, CierreAComponer cierre, byte[] musica, byte[] tipografia) {
    }

    /**
     * Un plano con foto.
     *
     * @param clip  el clip de IA; vacío si el plano usa el respaldo.
     * @param foto  la foto ya preparada en vertical (la usa el respaldo).
     */
    record PlanoAComponer(int orden, int segundos, byte[] clip, byte[] foto, String texto) {
    }

    /**
     * El cierre, sin clip de IA.
     *
     * @param fotoDelLocal   la foto del local ya en vertical; si falta, el fondo es el último cuadro del último plano,
     *                       desenfocado y oscurecido.
     * @param logo           el logo (PNG o JPEG); puede faltar.
     * @param textoPrincipal lo que va en grande: el precio o, si no hay, la frase que escribió la IA.
     * @param colorDeMarca   color primario del negocio (#rrggbb), para destacar el llamado.
     */
    record CierreAComponer(int segundos, byte[] fotoDelLocal, byte[] logo, String textoPrincipal, String llamado,
            String ubicacion, String whatsapp, String colorDeMarca) {
    }

    class ErrorDeComposicion extends RuntimeException {

        public ErrorDeComposicion(String mensaje) {
            super(mensaje);
        }

        public ErrorDeComposicion(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
