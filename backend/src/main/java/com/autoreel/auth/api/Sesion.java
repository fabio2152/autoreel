package com.autoreel.auth.api;

import com.autoreel.auth.dominio.Rol;
import com.autoreel.auth.dominio.Usuario;

/**
 * Datos de la sesión que necesita el frontend para decidir a dónde llevar al usuario.
 */
public record Sesion(Long id, String correo, Rol rol, Long negocioId, String nombreNegocio,
        boolean debeCambiarContrasena) {

    static Sesion de(Usuario usuario) {
        var negocio = usuario.getNegocio();
        return new Sesion(usuario.getId(), usuario.getCorreo(), usuario.getRol(),
                negocio == null ? null : negocio.getId(), negocio == null ? null : negocio.getNombre(),
                usuario.isDebeCambiarContrasena());
    }
}
