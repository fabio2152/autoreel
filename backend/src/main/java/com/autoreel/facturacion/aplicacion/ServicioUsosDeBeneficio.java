package com.autoreel.facturacion.aplicacion;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.dominio.BeneficioTemporal;
import com.autoreel.facturacion.infraestructura.BeneficioRepositorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usos de un beneficio (RF-12, por usos desde el 2026-11-25). Se descuentan junto con el video del día, en la misma
 * transacción, y se devuelven junto con él si el video falla (RN-27). El beneficio se bloquea, así que dos
 * aprobaciones a la vez no gastan más usos de los otorgados.
 */
@Service
public class ServicioUsosDeBeneficio {

    private final BeneficioRepositorio beneficios;

    public ServicioUsosDeBeneficio(BeneficioRepositorio beneficios) {
        this.beneficios = beneficios;
    }

    /**
     * Descuenta un uso del beneficio con el que se creó el video. Si ya no le quedan usos (otros videos los gastaron
     * o el administrador lo terminó), el video no se puede aprobar: el negocio lo descarta y crea otro con su plan.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consumir(Long beneficioId) {
        BeneficioTemporal beneficio = beneficios.bloquear(beneficioId).orElseThrow();
        if (beneficio.isTerminado()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "BENEFICIO_TERMINADO",
                    "Este video se creó con un beneficio que ya terminó. Descártalo y crea otro con tu plan.");
        }
        if (!beneficio.vigente()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "BENEFICIO_AGOTADO",
                    "Este video se creó con un beneficio que ya se usó por completo. Descártalo y crea otro con "
                            + "tu plan.");
        }
        beneficio.consumirUso();
    }

    /** RN-27: el video que usó el beneficio falló; el uso vuelve, aunque el beneficio ya estuviera agotado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void devolver(Long beneficioId) {
        beneficios.bloquear(beneficioId).ifPresent(BeneficioTemporal::devolverUso);
    }
}
