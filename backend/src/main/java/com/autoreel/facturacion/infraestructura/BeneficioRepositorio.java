package com.autoreel.facturacion.infraestructura;

import java.util.List;
import java.util.Optional;

import com.autoreel.facturacion.dominio.BeneficioTemporal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BeneficioRepositorio extends JpaRepository<BeneficioTemporal, Long> {

    /**
     * RN-50: los beneficios vigentes del negocio (sin terminar y con usos), el más antiguo primero, con el plan y sus
     * modelos ya cargados. Normalmente hay uno solo; puede haber dos si un video que falló le devuelve su uso a un
     * beneficio ya agotado después de otorgarse otro.
     */
    @Query("""
            select b from BeneficioTemporal b join fetch b.plan p join fetch p.modeloTexto join fetch p.modeloVideo
            where b.negocioId = :negocioId and b.terminado = false and b.usosConsumidos < b.usosOtorgados
            order by b.fechaCreacion, b.id""")
    List<BeneficioTemporal> vigentesDeNegocio(Long negocioId, Limit limite);

    default Optional<BeneficioTemporal> vigente(Long negocioId) {
        return vigentesDeNegocio(negocioId, Limit.of(1)).stream().findFirst();
    }

    /** Beneficios vigentes de todos los negocios (lista del panel). */
    @Query("""
            select b from BeneficioTemporal b join fetch b.plan p join fetch p.modeloTexto join fetch p.modeloVideo
            where b.terminado = false and b.usosConsumidos < b.usosOtorgados
            order by b.fechaCreacion, b.id""")
    List<BeneficioTemporal> vigentes();

    /** Bloquea el beneficio para descontar o devolver un uso de a uno: dos aprobaciones a la vez no gastan de más. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BeneficioTemporal b where b.id = :id")
    Optional<BeneficioTemporal> bloquear(Long id);

    @Query("""
            select b from BeneficioTemporal b join fetch b.plan p join fetch p.modeloVideo
            where b.negocioId = :negocioId order by b.fechaCreacion desc, b.id desc""")
    List<BeneficioTemporal> listarDeNegocio(Long negocioId, Limit limite);
}
