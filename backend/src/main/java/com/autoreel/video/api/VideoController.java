package com.autoreel.video.api;

import java.util.List;

import com.autoreel.compartido.seguridad.UsuarioAutenticado;
import com.autoreel.video.aplicacion.ServicioVideos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final ServicioVideos servicio;

    public VideoController(ServicioVideos servicio) {
        this.servicio = servicio;
    }

    /** "Mis videos", los más recientes primero. */
    @GetMapping
    public List<VideoResumen> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.listar(usuario.negocioId());
    }

    /** El storyboard; mientras el guion está pendiente, sin planos (la pantalla vuelve a consultar). */
    @GetMapping("/{id}")
    public VideoVista obtener(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return servicio.obtener(usuario.negocioId(), id);
    }

    @PutMapping("/{id}/planos/{orden}")
    public VideoVista editarPlano(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @PathVariable int orden, @RequestBody SolicitudPlano solicitud) {
        return servicio.editarPlano(usuario.negocioId(), id, orden, solicitud);
    }

    @PostMapping("/{id}/descartar")
    public VideoVista descartar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return servicio.descartar(usuario.negocioId(), id);
    }
}
