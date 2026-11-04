package com.autoreel.video.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tareas programadas de la generación (sección 12.2): una avanza los clips (envía, consulta, respaldo) y otra
 * compone. Van separadas para que una composición larga no frene las consultas al proveedor. Cada video se reclama
 * en la base de datos, así dos ejecuciones nunca procesan el mismo.
 */
@Component
public class ProcesadorDeVideos {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorDeVideos.class);
    private static final int LOTE = 5;

    private final ServicioGeneracion generacion;
    private final ServicioComposicion composicion;

    public ProcesadorDeVideos(ServicioGeneracion generacion, ServicioComposicion composicion) {
        this.generacion = generacion;
        this.composicion = composicion;
    }

    @Scheduled(fixedDelayString = "${autoreel.tareas.clips-cada:10s}")
    public void generarClips() {
        for (Long videoId : generacion.pendientes(LOTE)) {
            try {
                generacion.procesar(videoId);
            } catch (RuntimeException e) {
                log.error("No se pudo avanzar la generación del video {}", videoId, e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${autoreel.tareas.composicion-cada:10s}")
    public void componerVideos() {
        for (Long videoId : composicion.pendientes(LOTE)) {
            try {
                composicion.procesar(videoId);
            } catch (RuntimeException e) {
                log.error("No se pudo componer el video {}", videoId, e);
            }
        }
    }
}
