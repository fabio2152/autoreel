package com.autoreel.negocio.infraestructura;

import java.util.Optional;

import com.autoreel.negocio.dominio.Negocio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NegocioRepositorio extends JpaRepository<Negocio, Long> {

    /** Serializa operaciones del mismo negocio que no deben cruzarse (por ejemplo, empezar dos chats a la vez). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Negocio n where n.id = :id")
    Optional<Negocio> bloquear(Long id);
}
