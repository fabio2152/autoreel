package com.autoreel.costos.infraestructura;

import java.util.List;

import com.autoreel.costos.dominio.RegistroCosto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroCostoRepositorio extends JpaRepository<RegistroCosto, Long> {

    List<RegistroCosto> findByVideoIdOrderById(Long videoId);
}
