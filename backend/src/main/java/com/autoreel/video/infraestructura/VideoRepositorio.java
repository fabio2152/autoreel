package com.autoreel.video.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.autoreel.video.dominio.Video;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VideoRepositorio extends JpaRepository<Video, Long> {

    @Query("select v.id from Video v where v.conversacionId = :conversacionId")
    Optional<Long> idDeConversacion(Long conversacionId);

    @Query("select v from Video v join fetch v.planAplicado where v.id = :id and v.negocioId = :negocioId")
    Optional<Video> buscarDeNegocio(Long id, Long negocioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Video v where v.id = :id and v.negocioId = :negocioId")
    Optional<Video> bloquearDeNegocio(Long id, Long negocioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Video v where v.id = :id")
    Optional<Video> bloquear(Long id);

    @Query("select v from Video v join fetch v.modeloTextoUsado where v.id = :id")
    Optional<Video> buscarConModeloDeTexto(Long id);

    @Query("select v from Video v where v.negocioId = :negocioId order by v.fechaCreacion desc, v.id desc")
    List<Video> listarDeNegocio(Long negocioId, Limit limite);

    /** Videos esperando su guion que nadie está procesando, del más antiguo al más nuevo. */
    @Query("""
            select v.id from Video v
            where v.estado = com.autoreel.video.dominio.EstadoVideo.GUION_PENDIENTE
              and (v.guionReclamadoHasta is null or v.guionReclamadoHasta < :ahora)
            order by v.fechaCreacion, v.id""")
    List<Long> pendientesDeGuion(Instant ahora, Limit limite);

    @Query("select v.guionReclamadoHasta from Video v where v.id = :id")
    Optional<Instant> reclamadoHasta(Long id);

    /** Toma el video para escribir su guion; devuelve 1 solo si nadie lo tenía tomado. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Video v set v.guionReclamadoHasta = :hasta
            where v.id = :id and v.estado = com.autoreel.video.dominio.EstadoVideo.GUION_PENDIENTE
              and (v.guionReclamadoHasta is null or v.guionReclamadoHasta < :ahora)""")
    int reclamar(Long id, Instant ahora, Instant hasta);
}
