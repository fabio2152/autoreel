package com.autoreel.compartido.seguridad;

import java.io.IOException;

import com.autoreel.compartido.error.ErrorApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Los errores de seguridad se producen antes de llegar a los controladores; aquí se responden con la misma
 * forma {@code {"codigo","mensaje"}} que el resto de la API.
 */
@Component
public class RespuestasDeSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;

    public RespuestasDeSeguridad(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException e)
            throws IOException {
        escribir(respuesta, HttpServletResponse.SC_UNAUTHORIZED,
                new ErrorApi("NO_AUTENTICADO", "Tu sesión no está activa. Inicia sesión para continuar."));
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException e)
            throws IOException {
        ErrorApi error = e instanceof CsrfException
                ? new ErrorApi("CSRF_INVALIDO", "Tu sesión de seguridad expiró. Recarga la página e inténtalo de nuevo.")
                : new ErrorApi("ACCESO_DENEGADO", "No tienes permiso para acceder a esta sección.");
        escribir(respuesta, HttpServletResponse.SC_FORBIDDEN, error);
    }

    private void escribir(HttpServletResponse respuesta, int estado, ErrorApi error) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), error);
    }
}
