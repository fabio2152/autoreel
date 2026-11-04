package com.autoreel.compartido.seguridad;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * El token CSRF se genera de forma diferida. Este filtro lo carga en cada petición para que la cookie XSRF-TOKEN
 * llegue al navegador y el frontend pueda devolverla en la cabecera X-XSRF-TOKEN.
 */
public class FiltroCookieCsrf extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) peticion.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        cadena.doFilter(peticion, respuesta);
    }
}
