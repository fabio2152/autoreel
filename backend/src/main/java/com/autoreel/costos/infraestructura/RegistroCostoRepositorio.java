package com.autoreel.costos.infraestructura;

import java.time.Instant;
import java.util.List;

import com.autoreel.costos.dominio.RegistroCosto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegistroCostoRepositorio extends JpaRepository<RegistroCosto, Long> {

    List<RegistroCosto> findByVideoIdOrderById(Long videoId);

    @Query("""
            select new com.autoreel.costos.infraestructura.SumaDeCosto(
                r.negocioId, r.concepto, sum(r.costoUsd), count(distinct r.videoId))
            from RegistroCosto r where r.fechaHora >= :desde and r.fechaHora < :hasta
            group by r.negocioId, r.concepto""")
    List<SumaDeCosto> sumar(Instant desde, Instant hasta);

    @Query("""
            select new com.autoreel.costos.infraestructura.SumaDeCosto(
                r.negocioId, r.concepto, sum(r.costoUsd), count(distinct r.videoId))
            from RegistroCosto r where r.negocioId = :negocioId and r.fechaHora >= :desde and r.fechaHora < :hasta
            group by r.negocioId, r.concepto""")
    List<SumaDeCosto> sumarDeNegocio(Long negocioId, Instant desde, Instant hasta);
}
