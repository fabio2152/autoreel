package com.autoreel.negocio.infraestructura;

import java.util.Optional;

import com.autoreel.negocio.dominio.PerfilNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PerfilNegocioRepositorio extends JpaRepository<PerfilNegocio, Long> {

    @Query("select p from PerfilNegocio p join fetch p.negocio where p.negocio.id = :negocioId")
    Optional<PerfilNegocio> buscarPorNegocio(Long negocioId);
}
