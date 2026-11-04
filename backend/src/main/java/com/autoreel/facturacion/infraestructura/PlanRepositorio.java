package com.autoreel.facturacion.infraestructura;

import java.util.List;

import com.autoreel.facturacion.dominio.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanRepositorio extends JpaRepository<Plan, Long> {

    @Query("select p from Plan p join fetch p.modeloTexto join fetch p.modeloVideo order by p.id")
    List<Plan> listarConModelos();
}
