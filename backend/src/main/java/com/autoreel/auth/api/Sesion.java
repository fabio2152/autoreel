package com.autoreel.auth.api;

import com.autoreel.auth.dominio.Rol;
import com.autoreel.auth.dominio.Usuario;
import com.autoreel.negocio.dominio.Negocio;

/**
 * Datos de la sesión que necesita el frontend para decidir a dónde llevar al usuario: primero a crear su contraseña
 * (RN-03), después al onboarding (RF-16) y por último a su negocio.
 *
 * @param administrador correo del administrador que entró como este negocio; vacío en una sesión normal.
 */
public record Sesion(Long id, String correo, Rol rol, Long negocioId, String nombreNegocio,
        boolean debeCambiarContrasena, boolean onboardingCompleto, String administrador) {

    public static Sesion de(Usuario usuario) {
        var negocio = usuario.getNegocio();
        return new Sesion(usuario.getId(), usuario.getCorreo(), usuario.getRol(),
                negocio == null ? null : negocio.getId(), negocio == null ? null : negocio.getNombre(),
                usuario.isDebeCambiarContrasena(), negocio == null || negocio.isOnboardingCompleto(), null);
    }

    /** El administrador ve el negocio tal como lo ve su usuario. */
    public static Sesion comoNegocio(Usuario administrador, Usuario usuarioDelNegocio) {
        Negocio negocio = usuarioDelNegocio.getNegocio();
        return new Sesion(administrador.getId(), usuarioDelNegocio.getCorreo(), Rol.NEGOCIO, negocio.getId(),
                negocio.getNombre(), false, negocio.isOnboardingCompleto(), administrador.getCorreo());
    }
}
