package com.autoreel.facturacion.infraestructura;

import java.util.List;
import java.util.Optional;

import com.autoreel.facturacion.dominio.Suscripcion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SuscripcionRepositorio extends JpaRepository<Suscripcion, Long> {

    @Query("""
            select s from Suscripcion s join fetch s.negocio join fetch s.plan p join fetch p.modeloTexto
            join fetch p.modeloVideo""")
    List<Suscripcion> listarConNegocioYPlan();

    @Query("""
            select s from Suscripcion s join fetch s.negocio join fetch s.plan p join fetch p.modeloTexto
            join fetch p.modeloVideo where s.negocio.id = :negocioId""")
    Optional<Suscripcion> buscarPorNegocio(Long negocioId);

    @Query("""
            select s from Suscripcion s join fetch s.plan p join fetch p.modeloTexto join fetch p.modeloVideo
            where s.negocio.id = :negocioId""")
    Optional<Suscripcion> buscarConPlanYModelos(Long negocioId);

    /** Bloquea la suscripción para registrar un pago, cambiar el plan u otorgar un beneficio de a uno. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Suscripcion s join fetch s.negocio where s.negocio.id = :negocioId")
    Optional<Suscripcion> bloquearPorNegocio(Long negocioId);

    long countByPlanId(Long planId);
}
