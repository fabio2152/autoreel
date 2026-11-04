package com.autoreel.auth.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.autoreel.negocio.dominio.Negocio;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    private final Instant ahora = Instant.parse("2026-01-15T15:00:00Z");

    @Test
    void laContrasenaTemporalVenceALas48Horas() {
        Usuario usuario = Usuario.deNegocio("Ana@Pasteleria.PE ", new Negocio("Dulce", "PASTELERIA", ahora), "hash",
                ahora);

        assertThat(usuario.getCorreo()).isEqualTo("ana@pasteleria.pe");
        assertThat(usuario.contrasenaTemporalVencida(ahora.plus(Duration.ofHours(47)))).isFalse();
        assertThat(usuario.contrasenaTemporalVencida(ahora.plus(Duration.ofHours(48)))).isTrue();
    }

    @Test
    void reiniciarLaContrasenaInvalidaSesionesYDesbloquea() {
        Usuario usuario = Usuario.deNegocio("ana@pasteleria.pe", new Negocio("Dulce", "PASTELERIA", ahora), "hash",
                ahora);
        usuario.cambiarContrasena("propia");
        for (int i = 0; i < Usuario.MAXIMO_INTENTOS; i++) {
            usuario.registrarIntentoFallido(ahora);
        }
        assertThat(usuario.estaBloqueado(ahora)).isTrue();
        int version = usuario.getVersionSesion();

        usuario.asignarContrasenaTemporal("otra", ahora);

        assertThat(usuario.getVersionSesion()).isGreaterThan(version);
        assertThat(usuario.isDebeCambiarContrasena()).isTrue();
        assertThat(usuario.estaBloqueado(ahora)).isFalse();
    }

    @Test
    void cincoIntentosFallidosBloqueanQuinceMinutos() {
        Usuario usuario = Usuario.administrador("admin@autoreel.pe", "hash", ahora);
        for (int i = 0; i < Usuario.MAXIMO_INTENTOS - 1; i++) {
            usuario.registrarIntentoFallido(ahora);
        }
        assertThat(usuario.estaBloqueado(ahora)).isFalse();

        usuario.registrarIntentoFallido(ahora);

        assertThat(usuario.estaBloqueado(ahora.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(usuario.estaBloqueado(ahora.plus(Duration.ofMinutes(15)))).isFalse();
        assertThat(usuario.getIntentosFallidos()).isZero();
    }

    @Test
    void cambiarLaContrasenaQuitaLaTemporal() {
        Usuario usuario = Usuario.deNegocio("ana@pasteleria.pe", new Negocio("Dulce", "PASTELERIA", ahora), "hash",
                ahora);

        usuario.cambiarContrasena("propia");

        assertThat(usuario.isDebeCambiarContrasena()).isFalse();
        assertThat(usuario.contrasenaTemporalVencida(ahora.plus(Duration.ofDays(30)))).isFalse();
    }
}
