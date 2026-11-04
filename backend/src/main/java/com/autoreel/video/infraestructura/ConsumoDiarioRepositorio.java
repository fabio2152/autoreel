package com.autoreel.video.infraestructura;

import java.time.LocalDate;
import java.util.Optional;

import com.autoreel.video.dominio.ConsumoDiario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConsumoDiarioRepositorio extends JpaRepository<ConsumoDiario, Long> {

    /** Crea la fila del día si no existe; con dos aprobaciones a la vez, solo una la crea. */
    @Modifying
    @Query(value = """
            insert into consumo_diario (negocio_id, fecha, videos_generados) values (:negocioId, :fecha, 0)
            on conflict (negocio_id, fecha) do nothing""", nativeQuery = true)
    void crearSiFalta(Long negocioId, LocalDate fecha);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConsumoDiario c where c.negocioId = :negocioId and c.fecha = :fecha")
    Optional<ConsumoDiario> bloquear(Long negocioId, LocalDate fecha);

    Optional<ConsumoDiario> findByNegocioIdAndFecha(Long negocioId, LocalDate fecha);

    @Query("""
            select coalesce(sum(c.videosGenerados), 0) from ConsumoDiario c
            where c.negocioId = :negocioId and c.fecha between :desde and :hasta""")
    long sumar(Long negocioId, LocalDate desde, LocalDate hasta);
}
