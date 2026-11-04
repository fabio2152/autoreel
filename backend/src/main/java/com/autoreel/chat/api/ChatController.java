package com.autoreel.chat.api;

import com.autoreel.chat.aplicacion.ServicioChat;
import com.autoreel.compartido.seguridad.UsuarioAutenticado;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/conversaciones")
public class ChatController {

    /** @param empezarDeNuevo abandona la conversación en curso y empieza otra. */
    public record SolicitudConversacion(boolean empezarDeNuevo) {
    }

    private final ServicioChat servicio;

    public ChatController(ServicioChat servicio) {
        this.servicio = servicio;
    }

    /** Devuelve la conversación en curso o empieza una (idempotente salvo con {@code empezarDeNuevo}). */
    @PostMapping
    public ConversacionVista iniciar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestBody(required = false) SolicitudConversacion solicitud) {
        return servicio.iniciar(usuario.negocioId(), solicitud != null && solicitud.empezarDeNuevo());
    }

    /** La conversación en curso o la que se completó hoy; 204 si no hay ninguna. */
    @GetMapping("/actual")
    public ResponseEntity<ConversacionVista> actual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.actual(usuario.negocioId()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/mensajes")
    public ConversacionVista responder(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @RequestBody SolicitudMensaje solicitud) {
        return servicio.responder(usuario.negocioId(), id, solicitud);
    }

    /** Cierra la respuesta agrupada si ya pasaron 4 segundos sin mensajes nuevos (RN-17). */
    @PostMapping("/{id}/procesar")
    public ConversacionVista procesar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return servicio.procesar(usuario.negocioId(), id);
    }
}
