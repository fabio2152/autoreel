package com.autoreel.facturacion.dominio;

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
 * RF-12: el negocio usa otro plan durante una cantidad de videos (RN-50 a RN-53, por usos desde el 2026-11-25). Un uso
 * es un video creado con el beneficio y aprobado; si ese video falla, el uso se devuelve. No cambia el monto ni la
 * fecha de pago; al gastar sus usos (o si el administrador lo termina), el negocio vuelve solo a su plan.
 */
@Entity
@Table(name = "beneficio_temporal")
public class BeneficioTemporal {

    /** RN-51. */
    public static final int USOS_MAXIMOS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long negocioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", updatable = false)
    private Plan plan;

    /** Solo en los beneficios por fechas, anteriores al 2026-11-25 (historial). */
    @Column(updatable = false)
    private Instant desde;

    /** Solo en los beneficios por fechas, anteriores al 2026-11-25 (historial). */
    @Column(updatable = false)
    private Instant hasta;

    @Column(nullable = false, updatable = false)
    private int usosOtorgados;

    @Column(nullable = false)
    private int usosConsumidos;

    @Column(nullable = false)
    private boolean terminado;

    @Column(nullable = false, updatable = false)
    private String motivo;

    @Column(nullable = false, updatable = false)
    private Long otorgadoPor;

    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    protected BeneficioTemporal() {
    }

    public BeneficioTemporal(Long negocioId, Plan plan, int usosOtorgados, String motivo, Long otorgadoPor,
            Instant ahora) {
        this.negocioId = negocioId;
        this.plan = plan;
        this.usosOtorgados = usosOtorgados;
        this.motivo = motivo;
        this.otorgadoPor = otorgadoPor;
        this.fechaCreacion = ahora;
    }

    public boolean vigente() {
        return !terminado && usosConsumidos < usosOtorgados;
    }

    public boolean agotado() {
        return !terminado && usosConsumidos >= usosOtorgados;
    }

    public int usosRestantes() {
        return vigente() ? usosOtorgados - usosConsumidos : 0;
    }

    /** Un video creado con el beneficio se aprobó. */
    public void consumirUso() {
        if (!vigente()) {
            throw new IllegalStateException("El beneficio " + id + " no tiene usos disponibles");
        }
        usosConsumidos++;
    }

    /** El video que usó el beneficio falló al generarse o al armarse: el uso vuelve (RN-27). */
    public void devolverUso() {
        if (usosConsumidos > 0) {
            usosConsumidos--;
        }
    }

    /** El administrador retira el beneficio: el negocio vuelve a su plan y el beneficio queda en el historial. */
    public void terminar() {
        this.terminado = true;
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

    public int getUsosOtorgados() {
        return usosOtorgados;
    }

    public int getUsosConsumidos() {
        return usosConsumidos;
    }

    public boolean isTerminado() {
        return terminado;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }
}
