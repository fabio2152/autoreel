package com.autoreel.nicho.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Las tomas de cámara del nicho (sección 7.5, guion flexible del 2026-11-25). La IA escribe la toma de cada plano con
 * libertad, guiada por el nivel de creatividad que elige; los movimientos del catálogo le sirven de inspiración y son
 * los que usan los videos anteriores. El sistema envuelve la toma con la plantilla y las prohibiciones del nicho.
 *
 * @param plantillaDeToma envoltorio en inglés común a todos los clips; {@value #MARCA_INSTRUCCION} se reemplaza por la
 *                        toma.
 * @param prohibiciones   lo que nunca debe aparecer, en frases; va al final de cada prompt.
 * @param negativos       lo mismo como lista de sustantivos, para los proveedores con prompt negativo.
 * @param creatividades   los niveles de creatividad que puede elegir la IA.
 */
public record Movimientos(String plantillaDeToma, String prohibiciones, String negativos,
        List<Creatividad> creatividades, List<Movimiento> movimientos) {

    public static final String MARCA_INSTRUCCION = "{instruccion}";

    /**
     * @param descripcion    el movimiento en palabras simples, para el storyboard y para el modelo de texto.
     * @param instruccion    la toma de cámara en inglés para el modelo de video.
     * @param soloTiposVideo si no está vacío, el movimiento solo se usa en esos tipos de video.
     * @param camara3d       si la cámara rodea, sube o cruza el producto (y no solo se acerca, se aleja o se desliza).
     */
    public record Movimiento(String id, String descripcion, String instruccion, List<String> soloTiposVideo,
            Boolean camara3d) {

        public Movimiento {
            soloTiposVideo = soloTiposVideo == null ? List.of() : List.copyOf(soloTiposVideo);
            camara3d = Boolean.TRUE.equals(camara3d);
        }

        public boolean admiteTipoVideo(String tipoVideo) {
            return soloTiposVideo.isEmpty() || soloTiposVideo.contains(tipoVideo);
        }
    }

    /**
     * Cuánto se aleja la toma de la foto.
     *
     * @param descripcion        en español, para la IA y el storyboard.
     * @param guia               en inglés: cómo escribir la toma con este nivel.
     * @param prohibicionesExtra se suman a las prohibiciones generales en el prompt del clip.
     * @param negativosExtra     se suman a los negativos generales.
     */
    public record Creatividad(String id, String nombre, String descripcion, String guia, String prohibicionesExtra,
            String negativosExtra) {
    }

    public Movimientos {
        creatividades = creatividades == null ? List.of() : List.copyOf(creatividades);
        movimientos = movimientos == null ? List.of() : List.copyOf(movimientos);
    }

    public Optional<Movimiento> buscar(String id) {
        return movimientos.stream().filter(movimiento -> movimiento.id().equals(id)).findFirst();
    }

    public Optional<Creatividad> creatividad(String id) {
        return creatividades.stream().filter(creatividad -> creatividad.id().equals(id)).findFirst();
    }

    /** Movimientos que el modelo puede tomar como inspiración en un tipo de video. */
    public List<Movimiento> para(String tipoVideo) {
        return movimientos.stream().filter(movimiento -> movimiento.admiteTipoVideo(tipoVideo)).toList();
    }

    /**
     * El prompt completo de un clip: la plantilla con la toma, las prohibiciones generales y las del nivel de
     * creatividad (si el video tiene uno).
     */
    public String promptDeToma(String toma, String creatividad) {
        String extra = creatividad(creatividad).map(Creatividad::prohibicionesExtra)
                .filter(texto -> texto != null && !texto.isBlank()).map(texto -> " " + texto.strip()).orElse("");
        return plantillaDeToma.replace(MARCA_INSTRUCCION, toma.strip()).strip() + " " + prohibiciones.strip() + extra;
    }

    /** Los negativos generales y los del nivel de creatividad. */
    public String negativos(String creatividad) {
        return negativos + creatividad(creatividad).map(Creatividad::negativosExtra)
                .filter(texto -> texto != null && !texto.isBlank()).map(texto -> ", " + texto.strip()).orElse("");
    }
}
