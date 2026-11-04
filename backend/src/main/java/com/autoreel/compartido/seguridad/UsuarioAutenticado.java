package com.autoreel.compartido.seguridad;

import com.autoreel.auth.dominio.Rol;

/**
 * Usuario de la petición actual. Se obtiene en los controladores con {@code @AuthenticationPrincipal}.
 */
public record UsuarioAutenticado(Long id, String correo, Rol rol, Long negocioId, boolean debeCambiarContrasena) {
}
