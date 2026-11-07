package com.autoreel.facturacion.aplicacion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea diaria de la sección 12.2: a las 00:10 de Lima calcula los estados de pago y crea las notificaciones. También
 * corre al arrancar, para no perder un día si el servidor estuvo apagado a esa hora (no duplica nada). Las pruebas la
 * apagan y llaman a {@link ServicioNotificaciones#generarDePagos()} directamente.
 */
@Component
@ConditionalOnBooleanProperty(name = "autoreel.tareas.activas", matchIfMissing = true)
public class TareaDiariaDePagos {

    private final ServicioNotificaciones notificaciones;

    public TareaDiariaDePagos(ServicioNotificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "America/Lima")
    public void ejecutar() {
        notificaciones.generarDePagos();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        notificaciones.generarDePagos();
    }
}
