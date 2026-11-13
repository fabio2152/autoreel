package com.autoreel.video.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada que escribe los guiones pendientes (sección 12.2). Es la única que los toma: cada video se
 * reclama en la base de datos, así dos ejecuciones nunca procesan el mismo. La fase 5 le suma los demás estados.
 */
@Component
public class ProcesadorDeGuiones {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorDeGuiones.class);
    private static final int LOTE = 5;

    private final ServicioGuion guiones;

    public ProcesadorDeGuiones(ServicioGuion guiones) {
        this.guiones = guiones;
    }

    @Scheduled(fixedDelayString = "${autoreel.tareas.guiones-cada:3s}")
    public void procesarPendientes() {
        for (Long videoId : guiones.pendientes(LOTE)) {
            try {
                guiones.procesar(videoId);
            } catch (RuntimeException e) {
                // El reclamo vence y el video se vuelve a intentar dentro de su plazo.
                log.error("No se pudo escribir el guion del video {}", videoId, e);
            }
        }
    }
}
