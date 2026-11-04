package com.autoreel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Reloj de pruebas que avanza con el tiempo real, más un desplazamiento que cada prueba puede ajustar
 * para simular el paso de horas o días.
 */
public class RelojAjustable extends Clock {

    private final ZoneId zona;
    private volatile Duration desplazamiento = Duration.ZERO;

    public RelojAjustable(ZoneId zona) {
        this.zona = zona;
    }

    public void avanzar(Duration duracion) {
        desplazamiento = desplazamiento.plus(duracion);
    }

    public void reiniciar() {
        desplazamiento = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId otraZona) {
        RelojAjustable reloj = new RelojAjustable(otraZona);
        reloj.desplazamiento = desplazamiento;
        return reloj;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(desplazamiento);
    }
}
