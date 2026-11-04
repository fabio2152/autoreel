package com.autoreel.auth.api;

import com.autoreel.auth.aplicacion.ServicioAutenticacion;
import com.autoreel.auth.dominio.Usuario;
import com.autoreel.compartido.seguridad.CookieDeSesion;
import com.autoreel.compartido.seguridad.ServicioJwt;
import com.autoreel.compartido.seguridad.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ServicioAutenticacion servicio;
    private final ServicioJwt servicioJwt;
    private final CookieDeSesion cookieDeSesion;

    public AuthController(ServicioAutenticacion servicio, ServicioJwt servicioJwt, CookieDeSesion cookieDeSesion) {
        this.servicio = servicio;
        this.servicioJwt = servicioJwt;
        this.cookieDeSesion = cookieDeSesion;
    }

    @PostMapping("/login")
    public Sesion iniciarSesion(@Valid @RequestBody SolicitudInicioSesion solicitud, HttpServletResponse respuesta) {
        Usuario usuario = servicio.iniciarSesion(solicitud.correo(), solicitud.contrasena());
        cookieDeSesion.escribir(respuesta, servicioJwt.emitir(usuario));
        return Sesion.de(usuario);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(HttpServletResponse respuesta) {
        cookieDeSesion.borrar(respuesta);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cambiar-contrasena")
    public Sesion cambiarContrasena(@AuthenticationPrincipal UsuarioAutenticado actual,
            @Valid @RequestBody SolicitudCambioContrasena solicitud, HttpServletResponse respuesta) {
        Usuario usuario = servicio.cambiarContrasena(actual.id(), solicitud.contrasenaActual(), solicitud.nueva(),
                solicitud.confirmacion());
        // La versión de sesión cambió: la cookie anterior ya no sirve y se entrega una nueva.
        cookieDeSesion.escribir(respuesta, servicioJwt.emitir(usuario));
        return Sesion.de(usuario);
    }

    @GetMapping("/yo")
    public Sesion yo(@AuthenticationPrincipal UsuarioAutenticado actual) {
        return Sesion.de(servicio.buscar(actual.id()));
    }
}
