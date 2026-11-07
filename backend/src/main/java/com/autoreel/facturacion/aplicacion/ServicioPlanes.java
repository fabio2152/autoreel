package com.autoreel.facturacion.aplicacion;

import java.util.List;
import java.util.Locale;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.api.PlanResumen;
import com.autoreel.facturacion.api.SolicitudPlan;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.facturacion.infraestructura.PlanRepositorio;
import com.autoreel.facturacion.infraestructura.SuscripcionRepositorio;
import com.autoreel.ia.dominio.EstadoModelo;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.TipoModelo;
import com.autoreel.ia.infraestructura.ModeloIARepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-10: gestión de planes desde el panel, sin programar. Un cambio de plan rige para los videos nuevos; los que ya
 * existen conservan el plan y los modelos con los que nacieron (RN-54).
 */
@Service
public class ServicioPlanes {

    private final PlanRepositorio planes;
    private final SuscripcionRepositorio suscripciones;
    private final ModeloIARepositorio modelos;

    public ServicioPlanes(PlanRepositorio planes, SuscripcionRepositorio suscripciones, ModeloIARepositorio modelos) {
        this.planes = planes;
        this.suscripciones = suscripciones;
        this.modelos = modelos;
    }

    @Transactional(readOnly = true)
    public List<PlanResumen> listar() {
        return planes.listarConModelos().stream()
                .map(plan -> PlanResumen.de(plan, suscripciones.countByPlanId(plan.getId()))).toList();
    }

    @Transactional
    public PlanResumen crear(SolicitudPlan solicitud) {
        String nombre = solicitud.nombre().trim();
        if (planes.existeNombre(nombre.toLowerCase(Locale.ROOT), -1L)) {
            throw yaExiste();
        }
        Plan plan = planes.save(new Plan(nombre, descripcion(solicitud), solicitud.videosPorDia(),
                solicitud.regeneracionesPorVideo(), modelo(solicitud.modeloTextoId(), TipoModelo.TEXTO),
                modelo(solicitud.modeloVideoId(), TipoModelo.VIDEO), solicitud.precioReferencial(),
                solicitud.activo()));
        return PlanResumen.de(plan, 0);
    }

    @Transactional
    public PlanResumen actualizar(Long id, SolicitudPlan solicitud) {
        Plan plan = planes.findById(id).orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese plan."));
        String nombre = solicitud.nombre().trim();
        if (planes.existeNombre(nombre.toLowerCase(Locale.ROOT), id)) {
            throw yaExiste();
        }
        plan.actualizar(nombre, descripcion(solicitud), solicitud.videosPorDia(), solicitud.regeneracionesPorVideo(),
                modelo(solicitud.modeloTextoId(), TipoModelo.TEXTO), modelo(solicitud.modeloVideoId(), TipoModelo.VIDEO),
                solicitud.precioReferencial(), solicitud.activo());
        return PlanResumen.de(plan, suscripciones.countByPlanId(id));
    }

    /** RN-47: los modelos del plan deben estar activos y ser del tipo correcto. */
    private ModeloIA modelo(Long id, TipoModelo tipo) {
        ModeloIA modelo = modelos.findById(id)
                .orElseThrow(() -> new ExcepcionDeNegocio("MODELO_INVALIDO", "El modelo elegido no existe."));
        if (modelo.getTipo() != tipo) {
            throw new ExcepcionDeNegocio("MODELO_INVALIDO", tipo == TipoModelo.TEXTO
                    ? "El modelo de texto debe ser un modelo de texto." : "El modelo de video debe ser un modelo de video.");
        }
        if (modelo.getEstado() != EstadoModelo.ACTIVO) {
            throw new ExcepcionDeNegocio("MODELO_INACTIVO",
                    "El modelo " + modelo.getNombre() + " está inactivo. Actívalo en Integraciones antes de asignarlo.");
        }
        return modelo;
    }

    private static String descripcion(SolicitudPlan solicitud) {
        return solicitud.descripcion() == null || solicitud.descripcion().isBlank() ? null
                : solicitud.descripcion().trim();
    }

    private static ExcepcionDeNegocio yaExiste() {
        return new ExcepcionDeNegocio(HttpStatus.CONFLICT, "PLAN_YA_EXISTE", "Ya existe un plan con ese nombre.");
    }
}
