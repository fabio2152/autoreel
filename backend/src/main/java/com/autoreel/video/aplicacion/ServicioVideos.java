package com.autoreel.video.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos;
import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.catalogo.dominio.Producto;
import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.ia.aplicacion.FabricaDeProveedores;
import com.autoreel.negocio.dominio.PerfilNegocio;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import com.autoreel.nicho.dominio.Opcion;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PlantillaVideo;
import com.autoreel.nicho.dominio.PlantillaVideo.Estructura;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.nicho.dominio.Texto;
import com.autoreel.video.api.SolicitudPlano;
import com.autoreel.video.api.VideoResumen;
import com.autoreel.video.api.VideoVista;
import com.autoreel.video.dominio.DatosDelGuion;
import com.autoreel.video.dominio.EstadoVideo;
import com.autoreel.video.dominio.MotivoRespaldoPlano;
import com.autoreel.video.dominio.Plano;
import com.autoreel.video.dominio.ValidadorDeGuion;
import com.autoreel.video.dominio.Video;
import com.autoreel.video.infraestructura.VideoRepositorio;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Storyboard y lista de videos del negocio (RF-21, RF-22). Un video de otro negocio responde 404. */
@Service
public class ServicioVideos {

    private static final int MAXIMO_EN_LISTA = 50;
    /** Igual a la columna {@code plano.correcciones}. */
    public static final int MAXIMO_CARACTERES_CORRECCIONES = 300;

    private final VideoRepositorio videos;
    private final LectorDeDatosDelVideo lector;
    private final ServicioCatalogo catalogo;
    private final ServicioDeArchivos archivos;
    private final RegistroDeNichos nichos;
    private final NegocioRepositorio negocios;
    private final ServicioConsumo consumo;
    private final BibliotecaMusical musica;
    private final FabricaDeProveedores proveedores;
    private final AlmacenDeArchivos almacen;
    private final CorrectorDeTomas corrector;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final TransactionTemplate lectura;

