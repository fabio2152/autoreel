package com.autoreel.facturacion.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.autoreel.facturacion.dominio.NotificacionAdmin;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificacionRepositorio extends JpaRepository<NotificacionAdmin, Long> {

    /**
     * RN-44: crea la notificación solo si no existe otra del mismo tipo para el mismo negocio (o modelo) y período.
     *
     * @return 1 si la creó, 0 si ya existía.
     */
    @Modifying
    @Query(value = """
            insert into notificacion_admin (tipo, negocio_id, modelo_ia_id, periodo, mensaje, fecha, leida)
            values (:tipo, cast(:negocioId as bigint), cast(:modeloId as bigint), :periodo, :mensaje, :fecha, false)
            on conflict do nothing""", nativeQuery = true)
    int crearSiNoExiste(String tipo, Long negocioId, Long modeloId, LocalDate periodo, String mensaje, Instant fecha);

    @Query("select n from NotificacionAdmin n order by n.leida, n.fecha desc, n.id desc")
    List<NotificacionAdmin> recientes(Limit limite);

    long countByLeidaFalse();

    @Modifying
    @Query("update NotificacionAdmin n set n.leida = true where n.leida = false")
    int marcarTodasLeidas();

    /** Al registrar un pago, sus avisos de pago dejan de estar pendientes. */
    @Modifying
    @Query("""
            update NotificacionAdmin n set n.leida = true
            where n.negocioId = :negocioId and n.periodo <= :periodo and n.leida = false
              and n.tipo <> com.autoreel.facturacion.dominio.TipoNotificacion.INTEGRACION_CON_ERROR""")
    int marcarLeidasDePago(Long negocioId, LocalDate periodo);
}
