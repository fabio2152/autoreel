package com.autoreel.admin.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.negocio.aplicacion.ServicioPerfil.PerfilParaAdmin;
import com.autoreel.negocio.dominio.EstadoNegocio;

/**
 * Detalle de un negocio (RF-05). El catálogo se pide aparte; pagos, videos y consumo se agregan en sus fases.
 */
public record NegocioDetalle(Long id, String nombre, String nicho, String nombreNicho, EstadoNegocio estado,
        boolean onboardingCompleto, Instant fechaCreacion, Usuario usuario, Suscripcion suscripcion,
        PerfilParaAdmin perfil) {

    public record Usuario(String correo, Instant ultimoAcceso, boolean debeCambiarContrasena,
            Instant contrasenaTemporalVence, boolean bloqueado) {
    }

    public record Suscripcion(Long planId, String plan, BigDecimal montoMensual, LocalDate fechaInicio,
            LocalDate proximaFechaPago, int diasAviso, EstadoPago estadoPago) {
    }
}
