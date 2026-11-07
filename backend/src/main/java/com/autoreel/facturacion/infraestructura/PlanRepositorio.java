package com.autoreel.facturacion.infraestructura;

import java.util.List;

import com.autoreel.facturacion.dominio.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanRepositorio extends JpaRepository<Plan, Long> {

    @Query("select p from Plan p join fetch p.modeloTexto join fetch p.modeloVideo order by p.id")
    List<Plan> listarConModelos();

    @Query("select count(p) > 0 from Plan p where lower(p.nombre) = :nombre and p.id <> :excepto")
    boolean existeNombre(String nombre, Long excepto);

    /** Nombres de los planes que usan el modelo, y si alguno está activo o tiene negocios (RN-48). */
    @Query("""
            select p from Plan p where p.modeloTexto.id = :modeloId or p.modeloVideo.id = :modeloId order by p.id""")
    List<Plan> queUsanModelo(Long modeloId);
}
