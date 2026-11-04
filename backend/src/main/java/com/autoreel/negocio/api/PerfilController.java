package com.autoreel.negocio.api;

import java.util.Map;

import com.autoreel.compartido.seguridad.UsuarioAutenticado;
import com.autoreel.negocio.aplicacion.ServicioPerfil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final ServicioPerfil servicio;

    public PerfilController(ServicioPerfil servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public Perfil obtener(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.obtener(usuario.negocioId());
    }

    /**
     * Cambios parciales: {@code nombre}, {@code logo} y {@code fotoLocal} (claves de archivos subidos),
     * {@code colorPrimario}, {@code colorSecundario}, {@code estilo} y {@code respuestas}. Un campo en null se borra.
     */
    @PutMapping
    public Perfil actualizar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestBody Map<String, Object> cambios) {
        return servicio.actualizar(usuario.negocioId(), cambios);
    }

    @PostMapping("/completar-onboarding")
    public Perfil completarOnboarding(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.completarOnboarding(usuario.negocioId());
    }
}
