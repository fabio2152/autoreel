package com.autoreel.facturacion.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.autoreel.facturacion.dominio.BeneficioTemporal;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BeneficioRepositorio extends JpaRepository<BeneficioTemporal, Long> {

    /** RN-50: el beneficio vigente del negocio, con el plan y sus modelos ya cargados. */
    @Query("""
            select b from BeneficioTemporal b join fetch b.plan p join fetch p.modeloTexto join fetch p.modeloVideo
            where b.negocioId = :negocioId and b.desde <= :ahora and b.hasta > :ahora""")
    Optional<BeneficioTemporal> vigente(Long negocioId, Instant ahora);

    /** Beneficios vigentes de todos los negocios (lista del panel). */
    @Query("""
            select b from BeneficioTemporal b join fetch b.plan p join fetch p.modeloTexto join fetch p.modeloVideo
            where b.desde <= :ahora and b.hasta > :ahora""")
    List<BeneficioTemporal> vigentes(Instant ahora);

    /** RN-51: beneficios que se cruzan con el período pedido. */
    @Query("""
            select count(b) > 0 from BeneficioTemporal b
            where b.negocioId = :negocioId and b.desde < :hasta and b.hasta > :desde""")
    boolean existeCruce(Long negocioId, Instant desde, Instant hasta);

    @Query("select b from BeneficioTemporal b join fetch b.plan where b.negocioId = :negocioId order by b.desde desc")
    List<BeneficioTemporal> listarDeNegocio(Long negocioId, Limit limite);
}
