package com.autoreel.compartido.seguridad;

import com.autoreel.auth.dominio.Rol;

/**
 * Usuario de la petición actual. Se obtiene en los controladores con {@code @AuthenticationPrincipal}.
 *
 * @param comoAdministrador el administrador entró como el negocio {@code negocioId} (decisión del 2026-11-06): tiene
 *                          el rol NEGOCIO, pero {@code id} y {@code correo} son los del administrador.
 */
public record UsuarioAutenticado(Long id, String correo, Rol rol, Long negocioId, boolean debeCambiarContrasena,
        boolean comoAdministrador) {
}
