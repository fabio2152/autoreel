package com.autoreel.auth.api;

import com.autoreel.auth.dominio.Rol;
import com.autoreel.auth.dominio.Usuario;

/**
 * Datos de la sesión que necesita el frontend para decidir a dónde llevar al usuario: primero a crear su contraseña
 * (RN-03), después al onboarding (RF-16) y por último a su negocio.
 */
public record Sesion(Long id, String correo, Rol rol, Long negocioId, String nombreNegocio,
        boolean debeCambiarContrasena, boolean onboardingCompleto) {

    static Sesion de(Usuario usuario) {
        var negocio = usuario.getNegocio();
        return new Sesion(usuario.getId(), usuario.getCorreo(), usuario.getRol(),
                negocio == null ? null : negocio.getId(), negocio == null ? null : negocio.getNombre(),
                usuario.isDebeCambiarContrasena(), negocio == null || negocio.isOnboardingCompleto());
    }
}
