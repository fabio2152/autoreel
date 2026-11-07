package com.autoreel.facturacion.dominio;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Aviso para el administrador. Se crea con un INSERT que ignora los repetidos (mismo tipo, negocio o modelo y
 * período), así que nunca se duplica (RN-44).
 */
@Entity
@Table(name = "notificacion_admin")
public class NotificacionAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoNotificacion tipo;

    @Column(updatable = false)
    private Long negocioId;

    @Column(name = "modelo_ia_id", updatable = false)
    private Long modeloIaId;

    @Column(nullable = false, updatable = false)
    private LocalDate periodo;

    @Column(nullable = false, updatable = false)
    private String mensaje;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Column(nullable = false)
    private boolean leida;

    protected NotificacionAdmin() {
    }

    public void marcarLeida() {
        this.leida = true;
    }

    public Long getId() {
        return id;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public Long getNegocioId() {
        return negocioId;
    }

    public LocalDate getPeriodo() {
        return periodo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Instant getFecha() {
        return fecha;
    }

    public boolean isLeida() {
        return leida;
    }
}
