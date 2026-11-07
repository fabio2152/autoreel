package com.autoreel.facturacion.infraestructura;

import java.util.List;

import com.autoreel.facturacion.dominio.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PagoRepositorio extends JpaRepository<Pago, Long> {

    @Query("""
            select p from Pago p where p.suscripcion.negocio.id = :negocioId
            order by p.periodoCubierto desc, p.id desc""")
    List<Pago> listarDeNegocio(Long negocioId);
}
