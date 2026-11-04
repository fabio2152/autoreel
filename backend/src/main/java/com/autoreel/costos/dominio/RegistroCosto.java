package com.autoreel.costos.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Costo de una llamada a un proveedor de IA (RN-32). Se guarda aunque la respuesta no sirva: el proveedor la cobra.
 * Guarda identificadores y no relaciones, para que el registro no dependa del ciclo de vida del video.
 */
@Entity
@Table(name = "registro_costo")
public class RegistroCosto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long negocioId;

    @Column(updatable = false)
    private Long videoId;

    @Column(nullable = false, updatable = false)
    private String proveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ConceptoCosto concepto;

    @Column(nullable = false, updatable = false)
    private BigDecimal cantidad;

    @Column(nullable = false, updatable = false)
    private BigDecimal costoUsd;

    @Column(nullable = false, updatable = false)
    private Instant fechaHora;

    protected RegistroCosto() {
    }

    public RegistroCosto(Long negocioId, Long videoId, String proveedor, ConceptoCosto concepto, BigDecimal cantidad,
            BigDecimal costoUsd, Instant fechaHora) {
        this.negocioId = negocioId;
        this.videoId = videoId;
        this.proveedor = proveedor;
        this.concepto = concepto;
        this.cantidad = cantidad;
        this.costoUsd = costoUsd;
        this.fechaHora = fechaHora;
    }

    public Long getId() {
        return id;
    }

    public Long getNegocioId() {
        return negocioId;
    }

    public Long getVideoId() {
        return videoId;
    }

    public String getProveedor() {
        return proveedor;
    }

    public ConceptoCosto getConcepto() {
        return concepto;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getCostoUsd() {
        return costoUsd;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }
}
