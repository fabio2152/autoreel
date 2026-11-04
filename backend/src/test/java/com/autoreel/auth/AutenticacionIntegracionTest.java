package com.autoreel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import com.autoreel.PruebaDeIntegracion;
import com.autoreel.compartido.seguridad.CookieDeSesion;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AutenticacionIntegracionTest extends PruebaDeIntegracion {

    @Test
    void cookieDeSesionEsHttpOnly() throws Exception {
        Cookie cookie = sesionAdmin();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofHours(8).toSeconds());
    }

    @Test
    void credencialesIncorrectasSeRechazanSinRevelarSiElCorreoExiste() throws Exception {
        iniciarSesion("nadie@autoreel.test", "Cualquiera123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        iniciarSesion(CORREO_ADMIN, "Incorrecta123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void v03ConContrasenaTemporalSoloPuedeCrearSuContrasena() throws Exception {
        String correo = correoUnico();
        String temporal = negocioCreado(correo).get("contrasenaTemporal").asString();

        Cookie temporalCookie = sesion(correo, temporal);
        mvc.perform(get("/api/auth/yo").cookie(temporalCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true));
        mvc.perform(get("/api/perfil").cookie(temporalCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(get("/api/admin/negocios").cookie(temporalCookie))
                .andExpect(status().isForbidden());

        Cookie propia = cambiarContrasena(temporalCookie, "MiClave2026", "MiClave2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarContrasena").value(false))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        // Con la contraseña propia ya puede usar las rutas del negocio (404: el perfil llega en la fase 2).
        mvc.perform(get("/api/perfil").cookie(propia)).andExpect(status().isNotFound());
        iniciarSesion(correo, "MiClave2026").andExpect(status().isOk());
    }

    @Test
    void v04ContrasenaNuevaInvalidaSeRechaza() throws Exception {
        String correo = correoUnico();
        String temporal = negocioCreado(correo).get("contrasenaTemporal").asString();
        Cookie cookie = sesion(correo, temporal);

        cambiarContrasena(cookie, "Abcdef1", "Abcdef1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_MUY_CORTA"));
        cambiarContrasena(cookie, "SoloLetras", "SoloLetras")
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_SIN_NUMERO"));
        cambiarContrasena(cookie, "MiClave2026", "MiClave2027")
                .andExpect(jsonPath("$.codigo").value("CONTRASENAS_NO_COINCIDEN"));
        cambiarContrasena(cookie, temporal, temporal)
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_REPETIDA"));
    }

    @Test
    void v05ContrasenaTemporalDeMasDe48HorasSeRechaza() throws Exception {
        String correo = correoUnico();
        String temporal = negocioCreado(correo).get("contrasenaTemporal").asString();

        reloj.avanzar(Duration.ofHours(48).plusMinutes(1));

        iniciarSesion(correo, temporal)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_TEMPORAL_VENCIDA"));
    }

    @Test
    void cincoIntentosFallidosBloqueanLaCuentaQuinceMinutos() throws Exception {
        String correo = correoUnico();
        sesionNegocioListo(correo, "MiClave2026");

        for (int i = 0; i < 5; i++) {
            iniciarSesion(correo, "Equivocada" + i).andExpect(status().isUnauthorized());
        }
        iniciarSesion(correo, "MiClave2026")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));

        reloj.avanzar(Duration.ofMinutes(14));
        iniciarSesion(correo, "MiClave2026").andExpect(status().isLocked());

        reloj.avanzar(Duration.ofMinutes(2));
        iniciarSesion(correo, "MiClave2026").andExpect(status().isOk());
    }

    @Test
    void unCorreoInexistenteSeBloqueaIgualQueUnaCuentaReal() throws Exception {
        String correo = correoUnico();
        for (int i = 0; i < 5; i++) {
            iniciarSesion(correo, "Equivocada" + i)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        }
        iniciarSesion(correo, "Cualquiera1")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
    }

    @Test
    void unFormatoNoAdmitidoRespondeConSuEstadoYNoConUnErrorInterno() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NO_ADMITIDO"));
    }

    @Test
    void cambiarContrasenaPropiaExigeLaActual() throws Exception {
        String correo = correoUnico();
        Cookie cookie = sesionNegocioListo(correo, "MiClave2026");

        mvc.perform(post("/api/auth/cambiar-contrasena").with(csrf()).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("contrasenaActual", "Otra12345",
                                "nueva", "NuevaClave1", "confirmacion", "NuevaClave1"))))
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_ACTUAL_INCORRECTA"));
    }

    @Test
    void sinCabeceraCsrfLasModificacionesSeRechazan() throws Exception {
        Cookie admin = sesionAdmin();
        mvc.perform(post("/api/admin/negocios").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(solicitudNegocio(correoUnico()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CSRF_INVALIDO"));
    }

    @Test
    void sinSesionLaApiResponde401() throws Exception {
        mvc.perform(get("/api/auth/yo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/admin/negocios")).andExpect(status().isUnauthorized());
    }

    @Test
    void cerrarSesionBorraLaCookie() throws Exception {
        Cookie cookie = mvc.perform(post("/api/auth/logout").with(csrf()).cookie(sesionAdmin()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(cookie.getMaxAge()).isZero();
    }

    private ResultActions cambiarContrasena(Cookie cookie, String nueva, String confirmacion) throws Exception {
        return mvc.perform(post("/api/auth/cambiar-contrasena").with(csrf()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nueva", nueva, "confirmacion", confirmacion))));
    }
}
