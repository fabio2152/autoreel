package com.autoreel.chat.infraestructura;

import java.time.Instant;
import java.util.Optional;

import com.autoreel.chat.dominio.Conversacion;
import com.autoreel.chat.dominio.EstadoConversacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConversacionRepositorio extends JpaRepository<Conversacion, Long> {

    /**
     * Bloquea la conversación hasta el fin de la transacción: dos respuestas o dos procesamientos simultáneos se
     * atienden de a uno y no duplican mensajes ni saltan pasos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversacion c where c.id = :id and c.negocio.id = :negocioId")
    Optional<Conversacion> bloquearDeNegocio(Long id, Long negocioId);

    @Query("""
            select c from Conversacion c
            where c.negocio.id = :negocioId and c.estado = com.autoreel.chat.dominio.EstadoConversacion.EN_CURSO
            """)
    Optional<Conversacion> buscarEnCurso(Long negocioId);

    Optional<Conversacion> findFirstByNegocioIdAndEstadoOrderByFechaActualizacionDesc(Long negocioId,
            EstadoConversacion estado);

    /**
     * "Empezar de nuevo": se ejecuta antes de crear la nueva, porque el índice único de RN-19 no admite que la
     * nueva se inserte mientras la anterior sigue en curso.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Conversacion c set c.estado = com.autoreel.chat.dominio.EstadoConversacion.ABANDONADA,
                   c.fechaActualizacion = :ahora
            where c.negocio.id = :negocioId and c.estado = com.autoreel.chat.dominio.EstadoConversacion.EN_CURSO
            """)
    int abandonarEnCurso(Long negocioId, Instant ahora);
}
