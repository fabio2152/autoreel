package com.autoreel.ia.infraestructura;

import com.autoreel.ia.dominio.ModeloIA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModeloIARepositorio extends JpaRepository<ModeloIA, Long> {

    @Query("""
            select count(m) > 0 from ModeloIA m
            where m.adaptador = :adaptador and m.identificadorModelo = :identificador and m.id <> :excepto""")
    boolean existeAdaptadorEIdentificador(String adaptador, String identificador, Long excepto);
}
