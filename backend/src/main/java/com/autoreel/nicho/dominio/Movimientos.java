package com.autoreel.nicho.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Lista cerrada de movimientos de cámara (sección 7.5): el modelo de texto solo puede elegir entre ellos y el
 * sistema arma la instrucción para el modelo de video con una plantilla fija (fase 5).
 *
 * @param prohibiciones se agregan a cada instrucción enviada al modelo de video.
 */
public record Movimientos(List<Movimiento> movimientos, String prohibiciones) {

    /**
     * @param descripcion    el movimiento en palabras simples, para el storyboard.
     * @param instruccion    plantilla en inglés para el modelo de video.
     * @param soloTiposVideo si no está vacío, el movimiento solo se usa en esos tipos de video.
     */
    public record Movimiento(String id, String descripcion, String instruccion, List<String> soloTiposVideo) {

        public Movimiento {
            soloTiposVideo = soloTiposVideo == null ? List.of() : List.copyOf(soloTiposVideo);
        }

        public boolean admiteTipoVideo(String tipoVideo) {
            return soloTiposVideo.isEmpty() || soloTiposVideo.contains(tipoVideo);
        }
    }

    public Movimientos {
        movimientos = List.copyOf(movimientos);
    }

    public Optional<Movimiento> buscar(String id) {
        return movimientos.stream().filter(movimiento -> movimiento.id().equals(id)).findFirst();
    }

    /** Movimientos que el modelo puede elegir para un tipo de video. */
    public List<Movimiento> para(String tipoVideo) {
        return movimientos.stream().filter(movimiento -> movimiento.admiteTipoVideo(tipoVideo)).toList();
    }
}
