package com.autoreel.facturacion.dominio;

import java.math.BigDecimal;
import java.util.Map;

import com.autoreel.ia.dominio.ModeloIA;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Plan de suscripción. Vive en la base de datos: crear un plan nuevo no requiere programar (RF-10).
 */
@Entity
@Table(name = "plan")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(nullable = false)
    private int videosPorDia;

    @Column(nullable = false)
    private int regeneracionesPorVideo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modelo_texto_id")
    private ModeloIA modeloTexto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modelo_video_id")
    private ModeloIA modeloVideo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> opcionesProduccionPermitidas;

    @Column(nullable = false)
    private boolean permitePersonas;

    private BigDecimal precioReferencial;

    @Column(nullable = false)
    private boolean activo;

    protected Plan() {
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public int getVideosPorDia() {
        return videosPorDia;
    }

    public int getRegeneracionesPorVideo() {
        return regeneracionesPorVideo;
    }

    public ModeloIA getModeloTexto() {
        return modeloTexto;
    }

    public ModeloIA getModeloVideo() {
        return modeloVideo;
    }

    public Map<String, Object> getOpcionesProduccionPermitidas() {
        return opcionesProduccionPermitidas;
    }

    public boolean isPermitePersonas() {
        return permitePersonas;
    }

    public BigDecimal getPrecioReferencial() {
        return precioReferencial;
    }

    public boolean isActivo() {
        return activo;
    }
}
