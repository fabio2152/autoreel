package com.autoreel.facturacion.aplicacion;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.facturacion.infraestructura.SuscripcionRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan que se aplica hoy a un negocio, con sus modelos de IA ya cargados. En el MVP es el de su suscripción; los
 * beneficios temporales (fase 7) se resolverán aquí sin tocar a quien lo usa.
 */
@Component
public class ResolutorDePlanEfectivo {

    private final SuscripcionRepositorio suscripciones;

    public ResolutorDePlanEfectivo(SuscripcionRepositorio suscripciones) {
        this.suscripciones = suscripciones;
    }

    @Transactional(readOnly = true)
    public Plan de(Long negocioId) {
        return suscripciones.buscarConPlanYModelos(negocioId)
                .orElseThrow(() -> new ExcepcionDeNegocio(HttpStatus.CONFLICT, "SIN_SUSCRIPCION",
                        "Tu negocio no tiene un plan activo. Escríbenos para revisarlo."))
                .getPlan();
    }
}
