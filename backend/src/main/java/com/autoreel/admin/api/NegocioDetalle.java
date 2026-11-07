package com.autoreel.admin.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.autoreel.costos.aplicacion.ServicioCostos.Costo;
import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.negocio.aplicacion.ServicioPerfil.PerfilParaAdmin;
import com.autoreel.negocio.dominio.EstadoNegocio;
import com.autoreel.video.dominio.EstadoVideo;

/**
 * Detalle de un negocio (RF-05). El catálogo, los pagos y los beneficios se piden aparte.
 *
 * @param planEfectivo el plan que rige hoy (RN-50): el de la suscripción o el de un beneficio vigente.
 * @param costoDelMes  costo estimado de IA del mes de Lima en curso (RF-07, V32).
 */
public record NegocioDetalle(Long id, String nombre, String nicho, String nombreNicho, EstadoNegocio estado,
        boolean onboardingCompleto, Instant fechaCreacion, Usuario usuario, Suscripcion suscripcion,
        PlanEfectivo planEfectivo, Costo costoDelMes, PerfilParaAdmin perfil, Videos videos) {

    public record Usuario(String correo, Instant ultimoAcceso, boolean debeCambiarContrasena,
            Instant contrasenaTemporalVence, boolean bloqueado) {
    }

    public record Suscripcion(Long planId, String plan, BigDecimal montoMensual, LocalDate fechaInicio,
            LocalDate proximaFechaPago, int diaPago, int diasAviso, EstadoPago estadoPago) {
    }

    /** @param beneficioHasta fin del beneficio que da este plan, o vacío si es el de la suscripción. */
    public record PlanEfectivo(Long planId, String plan, String modeloTexto, String modeloVideo, int videosPorDia,
            int regeneracionesPorVideo, Instant beneficioHasta) {
    }

    /** @param delMes videos generados en el mes de Lima en curso. */
    public record Videos(long delMes, List<VideoDelNegocio> recientes) {
    }

    /**
     * Un video, para dar soporte: su estado, el modelo de video con el que se creó, su costo y, si falló, el motivo.
     */
    public record VideoDelNegocio(Long id, String tipoVideo, EstadoVideo estado, Instant fechaCreacion,
            String modeloVideo, BigDecimal costoEstimado, String motivoError) {
    }
}
