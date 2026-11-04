package com.autoreel.ia.infraestructura;

import com.autoreel.ia.dominio.ModeloIA;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloIARepositorio extends JpaRepository<ModeloIA, Long> {
}
