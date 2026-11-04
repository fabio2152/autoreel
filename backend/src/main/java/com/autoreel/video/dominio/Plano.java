package com.autoreel.video.dominio;

import com.autoreel.catalogo.dominio.TipoFoto;
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

/**
 * Uno de los cuatro planos del video (sección 7.4). Los planos 1 a 3 tienen foto, movimiento y texto; el 4 (cierre)
 * tiene precio, llamado a la acción y ubicación, y lo arma el sistema.
 */
@Entity
@Table(name = "plano")
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", updatable = false)
    private Video video;

    @Column(nullable = false, updatable = false)
    private int orden;

    /** Foto del producto que usa; vacía en el cierre o si el usuario subió otra en el storyboard. */
    @Enumerated(EnumType.STRING)
    private TipoFoto tipoFoto;

    private String claveFoto;

    private String movimiento;

    private String texto;

    private String precio;

    private String llamado;

    private String ubicacion;

    protected Plano() {
    }

    static Plano conFoto(Video video, int orden, TipoFoto tipoFoto, String claveFoto, String movimiento,
            String texto) {
        Plano plano = new Plano();
        plano.video = video;
        plano.orden = orden;
        plano.tipoFoto = tipoFoto;
        plano.claveFoto = claveFoto;
        plano.movimiento = movimiento;
        plano.texto = texto;
        return plano;
    }

    static Plano cierre(Video video, int orden, String precio, String llamado, String ubicacion) {
        Plano plano = new Plano();
        plano.video = video;
        plano.orden = orden;
        plano.precio = precio;
        plano.llamado = llamado;
        plano.ubicacion = ubicacion;
        return plano;
    }

    public boolean esCierre() {
        return claveFoto == null;
    }

    public void cambiarTexto(String texto) {
        this.texto = texto;
    }

    public void cambiarFoto(TipoFoto tipoFoto, String claveFoto) {
        this.tipoFoto = tipoFoto;
        this.claveFoto = claveFoto;
    }

    public void cambiarPrecio(String precio) {
        this.precio = precio;
    }

    public Long getId() {
        return id;
    }

    public int getOrden() {
        return orden;
    }

    public TipoFoto getTipoFoto() {
        return tipoFoto;
    }

    public String getClaveFoto() {
        return claveFoto;
    }

    public String getMovimiento() {
        return movimiento;
    }

    public String getTexto() {
        return texto;
    }

    public String getPrecio() {
        return precio;
    }

    public String getLlamado() {
        return llamado;
    }

    public String getUbicacion() {
        return ubicacion;
    }
}
