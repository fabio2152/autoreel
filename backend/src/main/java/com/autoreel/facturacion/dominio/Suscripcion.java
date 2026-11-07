package com.autoreel.facturacion.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.autoreel.negocio.dominio.Negocio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Cada negocio tiene exactamente una suscripción (RN-41).
 */
@Entity
@Table(name = "suscripcion")
public class Suscripcion {

    public static final int DIAS_AVISO_POR_DEFECTO = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", updatable = false)
    private Negocio negocio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(nullable = false)
    private BigDecimal montoMensual;

    @Column(nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false)
    private LocalDate proximaFechaPago;

    @Column(nullable = false)
    private int diasAviso;

    /** RN-42: día del mes en que se paga (el del primer pago); se conserva al avanzar la fecha. */
    @Column(nullable = false)
    private int diaPago;

    protected Suscripcion() {
    }

    public Suscripcion(Negocio negocio, Plan plan, BigDecimal montoMensual, LocalDate fechaInicio,
            LocalDate fechaPrimerPago) {
        this.negocio = negocio;
        this.plan = plan;
        this.montoMensual = montoMensual;
        this.fechaInicio = fechaInicio;
        this.proximaFechaPago = fechaPrimerPago;
        this.diasAviso = DIAS_AVISO_POR_DEFECTO;
        this.diaPago = fechaPrimerPago.getDayOfMonth();
    }

    /**
     * RF-08 y RN-42: el pago cubre la fecha de pago actual y la próxima avanza un mes.
     *
     * @return la fecha de pago que cubrió.
     */
    public LocalDate avanzarFechaPago() {
        LocalDate cubierta = proximaFechaPago;
        proximaFechaPago = CalculadoraFechaPago.siguiente(cubierta, diaPago);
        return cubierta;
    }

    /** RN-49: el plan nuevo rige para los videos que se creen desde ahora. */
    public void cambiarPlan(Plan plan, BigDecimal montoMensual) {
        this.plan = plan;
        this.montoMensual = montoMensual;
    }

    public EstadoPago estadoPago(LocalDate hoy) {
        return CalculadoraEstadoPago.calcular(proximaFechaPago, diasAviso, hoy);
    }

    public Long getId() {
        return id;
    }

    public Negocio getNegocio() {
        return negocio;
    }

    public Plan getPlan() {
        return plan;
    }

    public BigDecimal getMontoMensual() {
        return montoMensual;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getProximaFechaPago() {
        return proximaFechaPago;
    }

    public int getDiasAviso() {
        return diasAviso;
    }

    public int getDiaPago() {
        return diaPago;
    }
}
