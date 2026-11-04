package com.autoreel.video.aplicacion;

import java.time.Clock;

import com.autoreel.chat.dominio.ConversacionCompletada;
import com.autoreel.facturacion.aplicacion.ResolutorDePlanEfectivo;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.video.dominio.Video;
import com.autoreel.video.infraestructura.VideoRepositorio;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Crea el video en GUION_PENDIENTE en la misma transacción en la que termina el chat, con el plan y los modelos
 * vigentes (RN-54). El guion lo escribe después el {@link ProcesadorDeGuiones}, sin hacer esperar al chat.
 */
@Component
public class CreadorDeVideos {

    private final VideoRepositorio videos;
    private final ResolutorDePlanEfectivo planes;
    private final Clock reloj;

    public CreadorDeVideos(VideoRepositorio videos, ResolutorDePlanEfectivo planes, Clock reloj) {
        this.videos = videos;
        this.planes = planes;
        this.reloj = reloj;
    }

    @EventListener
    public void alCompletarse(ConversacionCompletada evento) {
        if (!(evento.respuestas().get(RespuestasDelChat.PRODUCTO) instanceof Number productoId)
                || !(evento.respuestas().get(RespuestasDelChat.TIPO_VIDEO) instanceof String tipoVideo)) {
            throw new IllegalStateException("La conversación " + evento.conversacionId()
                    + " terminó sin producto o sin tipo de video");
        }
        Plan plan = planes.de(evento.negocioId());
        videos.save(new Video(evento.negocioId(), evento.conversacionId(), productoId.longValue(), tipoVideo, plan,
                reloj.instant()));
    }
}
