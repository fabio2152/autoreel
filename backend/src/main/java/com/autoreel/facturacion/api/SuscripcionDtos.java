package com.autoreel.facturacion.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.autoreel.facturacion.dominio.BeneficioTemporal;
import com.autoreel.facturacion.dominio.MedioPago;
import com.autoreel.facturacion.dominio.Pago;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas de pagos, cambio de plan y beneficios de un negocio (RF-08, RF-12, RN-49). */
public final class SuscripcionDtos {

    private SuscripcionDtos() {
    }

    /**
     * @param comprobanteClave opcional: la clave temporal que devolvió {@code url-subida-comprobante} después de subir
     *                         la imagen del comprobante.
     */
    public record SolicitudPago(
            @NotNull(message = "Elige la fecha del pago.") LocalDate fechaPago,
            @NotNull(message = "Escribe el monto pagado.")
            @Positive(message = "El monto debe ser mayor que cero.")
            @Digits(integer = 8, fraction = 2, message = "El monto admite hasta dos decimales.") BigDecimal monto,
            @NotNull(message = "Elige el medio de pago.") MedioPago medio,
            @Size(max = 300, message = "La nota puede tener como máximo 300 caracteres.") String nota,
            @Size(max = 300, message = "El comprobante no es válido. Vuelve a subirlo.") String comprobanteClave) {
    }

    /** Para firmar la subida del comprobante de un pago: JPG, PNG o WebP de hasta 5 MB. */
    public record SolicitudSubidaComprobante(
            @NotBlank(message = "Indica el tipo de archivo.") String tipoContenido,
            @NotNull(message = "Indica el tamaño del archivo.") Long tamano) {
    }

    /** @param comprobanteUrl URL temporal de lectura de la imagen del comprobante, o vacía si el pago no tiene. */
    public record PagoVista(Long id, LocalDate fechaPago, BigDecimal monto, MedioPago medio,
            LocalDate periodoCubierto, String nota, String comprobanteUrl, Instant fechaRegistro) {

        public static PagoVista de(Pago pago, String comprobanteUrl) {
            return new PagoVista(pago.getId(), pago.getFechaPago(), pago.getMonto(), pago.getMedio(),
                    pago.getPeriodoCubierto(), pago.getNota(), comprobanteUrl, pago.getFechaRegistro());
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

    /** @param usos cuántos videos aprobados cubre el beneficio (RN-51: de 1 a 30). */
    public record SolicitudBeneficio(
            @NotNull(message = "Elige el plan del beneficio.") Long planId,
            @NotNull(message = "Escribe la cantidad de usos del beneficio.")
            @Min(value = 1, message = "La cantidad de usos debe estar entre 1 y " + BeneficioTemporal.USOS_MAXIMOS + ".")
            @Max(value = BeneficioTemporal.USOS_MAXIMOS,
                    message = "La cantidad de usos debe estar entre 1 y " + BeneficioTemporal.USOS_MAXIMOS + ".")
            Integer usos,
            @NotBlank(message = "Escribe el motivo del beneficio.")
            @Size(max = 300, message = "El motivo puede tener como máximo 300 caracteres.") String motivo) {
    }

    /** VIGENTE: le quedan usos; AGOTADO: los gastó todos; TERMINADO: lo retiró el administrador (o es por fechas). */
    public enum EstadoBeneficio {
        VIGENTE,
        AGOTADO,
        TERMINADO
    }

    /**
     * @param desde solo en los beneficios por fechas, anteriores al 2026-11-25 (historial); vacío en los por usos.
     * @param hasta igual que {@code desde}.
     */
    public record BeneficioVista(Long id, Long planId, String plan, String modeloVideo, int usosOtorgados,
            int usosConsumidos, Instant fechaCreacion, Instant desde, Instant hasta, String motivo,
            EstadoBeneficio estado) {

        public static BeneficioVista de(BeneficioTemporal beneficio) {
            EstadoBeneficio estado = beneficio.vigente() ? EstadoBeneficio.VIGENTE
                    : beneficio.agotado() ? EstadoBeneficio.AGOTADO : EstadoBeneficio.TERMINADO;
            return new BeneficioVista(beneficio.getId(), beneficio.getPlan().getId(), beneficio.getPlan().getNombre(),
                    beneficio.getPlan().getModeloVideo().getNombre(), beneficio.getUsosOtorgados(),
                    beneficio.getUsosConsumidos(), beneficio.getFechaCreacion(), beneficio.getDesde(),
                    beneficio.getHasta(), beneficio.getMotivo(), estado);
        }
    }
}
