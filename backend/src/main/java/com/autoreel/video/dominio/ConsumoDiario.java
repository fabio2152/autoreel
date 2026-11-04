package com.autoreel.video.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Videos aprobados por un negocio en un día de Lima (RN-26). Los no usados no se acumulan. */
@Entity
@Table(name = "consumo_diario")
public class ConsumoDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long negocioId;

    @Column(nullable = false, updatable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private int videosGenerados;

    protected ConsumoDiario() {
    }

    public void sumar() {
        videosGenerados++;
    }

    /** RN-27: devuelve un video del día. Nunca baja de cero. */
    public void restar() {
        if (videosGenerados > 0) {
            videosGenerados--;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getNegocioId() {
        return negocioId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public int getVideosGenerados() {
        return videosGenerados;
    }
}
