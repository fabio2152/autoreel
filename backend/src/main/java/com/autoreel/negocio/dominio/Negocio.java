package com.autoreel.negocio.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "negocio")
public class Negocio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    /** Identificador del paquete de nicho (por ejemplo, PASTELERIA). No cambia después de crear el negocio (RN-40). */
    @Column(nullable = false, updatable = false)
    private String nicho;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNegocio estado;

    @Column(nullable = false)
    private boolean onboardingCompleto;

    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    protected Negocio() {
    }

    public Negocio(String nombre, String nicho, Instant fechaCreacion) {
        this.nombre = nombre;
        this.nicho = nicho;
        this.estado = EstadoNegocio.ACTIVO;
        this.onboardingCompleto = false;
        this.fechaCreacion = fechaCreacion;
    }

    public void suspender() {
        this.estado = EstadoNegocio.SUSPENDIDO;
    }

    public void reactivar() {
        this.estado = EstadoNegocio.ACTIVO;
    }

    public boolean estaSuspendido() {
        return estado == EstadoNegocio.SUSPENDIDO;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNicho() {
        return nicho;
    }

    public EstadoNegocio getEstado() {
        return estado;
    }

    public boolean isOnboardingCompleto() {
        return onboardingCompleto;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }
}
