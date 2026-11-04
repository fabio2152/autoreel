package com.autoreel.facturacion.infraestructura;

import java.util.List;
import java.util.Optional;

import com.autoreel.facturacion.dominio.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SuscripcionRepositorio extends JpaRepository<Suscripcion, Long> {

    @Query("select s from Suscripcion s join fetch s.negocio join fetch s.plan")
    List<Suscripcion> listarConNegocioYPlan();

    @Query("select s from Suscripcion s join fetch s.negocio join fetch s.plan where s.negocio.id = :negocioId")
    Optional<Suscripcion> buscarPorNegocio(Long negocioId);
}
