package com.autoreel.video.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pista de la biblioteca de música (secciones 7.4 y 9.1), por nicho y estilo. */
@Entity
@Table(name = "pista_musical")
public class PistaMusical {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String nicho;

    @Column(nullable = false)
    private String estilo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, updatable = false)
    private String clave;

    @Column(nullable = false)
    private int duracionSegundos;

    @Column(nullable = false)
    private String licencia;

    @Column(nullable = false)
    private boolean activa;

    protected PistaMusical() {
    }

    public PistaMusical(String nicho, String estilo, String nombre, String clave, int duracionSegundos,
            String licencia) {
        this.nicho = nicho;
        this.estilo = estilo;
        this.nombre = nombre;
        this.clave = clave;
        this.duracionSegundos = duracionSegundos;
        this.licencia = licencia;
        this.activa = true;
    }

    public Long getId() {
        return id;
    }

    public String getNicho() {
        return nicho;
    }

    public String getEstilo() {
        return estilo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getClave() {
        return clave;
    }

    public int getDuracionSegundos() {
        return duracionSegundos;
    }

    public String getLicencia() {
        return licencia;
    }

    public boolean isActiva() {
        return activa;
    }
}
