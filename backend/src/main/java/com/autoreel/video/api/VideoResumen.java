package com.autoreel.video.api;

import java.time.Instant;

import com.autoreel.video.dominio.EstadoVideo;

/** Un video en "Mis videos": base del historial (RF-26, fase 5). */
public record VideoResumen(Long id, EstadoVideo estado, String tipoVideo, String producto, String foto,
        Instant fechaCreacion) {
}
