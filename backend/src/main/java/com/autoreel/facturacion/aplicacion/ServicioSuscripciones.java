package com.autoreel.facturacion.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.api.SuscripcionDtos.BeneficioVista;
import com.autoreel.facturacion.api.SuscripcionDtos.PagoRegistrado;
import com.autoreel.facturacion.api.SuscripcionDtos.PagoVista;
import com.autoreel.facturacion.api.SuscripcionDtos.SolicitudBeneficio;
import com.autoreel.facturacion.api.SuscripcionDtos.SolicitudCambioPlan;
import com.autoreel.facturacion.api.SuscripcionDtos.SolicitudPago;
import com.autoreel.facturacion.dominio.BeneficioTemporal;
import com.autoreel.facturacion.dominio.Pago;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.facturacion.dominio.Suscripcion;
import com.autoreel.facturacion.infraestructura.BeneficioRepositorio;
import com.autoreel.facturacion.infraestructura.PagoRepositorio;
import com.autoreel.facturacion.infraestructura.PlanRepositorio;
import com.autoreel.facturacion.infraestructura.SuscripcionRepositorio;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que el administrador hace con la suscripción de un negocio: registrar pagos (RF-08, RN-42), cambiar de plan
 * (RN-49) y otorgar beneficios temporales (RF-12, RN-50 a RN-53). Cada operación bloquea la suscripción, así que dos
 * clics seguidos no registran dos pagos ni dos beneficios cruzados.
 */
@Service
public class ServicioSuscripciones {

    private final SuscripcionRepositorio suscripciones;
    private final PagoRepositorio pagos;
    private final PlanRepositorio planes;
    private final BeneficioRepositorio beneficios;
    private final ServicioNotificaciones notificaciones;
    private final Clock reloj;

    public ServicioSuscripciones(SuscripcionRepositorio suscripciones, PagoRepositorio pagos, PlanRepositorio planes,
            BeneficioRepositorio beneficios, ServicioNotificaciones notificaciones, Clock reloj) {
        this.suscripciones = suscripciones;
        this.pagos = pagos;
        this.planes = planes;
        this.beneficios = beneficios;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<PagoVista> pagos(Long negocioId) {
        existe(negocioId);
        return pagos.listarDeNegocio(negocioId).stream().map(PagoVista::de).toList();
    }

    /** RF-08 y RN-42: el pago cubre la fecha de pago actual y la próxima avanza un mes, conservando el día. */
    @Transactional
    public PagoRegistrado registrarPago(Long negocioId, SolicitudPago solicitud, Long administradorId) {
        Suscripcion suscripcion = bloquear(negocioId);
        if (solicitud.fechaPago().isAfter(LocalDate.now(reloj))) {
            throw new ExcepcionDeNegocio("PAGO_FECHA_FUTURA", "La fecha del pago no puede ser posterior a hoy.");
        }
        LocalDate cubierta = suscripcion.avanzarFechaPago();
        String nota = solicitud.nota() == null || solicitud.nota().isBlank() ? null : solicitud.nota().trim();
        Pago pago = pagos.save(new Pago(suscripcion, solicitud.fechaPago(), solicitud.monto(), solicitud.medio(),
                cubierta, nota, administradorId, reloj.instant()));
        notificaciones.pagoRegistrado(negocioId, cubierta);
        return new PagoRegistrado(PagoVista.de(pago), suscripcion.getProximaFechaPago());
    }

    /** RN-49: el plan nuevo aplica a los videos que se creen desde ahora; los que están en proceso no cambian. */
    @Transactional
    public void cambiarPlan(Long negocioId, SolicitudCambioPlan solicitud) {
        Suscripcion suscripcion = bloquear(negocioId);
        suscripcion.cambiarPlan(planActivo(solicitud.planId()), solicitud.montoMensual());
    }

    @Transactional(readOnly = true)
    public List<BeneficioVista> beneficios(Long negocioId) {
        existe(negocioId);
        Instant ahora = reloj.instant();
        return beneficios.listarDeNegocio(negocioId, Limit.of(10)).stream()
                .map(beneficio -> BeneficioVista.de(beneficio, ahora)).toList();
    }

    /** RF-12 y RN-51: con motivo, de hasta 30 días y sin cruzarse con otro beneficio del negocio. */
    @Transactional
    public BeneficioVista otorgarBeneficio(Long negocioId, SolicitudBeneficio solicitud, Long administradorId) {
        Suscripcion suscripcion = bloquear(negocioId);
        Instant ahora = reloj.instant();
        Instant desde = solicitud.desde();
        Instant hasta = solicitud.hasta();
        if (!hasta.isAfter(desde)) {
            throw new ExcepcionDeNegocio("BENEFICIO_FECHAS_INVALIDAS", "El fin del beneficio debe ser después del inicio.");
        }
        if (Duration.between(desde, hasta).compareTo(BeneficioTemporal.DURACION_MAXIMA) > 0) {
            throw new ExcepcionDeNegocio("BENEFICIO_MUY_LARGO", "Un beneficio puede durar como máximo 30 días.");
        }
        if (!hasta.isAfter(ahora)) {
            throw new ExcepcionDeNegocio("BENEFICIO_FECHAS_INVALIDAS", "El beneficio ya habría terminado.");
        }
        Plan plan = planActivo(solicitud.planId());
        if (plan.getId().equals(suscripcion.getPlan().getId())) {
            throw new ExcepcionDeNegocio("BENEFICIO_MISMO_PLAN",
                    "El negocio ya tiene ese plan. Elige otro plan para el beneficio.");
        }
        if (beneficios.existeCruce(negocioId, desde, hasta)) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "BENEFICIO_CRUZADO",
                    "El negocio ya tiene un beneficio en esas fechas. Termina el anterior o elige otras fechas.");
        }
        BeneficioTemporal beneficio = beneficios.save(new BeneficioTemporal(negocioId, plan,
                desde.isBefore(ahora) ? ahora : desde, hasta, solicitud.motivo().trim(), administradorId, ahora));
        return BeneficioVista.de(beneficio, ahora);
    }

    /** Retira un beneficio: si está vigente termina ahora; si todavía no empezaba, se borra. */
    @Transactional
    public void terminarBeneficio(Long negocioId, Long beneficioId) {
        bloquear(negocioId);
        BeneficioTemporal beneficio = beneficios.findById(beneficioId)
                .filter(encontrado -> encontrado.getNegocioId().equals(negocioId))
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese beneficio."));
        Instant ahora = reloj.instant();
        if (beneficio.pendiente(ahora)) {
            beneficios.delete(beneficio);
        } else {
            beneficio.terminar(ahora);
        }
    }

    private Plan planActivo(Long planId) {
        return planes.findById(planId).filter(Plan::isActivo)
                .orElseThrow(() -> new ExcepcionDeNegocio("PLAN_INVALIDO", "El plan elegido no existe o no está activo."));
    }

    private Suscripcion bloquear(Long negocioId) {
        return suscripciones.bloquearPorNegocio(negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese negocio."));
    }

    private void existe(Long negocioId) {
        suscripciones.buscarConPlanYModelos(negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese negocio."));
    }
}
