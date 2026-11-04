package com.autoreel.chat.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.autoreel.negocio.dominio.Negocio;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Conversación guiada que reúne la información del video del día (sección 7.3). Cada negocio tiene como máximo
 * una en curso (RN-19).
 */
@Entity
@Table(name = "conversacion")
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", updatable = false)
    private Negocio negocio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConversacion estado;

    /** Paso del guion que espera respuesta; vacío al terminar. */
    private String pasoActual;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> respuestas = new LinkedHashMap<>();

    @Column(nullable = false, updatable = false)
    private Instant fechaInicio;

    @Column(nullable = false)
    private Instant fechaActualizacion;

    @OneToMany(mappedBy = "conversacion", cascade = CascadeType.ALL)
    @OrderBy("fechaHora, id")
    private List<MensajeChat> mensajes = new ArrayList<>();

    protected Conversacion() {
    }

    public Conversacion(Negocio negocio, String primerPaso, Instant ahora) {
        this.negocio = negocio;
        this.estado = EstadoConversacion.EN_CURSO;
        this.pasoActual = primerPaso;
        this.fechaInicio = ahora;
        this.fechaActualizacion = ahora;
    }

    public void decirSistema(String texto, Instant ahora) {
        mensajes.add(new MensajeChat(this, AutorMensaje.SISTEMA, texto, ahora, true));
        this.fechaActualizacion = ahora;
    }

    /** Respuesta inmediata del usuario (botón o producto): no se agrupa. */
    public void decirUsuario(String texto, Instant ahora) {
        mensajes.add(new MensajeChat(this, AutorMensaje.USUARIO, texto, ahora, true));
        this.fechaActualizacion = ahora;
    }

    /** Texto libre del usuario: queda pendiente hasta que se agrupa (RN-17). */
    public void escribirUsuario(String texto, Instant ahora) {
        mensajes.add(new MensajeChat(this, AutorMensaje.USUARIO, texto, ahora, false));
        this.fechaActualizacion = ahora;
    }

    public List<MensajeChat> pendientes() {
        return mensajes.stream().filter(mensaje -> !mensaje.isProcesado()).toList();
    }

    public void marcarProcesados(List<MensajeChat> grupo) {
        grupo.forEach(MensajeChat::marcarProcesado);
    }

    public void responder(String paso, Object valor) {
        respuestas.put(paso, valor);
    }

    public void irAPaso(String paso) {
        this.pasoActual = paso;
    }

    public void completar(Instant ahora) {
        this.estado = EstadoConversacion.COMPLETA;
        this.pasoActual = null;
        this.fechaActualizacion = ahora;
    }

    public boolean enCurso() {
        return estado == EstadoConversacion.EN_CURSO;
    }

    public Long getId() {
        return id;
    }

    public Negocio getNegocio() {
        return negocio;
    }

    public EstadoConversacion getEstado() {
        return estado;
    }

    public String getPasoActual() {
        return pasoActual;
    }

    public Map<String, Object> getRespuestas() {
        return respuestas;
    }

    public Instant getFechaInicio() {
        return fechaInicio;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public List<MensajeChat> getMensajes() {
        return mensajes;
    }
}
