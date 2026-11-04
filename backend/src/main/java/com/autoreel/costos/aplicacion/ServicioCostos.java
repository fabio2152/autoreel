package com.autoreel.costos.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;

import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.costos.dominio.RegistroCosto;
import com.autoreel.costos.infraestructura.RegistroCostoRepositorio;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.UnidadCosto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registro de lo que cuesta cada llamada a un proveedor de IA (RN-32), base del panel de costos (fase 7). */
@Service
public class ServicioCostos {

    private final RegistroCostoRepositorio registros;
    private final Clock reloj;

    public ServicioCostos(RegistroCostoRepositorio registros, Clock reloj) {
        this.registros = registros;
        this.reloj = reloj;
    }

    /**
     * Registra una llamada y devuelve su costo. {@code cantidad} son solicitudes o segundos, según la unidad del
     * modelo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal registrar(Long negocioId, Long videoId, ModeloIA modelo, ConceptoCosto concepto,
            BigDecimal cantidad) {
        BigDecimal costo = modelo.getCostoReferencial().multiply(cantidad);
        registros.save(new RegistroCosto(negocioId, videoId, modelo.getProveedor(), concepto, cantidad, costo,
                reloj.instant()));
        return costo;
    }

    /** Una solicitud de un modelo que cobra por solicitud (el guion). */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal registrarSolicitud(Long negocioId, Long videoId, ModeloIA modelo, ConceptoCosto concepto) {
        if (modelo.getUnidadCosto() != UnidadCosto.POR_SOLICITUD) {
            throw new IllegalArgumentException("El modelo " + modelo.getNombre() + " no cobra por solicitud");
        }
        return registrar(negocioId, videoId, modelo, concepto, BigDecimal.ONE);
    }
}
