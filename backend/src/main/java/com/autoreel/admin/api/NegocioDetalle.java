package com.autoreel.admin.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.negocio.aplicacion.ServicioPerfil.PerfilParaAdmin;
import com.autoreel.negocio.dominio.EstadoNegocio;

/**
 * Detalle de un negocio (RF-05). El catálogo se pide aparte; los pagos y los costos llegan en la fase 7.
 */
public record NegocioDetalle(Long id, String nombre, String nicho, String nombreNicho, EstadoNegocio estado,
        boolean onboardingCompleto, Instant fechaCreacion, Usuario usuario, Suscripcion suscripcion,
        PerfilParaAdmin perfil, Videos videos) {

    public record Usuario(String correo, Instant ultimoAcceso, boolean debeCambiarContrasena,
            Instant contrasenaTemporalVence, boolean bloqueado) {
    }

    public record Suscripcion(Long planId, String plan, BigDecimal montoMensual, LocalDate fechaInicio,
            LocalDate proximaFechaPago, int diasAviso, EstadoPago estadoPago) {
    }

    /** @param delMes videos generados en el mes de Lima en curso. */
    public record Videos(long delMes, java.util.List<VideoDelNegocio> recientes) {
    }

    /**
     * Un video, para dar soporte: su estado, cuántos planos usaron el respaldo y, si falló, el motivo.
     */
    public record VideoDelNegocio(Long id, String tipoVideo, com.autoreel.video.dominio.EstadoVideo estado,
            Instant fechaCreacion, long planosConRespaldo, String motivoError) {
    }
}