    public ServicioVideos(VideoRepositorio videos, LectorDeDatosDelVideo lector, ServicioCatalogo catalogo,
            ServicioDeArchivos archivos, RegistroDeNichos nichos, NegocioRepositorio negocios,
            ServicioConsumo consumo, BibliotecaMusical musica, FabricaDeProveedores proveedores,
            AlmacenDeArchivos almacen, CorrectorDeTomas corrector, Clock reloj,
            PlatformTransactionManager transacciones) {
        this.videos = videos;
        this.lector = lector;
        this.catalogo = catalogo;
        this.archivos = archivos;
        this.nichos = nichos;
        this.negocios = negocios;
        this.consumo = consumo;
        this.musica = musica;
        this.proveedores = proveedores;
        this.almacen = almacen;
        this.corrector = corrector;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** "Mis videos": los más recientes primero. */
    @Transactional(readOnly = true)
    public List<VideoResumen> listar(Long negocioId) {
        PaqueteNicho paquete = negocios.findById(negocioId).map(negocio -> nichos.de(negocio.getNicho()))
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos tu negocio."));
        return videos.listarDeNegocio(negocioId, Limit.of(MAXIMO_EN_LISTA)).stream()
                .map(video -> {
                    Optional<Producto> producto = catalogo.elegidoParaVideo(negocioId, video.getProductoId());
                    String foto = producto.flatMap(p -> p.foto(TipoFoto.FOTO_1)).map(catalogo::urlDeFoto).orElse(null);
                    return new VideoResumen(video.getId(), video.getEstado(), tipoVideo(paquete, video),
                            producto.map(Producto::getNombre).orElse("—"), foto, video.getFechaCreacion());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public VideoVista obtener(Long negocioId, Long videoId) {
        Video video = videos.buscarDeNegocio(videoId, negocioId).orElseThrow(ServicioVideos::noEncontrado);
        return vista(video);
    }

    /**
     * Cambia el texto o las correcciones de una toma, o el precio o la frase del cierre, en el storyboard o en la
     * revisión. V21: un texto por encima del límite se rechaza con el máximo. Los textos que escribe el usuario no pasan
     * por la RN-22. En la revisión, un texto, un precio o una frase solo recomponen (RN-29, V25); las correcciones solo
     * se guardan: se aplican al regenerar el plano o, en el storyboard, al crear el video.
     */
    @Transactional
    public VideoVista editarPlano(Long negocioId, Long videoId, int orden, SolicitudPlano solicitud) {
        Video video = editable(negocioId, videoId);
        boolean enRevision = video.getEstado() == EstadoVideo.LISTO;
        LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
        PlantillaVideo plantilla = datos.paquete().plantillaVideo();
        Plano plano = video.plano(orden).orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("Ese plano no existe."));
        if (plano.esCierre()) {
            editarCierre(plano, solicitud, plantilla, datos);
        } else {
            if (solicitud.precio() != null || solicitud.frase() != null
                    || (solicitud.texto() == null && solicitud.correcciones() == null)) {
                throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "Envía el texto o las correcciones del plano.");
            }
            if (solicitud.texto() != null) {
                plano.cambiarTexto(textoDelPlano(video, orden, solicitud.texto(), datos));
            }
            if (solicitud.correcciones() != null) {
                plano.cambiarCorrecciones(correccionesValidas(solicitud.correcciones(), datos));
            }
        }
        // Las correcciones no cambian nada visible hasta que se regenera el plano: no hace falta recomponer.
        boolean cambioVisible = plano.esCierre() || solicitud.texto() != null;
        if (enRevision && cambioVisible) {
            video.recomponer(reloj.instant());
        }
        video.marcarActualizado(reloj.instant());
        return vista(video);
    }

    /**
     * RF-22, RN-26 (V22): aprobar el storyboard consume el video del día (y el uso del beneficio, si el video se creó
     * con uno) y empieza la generación.
     */
    @Transactional
    public VideoVista aprobarStoryboard(Long negocioId, Long videoId) {
        Video video = conEstado(negocioId, videoId, EstadoVideo.STORYBOARD);
        // Sin el modelo de video disponible no se puede crear el video: no hay respaldo sin IA (2026-11-06).
        if (!proveedores.videoDisponible(video.getModeloVideoUsado())) {
            throw iaNoDisponible();
        }
        java.time.LocalDate dia = consumo.consumir(video);
        LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
        Long pista = musica.elegir(datos.paquete().id(), datos.perfil().getEstilo(), video.getId())
                .map(com.autoreel.video.dominio.PistaMusical::getId).orElse(null);
        video.aprobarStoryboard(dia, pista, reloj.instant());
        return vista(video);
    }

    /**
     * Reintentar un video que terminó en ERROR. Si falló al escribir su guion, vuelve a esperarlo sin consumir el video
     * del día. Si falló al generarse (por ejemplo, si el proveedor de IA falló), como el video del día se devolvió
     * (RN-27), el reintento lo vuelve a consumir, junto con el uso del beneficio si el video se creó con uno.
     */
    @Transactional
    public VideoVista reintentar(Long negocioId, Long videoId) {
        Video video = conEstado(negocioId, videoId, EstadoVideo.ERROR);
        if (!video.reintentable()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "VIDEO_NO_REINTENTABLE",
                    "Este video no se puede reintentar. Crea otro desde el inicio.");
        }
        if (video.falloEnElGuion()) {
            video.reintentarGuion(reloj.instant());
            return vista(video);
        }
        if (!proveedores.videoDisponible(video.getModeloVideoUsado())) {
            throw iaNoDisponible();
        }
        java.time.LocalDate dia = consumo.consumir(video);
        video.reintentar(dia, reloj.instant());
        return vista(video);
    }

    /**
     * RF-24, RN-28 (V26): pedir otro clip para un plano, desde su edición en la revisión. Si el plano tiene correcciones
     * que su toma todavía no tiene en cuenta, antes el modelo de texto la reescribe con ellas, fuera de toda
     * transacción; si no lo logra, responde CORRECCION_NO_DISPONIBLE sin gastar la regeneración. Una regeneración cuyo
     * clip falla tampoco se cobra.
     *
     * @param texto el texto en pantalla nuevo, si cambió en la misma edición; vacío si no.
     */
    public VideoVista regenerar(Long negocioId, Long videoId, int orden, String texto) {
        // Todo se valida antes de llamar al modelo de texto: un pedido que no puede regenerar no se paga.
        Optional<CorrectorDeTomas.Pedido> pedido = lectura.execute(estado -> {
            Video video = videos.buscarDeNegocio(videoId, negocioId).orElseThrow(ServicioVideos::noEncontrado);
            exigirRegenerable(video);
            Plano plano = video.planoConFoto(orden);
            LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
            if (texto != null) {
                textoDelPlano(video, orden, texto, datos);
            }
            return plano.correccionesPendientes()
                    ? Optional.of(CorrectorDeTomas.pedido(video, plano, datos.paquete()))
                    : Optional.<CorrectorDeTomas.Pedido>empty();
        });
        Optional<CorrectorDeTomas.TomaCorregida> corregida = pedido.map(datos -> corrector.corregir(datos)
                .orElseThrow(ServicioVideos::correccionNoDisponible));
        return transaccion.execute(estado -> {
            Video video = conEstado(negocioId, videoId, EstadoVideo.LISTO);
            exigirRegenerable(video);
            Plano plano = video.planoConFoto(orden);
            if (texto != null) {
                plano.cambiarTexto(textoDelPlano(video, orden, texto, lector.leer(video)));
            }
            // Si las correcciones cambiaron mientras tanto, se aplican al enviar el clip (ServicioGeneracion).
            corregida.filter(toma -> Objects.equals(plano.getCorrecciones(), pedido.get().correcciones()))
                    .ifPresent(toma -> plano.aplicarCorrecciones(toma.toma(), toma.descripcionToma(), toma.negativos()));
            video.regenerarPlano(orden, video.getPlanAplicado().getRegeneracionesPorVideo(), reloj.instant());
            return vista(video);
        });
    }

    private void exigirRegenerable(Video video) {
        if (video.getEstado() != EstadoVideo.LISTO) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "VIDEO_NO_EDITABLE", "Este video ya no se puede cambiar.");
        }
        if (!proveedores.videoDisponible(video.getModeloVideoUsado())) {
            throw iaNoDisponible();
        }
        if (video.getRegeneracionesUsadas() >= video.getPlanAplicado().getRegeneracionesPorVideo()) {
            throw sinRegeneraciones(video.getPlanAplicado());
        }
    }

    /** RF-24: el usuario aprueba su video (la publicación llega en la fase 6). */
    @Transactional
    public VideoVista aprobar(Long negocioId, Long videoId) {
        Video video = conEstado(negocioId, videoId, EstadoVideo.LISTO);
        video.aprobar(reloj.instant());
        return vista(video);
    }

    /** RF-25: enlace temporal para descargar el MP4 como archivo. */
    @Transactional(readOnly = true)
    public String descarga(Long negocioId, Long videoId) {
        Video video = videos.buscarDeNegocio(videoId, negocioId).orElseThrow(ServicioVideos::noEncontrado);
        boolean descargable = video.getClaveFinal() != null && switch (video.getEstado()) {
            case LISTO, APROBADO, PROGRAMADO, PUBLICADO -> true;
            default -> false;
        };
        if (!descargable) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "VIDEO_NO_DISPONIBLE",
                    "Tu video todavía no está listo para descargar.");
        }
        String producto = catalogo.elegidoParaVideo(negocioId, video.getProductoId()).map(Producto::getNombre)
                .orElse("video");
        String nombre = "autoreel-" + Texto.normalizar(producto).replace(' ', '-') + "-" + video.getId() + ".mp4";
        return almacen.urlDeDescargaComoAdjunto(video.getClaveFinal(), nombre, Duration.ofMinutes(15));
    }

    private Video conEstado(Long negocioId, Long videoId, EstadoVideo esperado) {
        Video video = videos.bloquearDeNegocio(videoId, negocioId).orElseThrow(ServicioVideos::noEncontrado);
        if (video.getEstado() != esperado) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "VIDEO_NO_EDITABLE",
                    "Este video ya no se puede cambiar.");
        }
        return video;
    }

    private static ExcepcionDeNegocio iaNoDisponible() {
        return new ExcepcionDeNegocio(HttpStatus.CONFLICT, "IA_NO_DISPONIBLE",
                "Por ahora no podemos crear clips con IA. Inténtalo en un rato; mientras tanto puedes editar los textos.");
    }

    private static ExcepcionDeNegocio correccionNoDisponible() {
        return new ExcepcionDeNegocio(HttpStatus.SERVICE_UNAVAILABLE, "CORRECCION_NO_DISPONIBLE",
                "No pudimos aplicar tus correcciones; inténtalo en un rato. No se usó ninguna regeneración.");
    }

    private static ExcepcionDeNegocio sinRegeneraciones(Plan plan) {
        return new ExcepcionDeNegocio(HttpStatus.CONFLICT, "SIN_REGENERACIONES", "Ya usaste las "
                + plan.getRegeneracionesPorVideo() + " regeneraciones de este video. Todavía puedes editar los textos.");
    }

    @Transactional
    public VideoVista descartar(Long negocioId, Long videoId) {
        Video video = editable(negocioId, videoId);
        video.descartar(reloj.instant());
        return vista(video);
    }

    private Video editable(Long negocioId, Long videoId) {
        Video video = videos.bloquearDeNegocio(videoId, negocioId).orElseThrow(ServicioVideos::noEncontrado);
        if (video.getEstado() != EstadoVideo.STORYBOARD && video.getEstado() != EstadoVideo.LISTO) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "VIDEO_NO_EDITABLE",
                    "Este video ya no se puede cambiar.");
        }
        return video;
    }

    /** En el cierre se cambian el precio y la frase; un texto vacío quita el que se envía. */
    private void editarCierre(Plano plano, SolicitudPlano solicitud, PlantillaVideo plantilla,
            LectorDeDatosDelVideo.DatosDelVideo datos) {
        if ((solicitud.precio() == null && solicitud.frase() == null) || solicitud.texto() != null
                || solicitud.correcciones() != null) {
            throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA",
                    "En el cierre solo se pueden cambiar el precio y la frase.");
        }
        int maximo = plantilla.maximoCaracteresPrecio();
        if (solicitud.precio() != null) {
            String precio = solicitud.precio().strip();
            if (precio.length() > maximo) {
                throw new ExcepcionDeNegocio("PRECIO_MUY_LARGO", "El precio admite hasta " + maximo + " caracteres.");
            }
            exigirDibujable(precio);
            exigirSinProhibidas(precio, datos);
            plano.cambiarPrecio(precio.isEmpty() ? null : precio);
        }
        if (solicitud.frase() != null) {
            String frase = solicitud.frase().strip();
            int maxPalabras = plantilla.maxPalabrasCierre();
            if (frase.length() > maximo || Texto.contarPalabras(frase) > maxPalabras) {
                throw new ExcepcionDeNegocio("FRASE_MUY_LARGA", "La frase admite hasta " + maxPalabras
                        + " palabras y " + maximo + " caracteres, para que se lea en el video.");
            }
            exigirDibujable(frase);
            exigirSinProhibidas(frase, datos);
            plano.cambiarFrase(frase.isEmpty() ? null : frase);
        }
    }

    private String textoValido(String texto, int maxPalabras, LectorDeDatosDelVideo.DatosDelVideo datos) {
        String limpio = texto.strip();
        if (limpio.isEmpty()) {
            throw new ExcepcionDeNegocio("TEXTO_VACIO", "Escribe el texto de este plano.");
        }
        if (limpio.length() > ValidadorDeGuion.MAXIMO_CARACTERES_TEXTO) {
            throw new ExcepcionDeNegocio("TEXTO_MUY_LARGO",
                    "Este plano admite hasta " + ValidadorDeGuion.MAXIMO_CARACTERES_TEXTO + " caracteres.");
        }
        if (Texto.contarPalabras(limpio) > maxPalabras) {
            throw new ExcepcionDeNegocio("TEXTO_MUY_LARGO",
                    "Este plano admite hasta " + maxPalabras + " palabras, para que se lea en el video.");
        }
        exigirDibujable(limpio);
        exigirSinProhibidas(limpio, datos);
        return limpio;
    }

    /** El texto de una toma: solo el gancho exige texto; en las demás, un texto vacío la deja sin texto. */
    private String textoDelPlano(Video video, int orden, String texto, LectorDeDatosDelVideo.DatosDelVideo datos) {
        if (orden > 1 && texto.isBlank()) {
            return null;
        }
        int maxPalabras = datos.paquete().plantillaVideo().estructuraOPrimera(video.getEstructura()).maxPalabras();
        return textoValido(texto, maxPalabras, datos);
    }

    /** Las correcciones en una línea (el usuario puede escribirlas con saltos de línea); vacías, se quitan. */
    private static String correccionesValidas(String correcciones, LectorDeDatosDelVideo.DatosDelVideo datos) {
        String limpio = correcciones.strip().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > MAXIMO_CARACTERES_CORRECCIONES) {
            throw new ExcepcionDeNegocio("CORRECCIONES_MUY_LARGAS",
                    "Las correcciones admiten hasta " + MAXIMO_CARACTERES_CORRECCIONES + " caracteres.");
        }
        exigirDibujable(limpio);
        exigirSinProhibidas(limpio, datos);
        return limpio;
    }

    private static void exigirDibujable(String texto) {
        if (Texto.tieneCaracteresNoDibujables(texto)) {
            throw new ExcepcionDeNegocio("CARACTER_NO_PERMITIDO",
                    "Quita los emojis, los saltos de línea y los símbolos especiales: no se pueden mostrar en el video.");
        }
    }

    private static void exigirSinProhibidas(String texto, LectorDeDatosDelVideo.DatosDelVideo datos) {
        ValidadorDeGuion.fraseProhibida(texto, datos.guion(), datos.paquete()).ifPresent(frase -> {
            throw new ExcepcionDeNegocio("PALABRA_PROHIBIDA",
                    "No podemos usar \"" + frase + "\" en tus videos. Escríbelo de otra forma.");
        });
    }

    private VideoVista vista(Video video) {
        LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
        PaqueteNicho paquete = datos.paquete();
        Producto producto = datos.producto();
        PlantillaVideo plantilla = paquete.plantillaVideo();
        Estructura estructura = plantilla.estructuraOPrimera(video.getEstructura());
        List<VideoVista.PlanoVista> planos = video.planosConFoto().stream()
                .map(plano -> {
                    VideoVista.MovimientoVista movimiento = plano.getDescripcionToma() != null
                            ? new VideoVista.MovimientoVista(null, plano.getDescripcionToma())
                            : paquete.movimientos().buscar(plano.getMovimiento())
                                    .map(m -> new VideoVista.MovimientoVista(m.id(), m.descripcion()))
                                    .orElse(new VideoVista.MovimientoVista(plano.getMovimiento(),
                                            plano.getMovimiento()));
                    return new VideoVista.PlanoVista(plano.getOrden(), PlantillaVideo.nombreDelPlano(plano.getOrden()),
                            plano.getSegundos(),
                            new VideoVista.FotoVista(plano.getTipoFoto(), archivos.urlDeLectura(plano.getClaveFoto())),
                            movimiento, plano.getTexto(), plano.getCorrecciones(), estructura.maxPalabras(),
                            plano.getEstado(),
                            plano.isUsoRespaldo());
                })
                .toList();
        VideoVista.CierreVista cierre = video.cierre()
                .map(plano -> cierre(plano, plantilla, datos.perfil(), datos.guion()))
                .orElse(null);
        VideoVista.ConceptoVista concepto = video.getEstructura() == null ? null : new VideoVista.ConceptoVista(
                plantilla.modoDeTomas(video.getModoTomas()).nombre(), estructura.nombre(),
                paquete.movimientos().creatividad(video.getCreatividad()).map(c -> c.nombre()).orElse(null),
                video.getIdea());
        return new VideoVista(video.getId(), video.getEstado(), tipoVideo(paquete, video),
                new VideoVista.ProductoDelVideo(producto.getId(), producto.getNombre()),
                new VideoVista.PlanDelVideo(video.getPlanAplicado().getNombre(),
                        video.getPlanAplicado().getVideosPorDia(),
                        video.getPlanAplicado().getRegeneracionesPorVideo()),
                concepto, planos, cierre,
                video.getDescripcionPublicacion(), video.getHashtags(), ValidadorDeGuion.MAXIMO_PUBLICACION,
                video.getFechaCreacion(), video.inicioDelGuion(), generacion(video));
    }

    private VideoVista.GeneracionVista generacion(Video video) {
        ServicioConsumo.ConsumoDeHoy hoy = consumo.deHoy(video.getNegocioId(),
                video.getPlanAplicado().getVideosPorDia());
        return new VideoVista.GeneracionVista(
                new VideoVista.ConsumoVista(hoy.usados(), hoy.porDia(), hoy.puedeAprobar()),
                archivos.urlDeLectura(video.getClaveFinal()), video.getRegeneracionesUsadas(),
                proveedores.videoDisponible(video.getModeloVideoUsado()), video.getAvisoRevision(),
                video.getEstado() == EstadoVideo.ERROR ? video.getMotivoError() : null, video.reintentable(),
                video.reintentable() && !video.falloEnElGuion());
    }

    private VideoVista.CierreVista cierre(Plano plano, PlantillaVideo plantilla, PerfilNegocio perfil,
            DatosDelGuion guion) {
        String whatsapp = guion.canales().contains(CamposDelPerfil.CANAL_WHATSAPP) ? guion.whatsapp() : null;
        return new VideoVista.CierreVista(plano.getOrden(), PlantillaVideo.NOMBRE_CIERRE, plano.getSegundos(),
                plano.getPrecio(), plantilla.maximoCaracteresPrecio(), plano.getFrase(), plantilla.maxPalabrasCierre(),
                plano.getLlamado(), plano.getUbicacion(),
                whatsapp, CamposDelPerfil.paginaWebDelCierre(perfil.getRespuestas()),
                archivos.urlDeLectura(perfil.getLogoClave()), perfil.getColorPrimario(),
                perfil.getColorSecundario(), archivos.urlDeLectura(perfil.getFotoLocalClave()));
    }

    private static String tipoVideo(PaqueteNicho paquete, Video video) {
        return paquete.guionDelChat().paso(RespuestasDelChat.TIPO_VIDEO).opcion(video.getTipoVideo())
                .map(Opcion::texto).orElse(video.getTipoVideo());
    }

    private static ExcepcionDeNegocio noEncontrado() {
        return ExcepcionDeNegocio.noEncontrado("No encontramos ese video.");
    }
}
