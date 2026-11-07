package com.autoreel.video.dominio;

import java.time.Instant;

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

    /** Estado del clip (fase 5); vacío en el storyboard y en el cierre. */
    @Enumerated(EnumType.STRING)
    private EstadoPlano estado;

    /** Envíos al proveedor del clip actual: con 2 fallidos, el plano usa el respaldo (RN-30). */
    @Column(nullable = false)
    private int intentos;

    @Column(nullable = false)
    private boolean usoRespaldo;

    @Enumerated(EnumType.STRING)
    private MotivoRespaldoPlano motivoRespaldo;

    private String idTareaProveedor;

    private Instant enviadoEn;

    private String claveClip;

    /** Una regeneración pedida que todavía no terminó: si termina en respaldo, se devuelve. */
    @Column(nullable = false)
    private boolean regeneracionEnCurso;

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

    /** Al aprobar el storyboard: el clip queda por enviar. */
    void pendiente() {
        this.estado = EstadoPlano.PENDIENTE;
        this.intentos = 0;
        this.idTareaProveedor = null;
        this.enviadoEn = null;
    }

    /**
     * Pedir otro clip. El clip anterior se conserva hasta que llegue el nuevo, salvo que la foto haya cambiado
     * (entonces ya no corresponde).
     *
     * @return la clave del clip descartado, para borrarlo; vacía si se conserva o no había.
     */
    String regenerar(boolean conservarClipAnterior) {
        pendiente();
        this.regeneracionEnCurso = true;
        if (conservarClipAnterior) {
            return null;
        }
        String descartado = this.claveClip;
        this.claveClip = null;
        this.usoRespaldo = false;
        this.motivoRespaldo = null;
        return descartado;
    }

    /** Antes de llamar al proveedor: cuenta el intento, para no reenviar sin límite si algo se corta. */
    public void prepararEnvio(Instant ahora) {
        this.intentos++;
        this.enviadoEn = ahora;
    }

    public void enviado(String idTarea) {
        this.idTareaProveedor = idTarea;
        this.estado = EstadoPlano.GENERANDO;
    }

    /** El intento no produjo clip: se vuelve a enviar en la siguiente pasada. */
    public void reintentar() {
        this.estado = EstadoPlano.PENDIENTE;
        this.idTareaProveedor = null;
    }

    /** @return la clave del clip anterior, para borrarlo; vacía si no había. */
    public String clipListo(String clave) {
        String anterior = this.claveClip;
        this.claveClip = clave;
        this.estado = EstadoPlano.LISTO;
        this.usoRespaldo = false;
        this.motivoRespaldo = null;
        this.idTareaProveedor = null;
        this.regeneracionEnCurso = false;
        return anterior;
    }

    /**
     * El clip nuevo no se pudo generar. Si era una regeneración, el plano conserva su clip anterior. No hay respaldo
     * sin IA (decisión del usuario, 2026-11-06): un plano sin ningún clip deja al video sin poder armarse.
     *
     * @return si conservó el clip anterior.
     */
    public boolean conservarClipAnterior() {
        if (!regeneracionEnCurso || claveClip == null) {
            return false;
        }
        this.regeneracionEnCurso = false;
        this.idTareaProveedor = null;
        this.estado = EstadoPlano.LISTO;
        return true;
    }

    public boolean terminado() {
        return estado == EstadoPlano.LISTO || estado == EstadoPlano.RESPALDO;
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

    public EstadoPlano getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public boolean isUsoRespaldo() {
        return usoRespaldo;
    }

    public MotivoRespaldoPlano getMotivoRespaldo() {
        return motivoRespaldo;
    }

    public String getIdTareaProveedor() {
        return idTareaProveedor;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public String getClaveClip() {
        return claveClip;
    }

    public boolean isRegeneracionEnCurso() {
        return regeneracionEnCurso;
    }
}
