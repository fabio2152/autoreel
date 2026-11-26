package com.autoreel.facturacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos;
import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos.SubidaFirmada;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
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
 * (RN-49) y otorgar beneficios por usos (RF-12, RN-50 a RN-53). Cada operación bloquea la suscripción, así que dos
 * clics seguidos no registran dos pagos ni dos beneficios a la vez.
 */
@Service
public class ServicioSuscripciones {

    private final SuscripcionRepositorio suscripciones;
    private final PagoRepositorio pagos;
    private final PlanRepositorio planes;
    private final BeneficioRepositorio beneficios;
    private final ServicioNotificaciones notificaciones;
    private final ServicioDeArchivos archivos;
    private final Clock reloj;

    public ServicioSuscripciones(SuscripcionRepositorio suscripciones, PagoRepositorio pagos, PlanRepositorio planes,
            BeneficioRepositorio beneficios, ServicioNotificaciones notificaciones, ServicioDeArchivos archivos,
            Clock reloj) {
        this.suscripciones = suscripciones;
        this.pagos = pagos;
        this.planes = planes;
        this.beneficios = beneficios;
        this.notificaciones = notificaciones;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<PagoVista> pagos(Long negocioId) {
        existe(negocioId);
        return pagos.listarDeNegocio(negocioId).stream().map(this::vista).toList();
    }

    /**
     * Firma la subida del comprobante de un pago en la carpeta del negocio que pagó. El administrador no tiene negocio
     * propio, así que el negocio sale de la ruta; la imagen se verifica al registrar el pago.
     */
    @Transactional(readOnly = true)
    public SubidaFirmada firmarSubidaComprobante(Long negocioId, String tipoContenido, long tamano) {
        existe(negocioId);
        return archivos.firmarSubida(negocioId, PropositoArchivo.COMPROBANTE_PAGO, tipoContenido, tamano);
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
        String comprobante = comprobante(negocioId, solicitud.comprobanteClave());
        Pago pago = pagos.save(new Pago(suscripcion, solicitud.fechaPago(), solicitud.monto(), solicitud.medio(),
                cubierta, nota, comprobante, administradorId, reloj.instant()));
        notificaciones.pagoRegistrado(negocioId, cubierta);
        return new PagoRegistrado(vista(pago), suscripcion.getProximaFechaPago());
    }

    /**
     * Verifica la imagen subida y la deja en su clave definitiva (la temporal se borra si el pago se guarda). Sin
     * comprobante, el pago se registra igual.
     */
    private String comprobante(Long negocioId, String claveSubida) {
        if (claveSubida == null || claveSubida.isBlank()) {
            return null;
        }
        if (!archivos.esSubida(claveSubida)) {
            throw new ExcepcionDeNegocio("ARCHIVO_INVALIDO",
                    "El comprobante no corresponde a este negocio. Vuelve a subirlo.");
        }
        return archivos.confirmar(negocioId, PropositoArchivo.COMPROBANTE_PAGO, claveSubida).clave();
    }

    private PagoVista vista(Pago pago) {
        return PagoVista.de(pago, archivos.urlDeLectura(pago.getComprobanteClave()));
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
        return beneficios.listarDeNegocio(negocioId, Limit.of(10)).stream().map(BeneficioVista::de).toList();
    }

    /** RF-12 y RN-51: con motivo, de 1 a 30 usos y solo si el negocio no tiene ya un beneficio vigente. */
    @Transactional
    public BeneficioVista otorgarBeneficio(Long negocioId, SolicitudBeneficio solicitud, Long administradorId) {
        Suscripcion suscripcion = bloquear(negocioId);
        Plan plan = planActivo(solicitud.planId());
        if (plan.getId().equals(suscripcion.getPlan().getId())) {
            throw new ExcepcionDeNegocio("BENEFICIO_MISMO_PLAN",
                    "El negocio ya tiene ese plan. Elige otro plan para el beneficio.");
        }
        if (beneficios.vigente(negocioId).isPresent()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "BENEFICIO_VIGENTE",
                    "El negocio ya tiene un beneficio con usos. Termina el anterior antes de otorgar otro.");
        }
        BeneficioTemporal beneficio = beneficios.save(new BeneficioTemporal(negocioId, plan, solicitud.usos(),
                solicitud.motivo().trim(), administradorId, reloj.instant()));
        return BeneficioVista.de(beneficio);
    }

    /**
     * Retira un beneficio: queda terminado en el historial y el negocio vuelve a su plan. Los videos ya creados con
     * él que todavía no se aprueban ya no se pueden aprobar.
     */
    @Transactional
    public void terminarBeneficio(Long negocioId, Long beneficioId) {
        bloquear(negocioId);
        BeneficioTemporal beneficio = beneficios.bloquear(beneficioId)
                .filter(encontrado -> encontrado.getNegocioId().equals(negocioId))
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese beneficio."));
        beneficio.terminar();
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
