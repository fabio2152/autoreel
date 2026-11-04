package com.autoreel.chat.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class AgrupadorDeMensajesTest {

    private static final Instant INICIO = Instant.parse("2026-11-04T15:00:00Z");

    private record Mensaje(String texto, Instant fecha) {
    }

    private static Mensaje a(int segundos, String texto) {
        return new Mensaje(texto, INICIO.plusMillis(segundos * 1000L));
    }

    private static final Function<Mensaje, Instant> FECHA = Mensaje::fecha;

    @Test
    void v15TresMensajesEn3SegundosSonUnaSolaRespuesta() {
        List<Mensaje> mensajes = List.of(a(0, "fresas"), a(1, "crema"), a(3, "la más pedida"));

        var abierto = AgrupadorDeMensajes.agrupar(mensajes, FECHA, INICIO.plusSeconds(5));
        assertThat(abierto.cerrados()).isEmpty();
        assertThat(abierto.abiertos()).hasSize(3);
        assertThat(abierto.cierreEn()).isEqualTo(Duration.ofSeconds(2));

        var cerrado = AgrupadorDeMensajes.agrupar(mensajes, FECHA, INICIO.plusSeconds(7));
        assertThat(cerrado.cerrados()).containsExactly(mensajes);
        assertThat(cerrado.abiertos()).isEmpty();
        assertThat(cerrado.cierreEn()).isNull();
    }

    @Test
    void cadaMensajeNuevoReiniciaElPlazo() {
        // Siete mensajes, cada uno 3 s después del anterior: siguen siendo una sola respuesta.
        List<Mensaje> mensajes = List.of(a(0, "a"), a(3, "b"), a(6, "c"), a(9, "d"), a(12, "e"), a(15, "f"), a(18, "g"));

        var resultado = AgrupadorDeMensajes.agrupar(mensajes, FECHA, INICIO.plusSeconds(22));

        assertThat(resultado.cerrados()).containsExactly(mensajes);
    }

    @Test
    void unMensaje5SegundosDespuesDelUltimoAbreOtraRespuesta() {
        List<Mensaje> mensajes = List.of(a(0, "primera"), a(2, "sigue"), a(7, "segunda"));

        var resultado = AgrupadorDeMensajes.agrupar(mensajes, FECHA, INICIO.plusSeconds(8));

        assertThat(resultado.cerrados()).containsExactly(List.of(mensajes.get(0), mensajes.get(1)));
        assertThat(resultado.abiertos()).containsExactly(mensajes.get(2));
        assertThat(resultado.cierreEn()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void sinMensajesNoHayNadaQueEsperar() {
        var resultado = AgrupadorDeMensajes.agrupar(List.<Mensaje>of(), FECHA, INICIO);

        assertThat(resultado.cerrados()).isEmpty();
        assertThat(resultado.cierreEn()).isNull();
    }
}
