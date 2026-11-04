package com.autoreel.catalogo.infraestructura;

import java.util.List;
import java.util.Optional;

import com.autoreel.catalogo.dominio.EstadoProducto;
import com.autoreel.catalogo.dominio.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Toda consulta lleva el negocio: un negocio nunca ve productos de otro (RNF-05).
 */
public interface ProductoRepositorio extends JpaRepository<Producto, Long> {

    @Query("""
            select distinct p from Producto p left join fetch p.fotos
            where p.negocio.id = :negocioId and (:estado is null or p.estado = :estado)
            order by p.fechaCreacion desc
            """)
    List<Producto> listarDeNegocio(Long negocioId, EstadoProducto estado);

    @Query("select p from Producto p left join fetch p.fotos where p.id = :id and p.negocio.id = :negocioId")
    Optional<Producto> buscarDeNegocio(Long id, Long negocioId);
}
