package com.autoreel.video.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.ia.dominio.ModeloIA;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Video del día de un negocio (sección 9.1). Nace al completarse el chat con el plan y los modelos vigentes, que
 * guarda para que un cambio de plan no lo afecte (RN-54).
 */
@Entity
@Table(name = "video")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long negocioId;

    @Column(nullable = false, updatable = false)
    private Long conversacionId;

    @Column(nullable = false, updatable = false)
    private Long productoId;

    @Column(nullable = false, updatable = false)
    private String tipoVideo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoVideo estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_aplicado_id", updatable = false)
    private Plan planAplicado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modelo_texto_usado_id", updatable = false)
    private ModeloIA modeloTextoUsado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modelo_video_usado_id", updatable = false)
    private ModeloIA modeloVideoUsado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> opcionesProduccion;

    /** Última respuesta del modelo, solo para auditoría: los planos son la fuente de verdad. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> guion;

    private String descripcionPublicacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> hashtags = new ArrayList<>();

    @Column(nullable = false)
    private boolean guionDeRespaldo;

    @Enumerated(EnumType.STRING)
    private MotivoRespaldo motivoRespaldo;

    @Column(nullable = false)
    private int intentosGuion;

    @Column(nullable = false)
    private BigDecimal costoEstimado = BigDecimal.ZERO;

    private Instant guionReclamadoHasta;

    private String motivoError;

    @Version
    private int version;

    @Column(nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(nullable = false)
    private Instant fechaActualizacion;

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden")
    private List<Plano> planos = new ArrayList<>();

    protected Video() {
    }

    public Video(Long negocioId, Long conversacionId, Long productoId, String tipoVideo, Plan plan, Instant ahora) {
        this.negocioId = negocioId;
        this.conversacionId = conversacionId;
        this.productoId = productoId;
        this.tipoVideo = tipoVideo;
        this.estado = EstadoVideo.GUION_PENDIENTE;
        this.planAplicado = plan;
        this.modeloTextoUsado = plan.getModeloTexto();
        this.modeloVideoUsado = plan.getModeloVideo();
        this.opcionesProduccion = Map.copyOf(plan.getOpcionesProduccionPermitidas());
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    /** Suma una llamada al modelo de texto y su costo (RN-32). */
    public void registrarIntentoDeGuion(BigDecimal costo, Instant ahora) {
        this.intentosGuion++;
        this.costoEstimado = this.costoEstimado.add(costo);
        this.fechaActualizacion = ahora;
    }

    /** Agrega un plano con foto (1 a 3). */
    public void agregarPlano(int orden, TipoFoto tipoFoto, String claveFoto, String movimiento, String texto) {
        planos.add(Plano.conFoto(this, orden, tipoFoto, claveFoto, movimiento, texto));
    }

    /** Agrega el cierre, armado por el sistema. */
    public void agregarCierre(int orden, String precio, String llamado, String ubicacion) {
        planos.add(Plano.cierre(this, orden, precio, llamado, ubicacion));
    }

    /**
     * Guarda el guion y deja el video listo para revisar.
     *
     * @param motivoRespaldo vacío si el guion lo escribió la IA.
     */
    public void mostrarStoryboard(String descripcion, List<String> hashtags, Map<String, Object> guionDelModelo,
            MotivoRespaldo motivoRespaldo, Instant ahora) {
        this.descripcionPublicacion = descripcion;
        this.hashtags = new ArrayList<>(hashtags);
        this.guion = guionDelModelo;
        this.guionDeRespaldo = motivoRespaldo != null;
        this.motivoRespaldo = motivoRespaldo;
        this.guionReclamadoHasta = null;
        this.estado = EstadoVideo.STORYBOARD;
        this.fechaActualizacion = ahora;
    }

    public void marcarError(String motivo, Instant ahora) {
        this.motivoError = motivo.length() > 300 ? motivo.substring(0, 300) : motivo;
        this.guionReclamadoHasta = null;
        this.estado = EstadoVideo.ERROR;
        this.fechaActualizacion = ahora;
    }

    public void descartar(Instant ahora) {
        this.estado = EstadoVideo.DESCARTADO;
        this.fechaActualizacion = ahora;
    }

    public void marcarActualizado(Instant ahora) {
        this.fechaActualizacion = ahora;
    }

    public Optional<Plano> plano(int orden) {
        return planos.stream().filter(plano -> plano.getOrden() == orden).findFirst();
    }

    public Long getId() {
        return id;
    }

    public Long getNegocioId() {
        return negocioId;
    }

    public Long getConversacionId() {
        return conversacionId;
    }

    public Long getProductoId() {
        return productoId;
    }

    public String getTipoVideo() {
        return tipoVideo;
    }

    public EstadoVideo getEstado() {
        return estado;
    }

    public Plan getPlanAplicado() {
        return planAplicado;
    }

    public ModeloIA getModeloTextoUsado() {
        return modeloTextoUsado;
    }

    public ModeloIA getModeloVideoUsado() {
        return modeloVideoUsado;
    }

    public Map<String, Object> getOpcionesProduccion() {
        return opcionesProduccion;
    }

    public Map<String, Object> getGuion() {
        return guion;
    }

    public String getDescripcionPublicacion() {
        return descripcionPublicacion;
    }

    public List<String> getHashtags() {
        return hashtags;
    }

    public boolean isGuionDeRespaldo() {
        return guionDeRespaldo;
    }

    public MotivoRespaldo getMotivoRespaldo() {
        return motivoRespaldo;
    }

    public int getIntentosGuion() {
        return intentosGuion;
    }

    public BigDecimal getCostoEstimado() {
        return costoEstimado;
    }

    public Instant getGuionReclamadoHasta() {
        return guionReclamadoHasta;
    }

    public String getMotivoError() {
        return motivoError;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public List<Plano> getPlanos() {
        return planos;
    }
}
