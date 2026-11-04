package com.autoreel.catalogo.infraestructura;

import java.util.List;
import java.util.Optional;

import com.autoreel.catalogo.dominio.EstadoProducto;
import com.autoreel.catalogo.dominio.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Toda consulta lleva el negocio: un negocio nunca ve productos de otro (RNF-05). El catálogo solo muestra los
 * productos guardados en él; los subidos desde el chat sin guardar existen solo para su video (RN-14).
 */
public interface ProductoRepositorio extends JpaRepository<Producto, Long> {

    @Query("""
            select distinct p from Producto p left join fetch p.fotos
            where p.negocio.id = :negocioId and p.enCatalogo = true and (:estado is null or p.estado = :estado)
            order by p.fechaCreacion desc
            """)
    List<Producto> listarDeNegocio(Long negocioId, EstadoProducto estado);

    @Query("""
            select p from Producto p left join fetch p.fotos
            where p.id = :id and p.negocio.id = :negocioId and p.enCatalogo = true
            """)
    Optional<Producto> buscarDeNegocio(Long id, Long negocioId);

    /** Productos activos del catálogo para el carrusel del chat; quien llama filtra los que tienen sus tres fotos. */
    @Query("""
            select distinct p from Producto p left join fetch p.fotos
            where p.negocio.id = :negocioId and p.enCatalogo = true
              and p.estado = com.autoreel.catalogo.dominio.EstadoProducto.ACTIVO
            order by p.fechaCreacion desc
            """)
    List<Producto> listarParaCarrusel(Long negocioId);

    /** Producto elegido para un video, esté o no en el catálogo. */
    @Query("select p from Producto p left join fetch p.fotos where p.id = :id and p.negocio.id = :negocioId")
    Optional<Producto> buscarParaVideo(Long id, Long negocioId);
}
