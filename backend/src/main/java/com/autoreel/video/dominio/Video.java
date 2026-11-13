package com.autoreel.video.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
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
import org.springframework.http.HttpStatus;

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

    /** Día de Lima en que se consumió el video del día (RN-26). */
    private LocalDate consumoFecha;

    @Column(nullable = false)
    private boolean consumoDevuelto;

    @Column(nullable = false)
    private int regeneracionesUsadas;

    @Column(nullable = false)
    private int intentosComposicion;

    /** Reclamo del procesador de videos (generación y composición). */
    private Instant trabajoReclamadoHasta;

    /** Inicio de la generación en curso (al aprobar o al regenerar), para el plazo total. */
    private Instant generacionIniciadaEn;

    private String claveFinal;

    /** Aviso para el usuario en la revisión, por ejemplo si no se pudo aplicar un cambio. */
    private String avisoRevision;

    private Long pistaMusicalId;

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

    /** Suma el costo de un clip (RN-32). */
    public void sumarCosto(BigDecimal costo) {
        this.costoEstimado = this.costoEstimado.add(costo);
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
        this.trabajoReclamadoHasta = null;
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

    /** RF-22: empieza la generación de los clips con la pista elegida; el consumo lo registra el servicio. */
    public void aprobarStoryboard(LocalDate dia, Long pistaMusicalId, Instant ahora) {
        exigirEstado(EstadoVideo.STORYBOARD);
        this.consumoFecha = dia;
        this.pistaMusicalId = pistaMusicalId;
        this.generacionIniciadaEn = ahora;
        this.estado = EstadoVideo.GENERANDO;
        planosConFoto().forEach(Plano::pendiente);
        this.fechaActualizacion = ahora;
    }

    /** Si el video falló después de aprobar el storyboard (sin clips o sin composición) y se puede reintentar. */
    public boolean reintentable() {
        return estado == EstadoVideo.ERROR && consumoFecha != null && !planosConFoto().isEmpty();
    }

    /**
     * Reintentar un video que falló al generarse: vuelve a consumir el video del día y pide solo los clips que
     * faltan; los que ya llegaron se conservan (no se vuelven a pagar).
     */
    public void reintentar(LocalDate dia, Instant ahora) {
        if (!reintentable()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "VIDEO_NO_REINTENTABLE",
                    "Este video no se puede reintentar. Crea otro desde el inicio.");
        }
        this.consumoFecha = dia;
        this.consumoDevuelto = false;
        this.motivoError = null;
        this.avisoRevision = null;
        this.generacionIniciadaEn = ahora;
        this.estado = EstadoVideo.GENERANDO;
        planosConFoto().stream().filter(plano -> !plano.terminado()).forEach(Plano::pendiente);
        this.fechaActualizacion = ahora;
    }

    /**
     * RN-28: pedir otro clip para un plano. Es la única forma de regenerar (botón o cambio de foto en la revisión).
     *
     * @param conservarClipAnterior false si cambió la foto: el clip anterior ya no corresponde.
     * @return la clave del clip descartado, para borrarlo; vacía si se conserva.
     */
    public String regenerarPlano(int orden, int regeneracionesPorVideo, boolean conservarClipAnterior, Instant ahora) {
        exigirEstado(EstadoVideo.LISTO);
        if (regeneracionesUsadas >= regeneracionesPorVideo) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "SIN_REGENERACIONES",
                    "Ya usaste las " + regeneracionesPorVideo + " regeneraciones de este video. Todavía puedes "
                            + "editar los textos.");
        }
        Plano plano = planoConFoto(orden);
        regeneracionesUsadas++;
        String descartado = plano.regenerar(conservarClipAnterior);
        this.avisoRevision = null;
        this.generacionIniciadaEn = ahora;
        this.estado = EstadoVideo.GENERANDO;
        this.fechaActualizacion = ahora;
        return descartado;
    }

    /** Una regeneración que no produjo clip no se cobra (decisión del usuario) y se avisa en la revisión. */
    public void regeneracionFallida(String aviso) {
        if (regeneracionesUsadas > 0) {
            regeneracionesUsadas--;
        }
        this.avisoRevision = aviso;
    }

    /** Si todos los planos con IA tienen su clip. */
    public boolean clipsTerminados() {
        return planosConFoto().stream().allMatch(Plano::terminado);
    }

    public void pasarAComponer(Instant ahora) {
        this.intentosComposicion = 0;
        this.estado = EstadoVideo.COMPONIENDO;
        this.fechaActualizacion = ahora;
    }

    /** RN-29: un cambio de texto o de precio en la revisión solo vuelve a componer. */
    public void recomponer(Instant ahora) {
        exigirEstado(EstadoVideo.LISTO);
        this.avisoRevision = null;
        pasarAComponer(ahora);
    }

    public void contarIntentoDeComposicion() {
        this.intentosComposicion++;
    }

    /** @return la clave del MP4 anterior, para borrarlo; vacía si no había. */
    public String composicionLista(String clave, Instant ahora) {
        String anterior = this.claveFinal;
        this.claveFinal = clave;
        this.estado = EstadoVideo.LISTO;
        this.trabajoReclamadoHasta = null;
        this.fechaActualizacion = ahora;
        return anterior;
    }

    /** Una recomposición falló: el video vuelve a la revisión con su MP4 anterior. */
    public void recomposicionFallida(String aviso, Instant ahora) {
        this.avisoRevision = aviso;
        this.estado = EstadoVideo.LISTO;
        this.trabajoReclamadoHasta = null;
        this.fechaActualizacion = ahora;
    }

    /** RN-27: marca el consumo como devuelto; false si ya se había devuelto o no hubo consumo. */
    public boolean devolverConsumo() {
        if (consumoFecha == null || consumoDevuelto) {
            return false;
        }
        this.consumoDevuelto = true;
        return true;
    }

    public void liberarTrabajo() {
        this.trabajoReclamadoHasta = null;
    }

    public void aprobar(Instant ahora) {
        exigirEstado(EstadoVideo.LISTO);
        this.estado = EstadoVideo.APROBADO;
        this.fechaActualizacion = ahora;
    }

    public List<Plano> planosConFoto() {
        return planos.stream().filter(plano -> !plano.esCierre()).toList();
    }

    public Plano planoConFoto(int orden) {
        return plano(orden).filter(plano -> !plano.esCierre())
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("Ese plano no existe."));
    }

    private void exigirEstado(EstadoVideo esperado) {
        if (estado != esperado) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "VIDEO_NO_EDITABLE", "Este video ya no se puede cambiar.");
        }
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

    public LocalDate getConsumoFecha() {
        return consumoFecha;
    }

    public boolean isConsumoDevuelto() {
        return consumoDevuelto;
    }

    public int getRegeneracionesUsadas() {
        return regeneracionesUsadas;
    }

    public int getIntentosComposicion() {
        return intentosComposicion;
    }

    public Instant getTrabajoReclamadoHasta() {
        return trabajoReclamadoHasta;
    }

    public Instant getGeneracionIniciadaEn() {
        return generacionIniciadaEn;
    }

    public String getClaveFinal() {
        return claveFinal;
    }

    public String getAvisoRevision() {
        return avisoRevision;
    }

    public Long getPistaMusicalId() {
        return pistaMusicalId;
    }
}
