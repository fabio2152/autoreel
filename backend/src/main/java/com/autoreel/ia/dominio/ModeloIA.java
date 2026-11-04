package com.autoreel.ia.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Modelo de IA administrable (sección 11.5). El adaptador identifica la implementación en el código;
 * el identificador y los parámetros se leen de aquí, así que un modelo nuevo de un proveedor ya integrado
 * no requiere programar. Solo se guarda el nombre del secreto con la credencial, nunca la credencial (RN-55).
 */
@Entity
@Table(name = "modelo_ia")
public class ModeloIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String proveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoModelo tipo;

    @Column(nullable = false)
    private String adaptador;

    @Column(nullable = false)
    private String identificadorModelo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> parametros;

    @Column(nullable = false)
    private BigDecimal costoReferencial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnidadCosto unidadCosto;

    @Column(nullable = false)
    private String nombreSecreto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoModelo estado;

    private Instant ultimaPrueba;

    protected ModeloIA() {
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getProveedor() {
        return proveedor;
    }

    public TipoModelo getTipo() {
        return tipo;
    }

    public String getAdaptador() {
        return adaptador;
    }

    public String getIdentificadorModelo() {
        return identificadorModelo;
    }

    public Map<String, Object> getParametros() {
        return parametros;
    }

    public BigDecimal getCostoReferencial() {
        return costoReferencial;
    }

    public UnidadCosto getUnidadCosto() {
        return unidadCosto;
    }

    public String getNombreSecreto() {
        return nombreSecreto;
    }

    public EstadoModelo getEstado() {
        return estado;
    }

    public Instant getUltimaPrueba() {
        return ultimaPrueba;
    }
}
