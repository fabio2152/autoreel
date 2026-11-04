package com.autoreel.compartido.seguridad;

import java.io.IOException;
import java.util.List;

import com.autoreel.auth.dominio.Usuario;
import com.autoreel.auth.infraestructura.UsuarioRepositorio;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica cada petición a partir de la cookie de sesión. Los permisos salen de la base de datos, no del token:
 * así, reiniciar la contraseña o suspender el negocio cierra las sesiones abiertas al instante (RN-05, RN-07).
 * Si la cookie no sirve, la petición sigue como anónima y las reglas de acceso responden 401.
 */
public class FiltroJwtCookie extends OncePerRequestFilter {

    /** Única autoridad de quien todavía usa una contraseña temporal (RN-03). */
    public static final String CAMBIO_PENDIENTE = "CAMBIO_PENDIENTE";

    private final ServicioJwt servicioJwt;
    private final CookieDeSesion cookieDeSesion;
    private final UsuarioRepositorio usuarios;

    public FiltroJwtCookie(ServicioJwt servicioJwt, CookieDeSesion cookieDeSesion, UsuarioRepositorio usuarios) {
        this.servicioJwt = servicioJwt;
        this.cookieDeSesion = cookieDeSesion;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        cookieDeSesion.leer(peticion).ifPresent(token -> autenticar(token, peticion));
        cadena.doFilter(peticion, respuesta);
    }

    private void autenticar(String token, HttpServletRequest peticion) {
        Jwt jwt;
        Long idUsuario;
        try {
            jwt = servicioJwt.leer(token);
            idUsuario = Long.valueOf(jwt.getSubject());
        } catch (JwtException | NumberFormatException e) {
            return;
        }
        Usuario usuario = usuarios.buscarConNegocio(idUsuario).orElse(null);
        if (usuario == null || !sesionVigente(usuario, jwt)) {
            return;
        }
        UsuarioAutenticado principal = new UsuarioAutenticado(usuario.getId(), usuario.getCorreo(), usuario.getRol(),
                usuario.getNegocio() == null ? null : usuario.getNegocio().getId(), usuario.isDebeCambiarContrasena());
        var autenticacion = UsernamePasswordAuthenticationToken.authenticated(principal, null, autoridades(usuario));
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
    }

    private static boolean sesionVigente(Usuario usuario, Jwt jwt) {
        Object version = jwt.getClaims().get(ServicioJwt.CLAIM_VERSION);
        return version instanceof Number numero
                && numero.intValue() == usuario.getVersionSesion()
                && usuario.isActivo()
                && !usuario.negocioSuspendido();
    }

    private static List<GrantedAuthority> autoridades(Usuario usuario) {
        if (usuario.isDebeCambiarContrasena()) {
            return List.of(new SimpleGrantedAuthority(CAMBIO_PENDIENTE));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
    }
}
