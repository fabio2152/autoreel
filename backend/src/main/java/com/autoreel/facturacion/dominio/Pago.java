package com.autoreel.facturacion.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Pago recibido y registrado por el administrador (RF-08). {@code periodoCubierto} es la fecha de pago que salda.
 */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suscripcion_id", updatable = false)
    private Suscripcion suscripcion;

    @Column(nullable = false, updatable = false)
    private LocalDate fechaPago;

    @Column(nullable = false, updatable = false)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MedioPago medio;

    @Column(nullable = false, updatable = false)
    private LocalDate periodoCubierto;

    @Column(updatable = false)
    private String nota;

    @Column(nullable = false, updatable = false)
    private Long registradoPor;

    @Column(nullable = false, updatable = false)
    private Instant fechaRegistro;

    protected Pago() {
    }

    public Pago(Suscripcion suscripcion, LocalDate fechaPago, BigDecimal monto, MedioPago medio,
            LocalDate periodoCubierto, String nota, Long registradoPor, Instant fechaRegistro) {
        this.suscripcion = suscripcion;
        this.fechaPago = fechaPago;
        this.monto = monto;
        this.medio = medio;
        this.periodoCubierto = periodoCubierto;
        this.nota = nota;
        this.registradoPor = registradoPor;
        this.fechaRegistro = fechaRegistro;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public MedioPago getMedio() {
        return medio;
    }

    public LocalDate getPeriodoCubierto() {
        return periodoCubierto;
    }

    public String getNota() {
        return nota;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }
}
