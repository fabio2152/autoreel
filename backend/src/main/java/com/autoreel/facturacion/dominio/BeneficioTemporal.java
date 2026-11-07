package com.autoreel.facturacion.dominio;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * RF-12: el negocio usa otro plan durante un período (RN-50 a RN-53). No cambia el monto ni la fecha de pago; al
 * vencer, el negocio vuelve solo a su plan.
 */
@Entity
@Table(name = "beneficio_temporal")
public class BeneficioTemporal {

    /** RN-51. */
    public static final Duration DURACION_MAXIMA = Duration.ofDays(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long negocioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", updatable = false)
    private Plan plan;

    @Column(nullable = false, updatable = false)
    private Instant desde;

    @Column(nullable = false)
    private Instant hasta;

    @Column(nullable = false, updatable = false)
    private String motivo;

    @Column(nullable = false, updatable = false)
    private Long otorgadoPor;

    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    protected BeneficioTemporal() {
    }

    public BeneficioTemporal(Long negocioId, Plan plan, Instant desde, Instant hasta, String motivo, Long otorgadoPor,
            Instant ahora) {
        this.negocioId = negocioId;
        this.plan = plan;
        this.desde = desde;
        this.hasta = hasta;
        this.motivo = motivo;
        this.otorgadoPor = otorgadoPor;
        this.fechaCreacion = ahora;
    }

    public boolean vigente(Instant ahora) {
        return !ahora.isBefore(desde) && ahora.isBefore(hasta);
    }

    public boolean pendiente(Instant ahora) {
        return ahora.isBefore(desde);
    }

    /** El administrador retira un beneficio vigente: termina ahora y queda en el historial. */
    public void terminar(Instant ahora) {
        if (vigente(ahora)) {
            this.hasta = ahora;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getNegocioId() {
        return negocioId;
    }

    public Plan getPlan() {
        return plan;
    }

    public Instant getDesde() {
        return desde;
    }

    public Instant getHasta() {
        return hasta;
    }

    public String getMotivo() {
        return motivo;
    }
}
