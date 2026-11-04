package com.autoreel.catalogo.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Producto del catálogo de un negocio (sección 7.2). Puede existir sin todas sus fotos, pero solo se usa en un
 * video cuando tiene las tres (RN-12).
 */
@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", updatable = false)
    private Negocio negocio;

    @Column(nullable = false)
    private String nombre;

    /** La única fuente de detalles del producto que la IA puede usar. */
    @Column(nullable = false)
    private String descripcion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> temporadas = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoProducto estado;

    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FotoProducto> fotos = new ArrayList<>();

    protected Producto() {
    }

    public Producto(Negocio negocio, String nombre, String descripcion, List<String> temporadas, Instant ahora) {
        this.negocio = negocio;
        this.estado = EstadoProducto.ACTIVO;
        this.fechaCreacion = ahora;
        cambiarDatos(nombre, descripcion, temporadas);
    }

    public void cambiarDatos(String nombre, String descripcion, List<String> temporadas) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.temporadas = new ArrayList<>(temporadas);
    }

    public void ponerFoto(TipoFoto tipo, String clave, int ancho, int alto) {
        foto(tipo).ifPresentOrElse(foto -> foto.reemplazar(clave, ancho, alto),
                () -> fotos.add(new FotoProducto(this, tipo, clave, ancho, alto)));
    }

    public void quitarFoto(TipoFoto tipo) {
        fotos.removeIf(foto -> foto.getTipo() == tipo);
    }

    public Optional<FotoProducto> foto(TipoFoto tipo) {
        return fotos.stream().filter(foto -> foto.getTipo() == tipo).findFirst();
    }

    /** RN-12: solo un producto con sus tres fotos puede usarse en un video. */
    public boolean estaCompleto() {
        EnumSet<TipoFoto> presentes = EnumSet.noneOf(TipoFoto.class);
        fotos.forEach(foto -> presentes.add(foto.getTipo()));
        return presentes.containsAll(EnumSet.allOf(TipoFoto.class));
    }

    public void archivar() {
        this.estado = EstadoProducto.ARCHIVADO;
    }

    public void reactivar() {
        this.estado = EstadoProducto.ACTIVO;
    }

    public Long getId() {
        return id;
    }

    public Negocio getNegocio() {
        return negocio;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public List<String> getTemporadas() {
        return temporadas;
    }

    public EstadoProducto getEstado() {
        return estado;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public List<FotoProducto> getFotos() {
        return fotos;
    }
}
