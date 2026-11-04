package com.autoreel.negocio.infraestructura;

import com.autoreel.negocio.dominio.Negocio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NegocioRepositorio extends JpaRepository<Negocio, Long> {
}
