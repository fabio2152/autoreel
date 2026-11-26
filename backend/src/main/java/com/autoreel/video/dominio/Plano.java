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
 * Un plano del video (sección 7.4, guion flexible del 2026-11-25). Las tomas (1 a 4, según la estructura que eligió
 * la IA) tienen foto, toma de cámara y texto; el cierre (el plano sin foto, después de la última toma) tiene precio o
 * frase (los propone la IA), llamado a la acción y ubicación, y no tiene clip.
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

    /** Foto del producto que usa; vacía en el cierre. */
    @Enumerated(EnumType.STRING)
    private TipoFoto tipoFoto;

    private String claveFoto;

    /** Movimiento del catálogo: solo en los videos anteriores al guion flexible (sin {@link #toma}). */
    private String movimiento;

    /** La toma de cámara en inglés que escribió la IA, para el modelo de video; vacía en el cierre. */
    private String toma;

    /** La toma en pocas palabras, en español, para el storyboard. */
    private String descripcionToma;

    @Column(nullable = false)
    private int segundos;

    /** Texto en pantalla; puede faltar en las tomas que no son el gancho. */
    private String texto;

    /** Lo que el negocio no quiere ver en la toma, en sus palabras; la IA reescribe la toma con esto. */
    private String correcciones;

    /** Si la toma actual ya se reescribió con las correcciones guardadas. */
    @Column(nullable = false)
    private boolean correccionesAplicadas;

    /** Lo que no debe aparecer según las correcciones, en inglés, para el prompt negativo del modelo de video. */
    private String negativosCorreccion;

    private String precio;

    /** Texto principal del cierre cuando no hay precio. */
    private String frase;

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

    /** Plano extendido: el clip base ya llegó y se está extendiendo; si la extensión falla, se conserva el base. */
    @Column(nullable = false)
    private boolean extensionEnCurso;

    /** Una regeneración pedida que todavía no terminó: si termina en respaldo, se devuelve. */
    @Column(nullable = false)
    private boolean regeneracionEnCurso;

    protected Plano() {
    }

    static Plano conFoto(Video video, int orden, int segundos, TipoFoto tipoFoto, String claveFoto, String toma,
            String descripcionToma, String texto) {
        Plano plano = new Plano();
        plano.video = video;
        plano.orden = orden;
        plano.segundos = segundos;
        plano.tipoFoto = tipoFoto;
        plano.claveFoto = claveFoto;
        plano.toma = toma;
        plano.descripcionToma = descripcionToma;
        plano.texto = texto;
        return plano;
    }

    static Plano cierre(Video video, int orden, int segundos, String precio, String frase, String llamado,
            String ubicacion) {
        Plano plano = new Plano();
        plano.video = video;
        plano.orden = orden;
        plano.segundos = segundos;
        plano.precio = precio;
        plano.frase = frase;
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

    /** Correcciones nuevas (o vacías para quitarlas): la toma se reescribirá con ellas antes del próximo clip. */
    public void cambiarCorrecciones(String correcciones) {
        if (java.util.Objects.equals(this.correcciones, correcciones)) {
            return;
        }
        this.correcciones = correcciones;
        this.correccionesAplicadas = false;
        if (correcciones == null) {
            // Sin correcciones no queda nada que evitar; la toma ya reescrita se conserva.
            this.negativosCorreccion = null;
        }
    }

    /** Si hay correcciones que la toma actual todavía no tiene en cuenta. */
    public boolean correccionesPendientes() {
        return correcciones != null && !correccionesAplicadas;
    }

    /**
     * La IA reescribió la toma con las correcciones: el próximo clip usa la toma nueva y evita sus negativos.
     *
     * @param negativos lo que no debe aparecer, en inglés; vacío si la IA no devolvió nada.
     */
    public void aplicarCorrecciones(String toma, String descripcionToma, String negativos) {
        this.toma = toma;
        this.descripcionToma = descripcionToma;
        this.negativosCorreccion = negativos;
        this.correccionesAplicadas = true;
    }

    public void cambiarPrecio(String precio) {
        this.precio = precio;
    }

    public void cambiarFrase(String frase) {
        this.frase = frase;
    }

    /** Lo que el cierre muestra en grande: el precio o, si no hay, la frase. */
    public String textoPrincipalDelCierre() {
        return precio != null ? precio : frase;
    }

    /** Al aprobar el storyboard: el clip queda por enviar. */
    void pendiente() {
        this.estado = EstadoPlano.PENDIENTE;
        this.extensionEnCurso = false;
        this.intentos = 0;
        this.idTareaProveedor = null;
        this.enviadoEn = null;
    }

    /** Pedir otro clip. El clip anterior se conserva hasta que llegue el nuevo: si el nuevo falla, sigue armado. */
    void regenerar() {
        pendiente();
        this.regeneracionEnCurso = true;
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
        this.extensionEnCurso = false;
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

    /** El clip base ya se guardó: se envía su extensión y el plano sigue generando. */
    public void extendiendo(String idTarea, Instant ahora) {
        this.extensionEnCurso = true;
        this.idTareaProveedor = idTarea;
        this.enviadoEn = ahora;
        this.estado = EstadoPlano.GENERANDO;
    }

    /** La extensión no llegó: el plano queda con su clip base (la composición congela el último cuadro). */
    public void extensionFallida() {
        this.extensionEnCurso = false;
        this.idTareaProveedor = null;
        this.estado = EstadoPlano.LISTO;
        this.regeneracionEnCurso = false;
    }

    public boolean isExtensionEnCurso() {
        return extensionEnCurso;
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

    public String getToma() {
        return toma;
    }

    public String getDescripcionToma() {
        return descripcionToma;
    }

    public int getSegundos() {
        return segundos;
    }

    public String getTexto() {
        return texto;
    }

    public String getCorrecciones() {
        return correcciones;
    }

    public String getNegativosCorreccion() {
        return negativosCorreccion;
    }

    public boolean isCorreccionesAplicadas() {
        return correccionesAplicadas;
    }

    public String getPrecio() {
        return precio;
    }

    public String getFrase() {
        return frase;
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
