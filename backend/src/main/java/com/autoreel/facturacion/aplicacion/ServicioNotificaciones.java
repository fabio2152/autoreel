package com.autoreel.facturacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.facturacion.dominio.NotificacionAdmin;
import com.autoreel.facturacion.dominio.Suscripcion;
import com.autoreel.facturacion.dominio.TipoNotificacion;
import com.autoreel.facturacion.infraestructura.NotificacionRepositorio;
import com.autoreel.facturacion.infraestructura.SuscripcionRepositorio;
import com.autoreel.negocio.dominio.Negocio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificaciones del administrador (RF-09): las de pago las crea la tarea diaria (RN-44 y RN-45) y las de integración,
 * una falla de un proveedor de IA (RN-57). Ninguna se repite para el mismo negocio (o modelo) y período.
 */
@Service
public class ServicioNotificaciones {

    /** RN-45: días de vencido tras los que se sugiere suspender. */
    public static final int DIAS_PARA_SUGERIR_SUSPENSION = 7;
    private static final int MAXIMO_MENSAJE = 300;
    private static final Logger log = LoggerFactory.getLogger(ServicioNotificaciones.class);

    public record Bandeja(long sinLeer, List<NotificacionAdmin> notificaciones) {
    }

    private final NotificacionRepositorio notificaciones;
    private final SuscripcionRepositorio suscripciones;
    private final Clock reloj;

    public ServicioNotificaciones(NotificacionRepositorio notificaciones, SuscripcionRepositorio suscripciones,
            Clock reloj) {
        this.notificaciones = notificaciones;
        this.suscripciones = suscripciones;
        this.reloj = reloj;
    }

    /**
     * RN-44 y RN-45: revisa el estado de pago de cada negocio según el día de Lima y crea las notificaciones que
     * correspondan. Se puede ejecutar varias veces en el mismo día sin duplicar nada.
     *
     * @return cuántas notificaciones nuevas creó.
     */
    @Transactional
    public int generarDePagos() {
        LocalDate hoy = LocalDate.now(reloj);
        int creadas = 0;
        for (Suscripcion suscripcion : suscripciones.listarConNegocioYPlan()) {
            Negocio negocio = suscripcion.getNegocio();
            LocalDate fecha = suscripcion.getProximaFechaPago();
            EstadoPago estado = suscripcion.estadoPago(hoy);
            String nombre = negocio.getNombre();
            switch (estado) {
                case POR_VENCER -> {
                    long dias = ChronoUnit.DAYS.between(hoy, fecha);
                    creadas += crear(TipoNotificacion.PAGO_POR_VENCER, negocio.getId(), null, fecha,
                            "El pago de " + nombre + " vence " + (dias == 1 ? "mañana." : "en " + dias + " días."));
                }
                case VENCE_HOY -> creadas += crear(TipoNotificacion.PAGO_HOY, negocio.getId(), null, fecha,
                        "El pago de " + nombre + " vence hoy.");
                case VENCIDO -> {
                    creadas += crear(TipoNotificacion.PAGO_VENCIDO, negocio.getId(), null, fecha,
                            "El pago de " + nombre + " está vencido.");
                    if (ChronoUnit.DAYS.between(fecha, hoy) >= DIAS_PARA_SUGERIR_SUSPENSION
                            && !negocio.estaSuspendido()) {
                        creadas += crear(TipoNotificacion.SUGERENCIA_SUSPENSION, negocio.getId(), null, fecha,
                                "El pago de " + nombre + " lleva " + DIAS_PARA_SUGERIR_SUSPENSION
                                        + " días vencido. Puedes suspenderlo desde su detalle.");
                    }
                }
                case AL_DIA -> {
                }
            }
        }
        if (creadas > 0) {
            log.info("Tarea de pagos: {} notificaciones nuevas", creadas);
        }
        return creadas;
    }

    /**
     * RN-57: una por modelo y por día. Va en su propia transacción: si falla, no deshace el proceso que la llamó.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void integracionConError(Long modeloId, String mensaje) {
        crear(TipoNotificacion.INTEGRACION_CON_ERROR, null, modeloId, LocalDate.now(reloj), mensaje);
    }

    @Transactional(readOnly = true)
    public Bandeja bandeja() {
        return new Bandeja(notificaciones.countByLeidaFalse(), notificaciones.recientes(Limit.of(50)));
    }

    @Transactional
    public void marcarLeida(Long id) {
        notificaciones.findById(id)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos esa notificación."))
                .marcarLeida();
    }

    @Transactional
    public void marcarTodasLeidas() {
        notificaciones.marcarTodasLeidas();
    }

    /** Al registrar el pago de una fecha, sus avisos (por vencer, hoy, vencido, sugerencia) quedan atendidos. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void pagoRegistrado(Long negocioId, LocalDate periodo) {
        notificaciones.marcarLeidasDePago(negocioId, periodo);
    }

    private int crear(TipoNotificacion tipo, Long negocioId, Long modeloId, LocalDate periodo, String mensaje) {
        String texto = mensaje.length() > MAXIMO_MENSAJE ? mensaje.substring(0, MAXIMO_MENSAJE - 1) + "…" : mensaje;
        return notificaciones.crearSiNoExiste(tipo.name(), negocioId, modeloId, periodo, texto, reloj.instant());
    }
}
