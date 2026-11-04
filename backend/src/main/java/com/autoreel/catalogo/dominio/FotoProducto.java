package com.autoreel.catalogo.dominio;

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
@Table(name = "foto_producto")
public class FotoProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", updatable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoFoto tipo;

    @Column(name = "clave_s3", nullable = false)
    private String claveS3;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    protected FotoProducto() {
    }

    FotoProducto(Producto producto, TipoFoto tipo, String claveS3, int ancho, int alto) {
        this.producto = producto;
        this.tipo = tipo;
        reemplazar(claveS3, ancho, alto);
    }

    /** Se reemplaza en la misma fila: borrar e insertar chocaría con la restricción única (producto, tipo). */
    void reemplazar(String claveS3, int ancho, int alto) {
        this.claveS3 = claveS3;
        this.ancho = ancho;
        this.alto = alto;
    }

    public boolean esHorizontal() {
        return ancho > alto;
    }

    public Long getId() {
        return id;
    }

    public TipoFoto getTipo() {
        return tipo;
    }

    public String getClaveS3() {
        return claveS3;
    }

    public int getAncho() {
        return ancho;
    }

    public int getAlto() {
        return alto;
    }
}
