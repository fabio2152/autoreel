package com.autoreel.negocio.dominio;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Perfil del negocio (sección 7.1). La marca (logo, colores y estilo) es común a todos los nichos; el resto de las
 * respuestas las define el paquete de nicho y se guardan como JSON.
 */
@Entity
@Table(name = "perfil_negocio")
public class PerfilNegocio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", updatable = false)
    private Negocio negocio;

    private String logoClave;

    @Column(nullable = false)
    private boolean logoSinTransparencia;

    private String colorPrimario;

    private String colorSecundario;

    private String estilo;

    private String fotoLocalClave;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> respuestas = new LinkedHashMap<>();

    @Column(nullable = false)
    private Instant fechaActualizacion;

    protected PerfilNegocio() {
    }

    public PerfilNegocio(Negocio negocio, Instant ahora) {
        this.negocio = negocio;
        this.fechaActualizacion = ahora;
    }

    public void cambiarLogo(String clave, boolean sinTransparencia) {
        this.logoClave = clave;
        this.logoSinTransparencia = clave != null && sinTransparencia;
    }

    public void cambiarColores(String primario, String secundario) {
        this.colorPrimario = primario;
        this.colorSecundario = secundario;
    }

    public void cambiarEstilo(String estilo) {
        this.estilo = estilo;
    }

    public void cambiarFotoLocal(String clave) {
        this.fotoLocalClave = clave;
    }

    public void cambiarRespuestas(Map<String, Object> respuestas) {
        this.respuestas = new LinkedHashMap<>(respuestas);
    }

    public void marcarActualizado(Instant ahora) {
        this.fechaActualizacion = ahora;
    }

    public Long getId() {
        return id;
    }

    public Negocio getNegocio() {
        return negocio;
    }

    public String getLogoClave() {
        return logoClave;
    }

    public boolean isLogoSinTransparencia() {
        return logoSinTransparencia;
    }

    public String getColorPrimario() {
        return colorPrimario;
    }

    public String getColorSecundario() {
        return colorSecundario;
    }

    public String getEstilo() {
        return estilo;
    }

    public String getFotoLocalClave() {
        return fotoLocalClave;
    }

    public Map<String, Object> getRespuestas() {
        return respuestas;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }
}
