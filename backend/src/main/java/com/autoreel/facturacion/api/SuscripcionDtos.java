package com.autoreel.facturacion.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.autoreel.facturacion.dominio.BeneficioTemporal;
import com.autoreel.facturacion.dominio.MedioPago;
import com.autoreel.facturacion.dominio.Pago;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas de pagos, cambio de plan y beneficios de un negocio (RF-08, RF-12, RN-49). */
public final class SuscripcionDtos {

    private SuscripcionDtos() {
    }

    public record SolicitudPago(
            @NotNull(message = "Elige la fecha del pago.") LocalDate fechaPago,
            @NotNull(message = "Escribe el monto pagado.")
            @Positive(message = "El monto debe ser mayor que cero.")
            @Digits(integer = 8, fraction = 2, message = "El monto admite hasta dos decimales.") BigDecimal monto,
            @NotNull(message = "Elige el medio de pago.") MedioPago medio,
            @Size(max = 300, message = "La nota puede tener como máximo 300 caracteres.") String nota) {
    }

    public record PagoVista(Long id, LocalDate fechaPago, BigDecimal monto, MedioPago medio,
            LocalDate periodoCubierto, String nota, Instant fechaRegistro) {

        public static PagoVista de(Pago pago) {
            return new PagoVista(pago.getId(), pago.getFechaPago(), pago.getMonto(), pago.getMedio(),
                    pago.getPeriodoCubierto(), pago.getNota(), pago.getFechaRegistro());
        }
    }

    /** @param proximaFechaPago la nueva fecha, ya avanzada un mes. */
    public record PagoRegistrado(PagoVista pago, LocalDate proximaFechaPago) {
    }

    public record SolicitudCambioPlan(
            @NotNull(message = "Elige el plan.") Long planId,
            @NotNull(message = "Escribe el monto mensual.")
            @Positive(message = "El monto mensual debe ser mayor que cero.")
            @Digits(integer = 8, fraction = 2, message = "El monto mensual admite hasta dos decimales.")
            BigDecimal montoMensual) {
    }

    public record SolicitudBeneficio(
            @NotNull(message = "Elige el plan del beneficio.") Long planId,
            @NotNull(message = "Elige desde cuándo rige el beneficio.") Instant desde,
            @NotNull(message = "Elige hasta cuándo rige el beneficio.") Instant hasta,
            @NotBlank(message = "Escribe el motivo del beneficio.")
            @Size(max = 300, message = "El motivo puede tener como máximo 300 caracteres.") String motivo) {
    }

    public enum EstadoBeneficio {
        PROGRAMADO,
        VIGENTE,
        TERMINADO
    }

    public record BeneficioVista(Long id, Long planId, String plan, String modeloVideo, Instant desde, Instant hasta,
            String motivo, EstadoBeneficio estado) {

        public static BeneficioVista de(BeneficioTemporal beneficio, Instant ahora) {
            EstadoBeneficio estado = beneficio.pendiente(ahora) ? EstadoBeneficio.PROGRAMADO
                    : beneficio.vigente(ahora) ? EstadoBeneficio.VIGENTE : EstadoBeneficio.TERMINADO;
            return new BeneficioVista(beneficio.getId(), beneficio.getPlan().getId(), beneficio.getPlan().getNombre(),
                    beneficio.getPlan().getModeloVideo().getNombre(), beneficio.getDesde(), beneficio.getHasta(),
                    beneficio.getMotivo(), estado);
        }
    }
}
