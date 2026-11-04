package com.autoreel.chat.dominio;

import java.time.Instant;

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

@Entity
@Table(name = "mensaje_chat")
public class MensajeChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversacion_id", updatable = false)
    private Conversacion conversacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AutorMensaje autor;

    @Column(nullable = false, updatable = false)
    private String texto;

    @Column(nullable = false, updatable = false)
    private Instant fechaHora;

    /** Un texto del usuario queda pendiente hasta que se agrupa en una respuesta (RN-17). */
    @Column(nullable = false)
    private boolean procesado;

    protected MensajeChat() {
    }

    MensajeChat(Conversacion conversacion, AutorMensaje autor, String texto, Instant fechaHora, boolean procesado) {
        this.conversacion = conversacion;
        this.autor = autor;
        this.texto = texto;
        this.fechaHora = fechaHora;
        this.procesado = procesado;
    }

    void marcarProcesado() {
        this.procesado = true;
    }

    public Long getId() {
        return id;
    }

    public AutorMensaje getAutor() {
        return autor;
    }

    public String getTexto() {
        return texto;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public boolean isProcesado() {
        return procesado;
    }
}
