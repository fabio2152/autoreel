package com.autoreel.nicho.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Lista cerrada de movimientos de cámara (sección 7.5): el modelo de texto solo puede elegir entre ellos y el
 * sistema arma la instrucción para el modelo de video con la plantilla de toma del nicho (fase 5).
 *
 * @param plantillaDeToma envoltorio en inglés común a todos los clips; {@value #MARCA_INSTRUCCION} se reemplaza por la
 *                        instrucción del movimiento.
 * @param prohibiciones   lo que nunca debe aparecer, en frases; va al final de cada prompt.
 * @param negativos       lo mismo como lista de sustantivos, para los proveedores con prompt negativo.
 * @param minimoPlanosCamara3d cuántos planos de un video, como mínimo, usan un movimiento de cámara 3D (órbita,
 *                        arco, grúa, paralaje o diagonal), para que el video no sea solo acercamientos.
 */
public record Movimientos(String plantillaDeToma, String prohibiciones, String negativos, Integer minimoPlanosCamara3d,
        List<Movimiento> movimientos) {

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

    public Movimientos {
        minimoPlanosCamara3d = minimoPlanosCamara3d == null ? 0 : minimoPlanosCamara3d;
        movimientos = movimientos == null ? List.of() : List.copyOf(movimientos);
    }

    /** Si el movimiento existe y es de cámara 3D. */
    public boolean esCamara3d(String id) {
        return buscar(id).map(Movimiento::camara3d).orElse(false);
    }

    public Optional<Movimiento> buscar(String id) {
        return movimientos.stream().filter(movimiento -> movimiento.id().equals(id)).findFirst();
    }

    /** Movimientos que el modelo puede elegir para un tipo de video. */
    public List<Movimiento> para(String tipoVideo) {
        return movimientos.stream().filter(movimiento -> movimiento.admiteTipoVideo(tipoVideo)).toList();
    }

    /** El prompt completo de un clip: la plantilla de toma con la instrucción del movimiento, y las prohibiciones. */
    public String promptDeToma(Movimiento movimiento) {
        return plantillaDeToma.replace(MARCA_INSTRUCCION, movimiento.instruccion()).strip() + " " + prohibiciones.strip();
    }
}
