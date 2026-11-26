package com.autoreel.facturacion.aplicacion;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.dominio.BeneficioTemporal;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.facturacion.infraestructura.BeneficioRepositorio;
import com.autoreel.facturacion.infraestructura.SuscripcionRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-50: plan que se aplica hoy a un negocio, con sus modelos de IA ya cargados. Si tiene un beneficio vigente (sin
 * terminar y con usos), el del beneficio; si no, el de su suscripción. Al gastar los usos del beneficio, vuelve solo a
 * su plan (RN-52).
 */
@Component
public class ResolutorDePlanEfectivo {

    /** @param beneficio el beneficio que da el plan, o vacío si es el de la suscripción. */
    public record PlanEfectivo(Plan plan, BeneficioTemporal beneficio) {
    }

    private final SuscripcionRepositorio suscripciones;
    private final BeneficioRepositorio beneficios;

    public ResolutorDePlanEfectivo(SuscripcionRepositorio suscripciones, BeneficioRepositorio beneficios) {
        this.suscripciones = suscripciones;
        this.beneficios = beneficios;
    }

    @Transactional(readOnly = true)
    public Plan de(Long negocioId) {
        return resolver(negocioId).plan();
    }

    @Transactional(readOnly = true)
    public PlanEfectivo resolver(Long negocioId) {
        var beneficio = beneficios.vigente(negocioId);
        if (beneficio.isPresent()) {
            return new PlanEfectivo(beneficio.get().getPlan(), beneficio.get());
        }
        Plan plan = suscripciones.buscarConPlanYModelos(negocioId)
                .orElseThrow(() -> new ExcepcionDeNegocio(HttpStatus.CONFLICT, "SIN_SUSCRIPCION",
                        "Tu negocio no tiene un plan activo. Escríbenos para revisarlo."))
                .getPlan();
        return new PlanEfectivo(plan, null);
    }
}
