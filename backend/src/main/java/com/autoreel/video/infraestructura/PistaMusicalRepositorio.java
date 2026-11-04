package com.autoreel.video.infraestructura;

import java.util.List;

import com.autoreel.video.dominio.PistaMusical;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PistaMusicalRepositorio extends JpaRepository<PistaMusical, Long> {

    boolean existsByClave(String clave);

    List<PistaMusical> findByNichoAndEstiloAndActivaTrueOrderById(String nicho, String estilo);
}
