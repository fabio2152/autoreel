package com.autoreel.compartido.seguridad;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * RNF-04 y RN-08: la sesión viaja en una cookie HttpOnly y Secure, que el JavaScript del navegador no puede leer.
 */
@Component
public class CookieDeSesion {

    public static final String NOMBRE = "autoreel_sesion";

    private final PropiedadesSeguridad propiedades;

    public CookieDeSesion(PropiedadesSeguridad propiedades) {
        this.propiedades = propiedades;
    }

    public void escribir(HttpServletResponse respuesta, String token) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE,
                construir(token, Duration.ofHours(propiedades.sesionHoras())).toString());
    }

    public void borrar(HttpServletResponse respuesta) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, construir("", Duration.ZERO).toString());
    }

    public Optional<String> leer(HttpServletRequest peticion) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NOMBRE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> !valor.isBlank())
                .findFirst();
    }

    private ResponseCookie construir(String valor, Duration duracion) {
        return ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)
                .secure(propiedades.cookieSegura())
                .sameSite("Lax")
                .path("/")
                .maxAge(duracion)
                .build();
    }
}
