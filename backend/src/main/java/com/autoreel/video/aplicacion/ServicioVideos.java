package com.autoreel.video.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos;
import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.catalogo.dominio.FotoProducto;
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
import com.autoreel.nicho.dominio.PlantillaVideo.PlanoPlantilla;
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
import org.springframework.transaction.annotation.Transactional;

/** Storyboard y lista de videos del negocio (RF-21, RF-22). Un video de otro negocio responde 404. */
@Service
public class ServicioVideos {

    private static final int MAXIMO_EN_LISTA = 50;

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
    private final Clock reloj;

    public ServicioVideos(VideoRepositorio videos, LectorDeDatosDelVideo lector, ServicioCatalogo catalogo,
            ServicioDeArchivos archivos, RegistroDeNichos nichos, NegocioRepositorio negocios,
            ServicioConsumo consumo, BibliotecaMusical musica, FabricaDeProveedores proveedores,
            AlmacenDeArchivos almacen, Clock reloj) {
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
        this.reloj = reloj;
    }

    /** "Mis videos": los más recientes primero. */
    @Transactional(readOnly = true)
    public List<VideoResumen> listar(Long negocioId) {
        PaqueteNicho paquete = negocios.findById(negocioId).map(negocio -> nichos.de(negocio.getNicho()))
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos tu negocio."));
        return videos.listarDeNegocio(negocioId, Limit.of(MAXIMO_EN_LISTA)).stream()
                .map(video -> {
                    Optional<Producto> producto = catalogo.elegidoParaVideo(negocioId, video.getProductoId());
                    String foto = producto.flatMap(p -> p.foto(TipoFoto.ENTERO)).map(catalogo::urlDeFoto).orElse(null);
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
     * Cambia el texto o la foto de un plano, o el precio del cierre, en el storyboard o en la revisión. V21: un texto
     * por encima del límite se rechaza con el máximo. Los textos que escribe el usuario no pasan por la RN-22.
     * En la revisión, un texto o un precio solo recomponen (RN-29, V25); una foto nueva regenera ese clip (8.4).
     */
    @Transactional
    public VideoVista editarPlano(Long negocioId, Long videoId, int orden, SolicitudPlano solicitud) {
        Video video = editable(negocioId, videoId);
        boolean enRevision = video.getEstado() == EstadoVideo.LISTO;
        // En la revisión, una foto nueva necesita un clip nuevo de IA: no hay respaldo sin IA.
        boolean conIa = enRevision && solicitud.foto() != null;
        if (conIa && !proveedores.videoDisponible(video.getModeloVideoUsado())) {
            throw iaNoDisponible();
        }
        if (conIa && video.getRegeneracionesUsadas() >= video.getPlanAplicado().getRegeneracionesPorVideo()) {
            throw sinRegeneraciones(video.getPlanAplicado());
        }
        LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
        PlantillaVideo plantilla = datos.paquete().plantillaVideo();
        Plano plano = video.plano(orden).orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("Ese plano no existe."));
        if (orden == plantilla.cierre().orden()) {
            editarCierre(plano, solicitud, plantilla, datos);
        } else {
            PlanoPlantilla dePlantilla = plantilla.plano(orden);
            if (solicitud.precio() != null || (solicitud.texto() == null && solicitud.foto() == null)) {
                throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "Envía el texto o la foto nueva del plano.");
            }
            if (solicitud.texto() != null) {
                plano.cambiarTexto(textoValido(solicitud.texto(), dePlantilla.maxPalabras(), datos));
            }
            if (solicitud.foto() != null) {
                cambiarFoto(negocioId, plano, solicitud.foto(), datos.producto());
            }
        }
        if (enRevision) {
            if (conIa) {
                // El clip anterior se conserva hasta que llegue el nuevo: si el nuevo falla, el video sigue armado.
                video.regenerarPlano(orden, video.getPlanAplicado().getRegeneracionesPorVideo(), true,
                        reloj.instant());
            } else {
                video.recomponer(reloj.instant());
            }
        }
        video.marcarActualizado(reloj.instant());
        return vista(video);
    }

    /** RF-22, RN-26 (V22): aprobar el storyboard consume el video del día y empieza la generación. */
    @Transactional
    public VideoVista aprobarStoryboard(Long negocioId, Long videoId) {
        Video video = conEstado(negocioId, videoId, EstadoVideo.STORYBOARD);
        // Sin el modelo de video disponible no se puede crear el video: no hay respaldo sin IA (2026-11-06).
        if (!proveedores.videoDisponible(video.getModeloVideoUsado())) {
            throw iaNoDisponible();
        }
        java.time.LocalDate dia = consumo.consumir(negocioId, video.getPlanAplicado().getVideosPorDia());
        LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
        Long pista = musica.elegir(datos.paquete().id(), datos.perfil().getEstilo(), video.getId())
                .map(com.autoreel.video.dominio.PistaMusical::getId).orElse(null);
        video.aprobarStoryboard(dia, pista, reloj.instant());
        return vista(video);
    }

    /** RF-24, RN-28 (V26): pedir otro clip para un plano; una regeneración que falla no se cobra. */
    @Transactional
    public VideoVista regenerar(Long negocioId, Long videoId, int orden) {
        Video video = conEstado(negocioId, videoId, EstadoVideo.LISTO);
        if (!proveedores.videoDisponible(video.getModeloVideoUsado())) {
            throw iaNoDisponible();
        }
        if (video.getRegeneracionesUsadas() >= video.getPlanAplicado().getRegeneracionesPorVideo()) {
            throw sinRegeneraciones(video.getPlanAplicado());
        }
        video.regenerarPlano(orden, video.getPlanAplicado().getRegeneracionesPorVideo(), true, reloj.instant());
        return vista(video);
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

    /** El clip que dejó de usarse se borra solo si el cambio se confirma. */
    private void borrarAlConfirmar(String clave) {
        if (clave == null) {
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            almacen.eliminar(clave);
                        } catch (RuntimeException e) {
                            org.slf4j.LoggerFactory.getLogger(ServicioVideos.class)
                                    .warn("No se pudo borrar el clip descartado {}", clave, e);
                        }
                    }
                });
    }

    private static ExcepcionDeNegocio iaNoDisponible() {
        return new ExcepcionDeNegocio(HttpStatus.CONFLICT, "IA_NO_DISPONIBLE",
                "Por ahora no podemos crear clips con IA. Inténtalo en un rato; mientras tanto puedes editar los textos.");
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

    private void editarCierre(Plano plano, SolicitudPlano solicitud, PlantillaVideo plantilla,
            LectorDeDatosDelVideo.DatosDelVideo datos) {
        if (solicitud.precio() == null || solicitud.texto() != null || solicitud.foto() != null) {
            throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "En el cierre solo se puede cambiar el precio.");
        }
        String precio = solicitud.precio().strip();
        if (precio.length() > plantilla.maximoCaracteresPrecio()) {
            throw new ExcepcionDeNegocio("PRECIO_MUY_LARGO", "El precio admite hasta "
                    + plantilla.maximoCaracteresPrecio() + " caracteres.");
        }
        exigirDibujable(precio);
        exigirSinProhibidas(precio, datos);
        plano.cambiarPrecio(precio.isEmpty() ? null : precio);
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

    private void cambiarFoto(Long negocioId, Plano plano, SolicitudPlano.FotoElegida foto, Producto producto) {
        if ((foto.tipo() == null) == (foto.clave() == null)) {
            throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "Elige una foto del producto o sube una nueva.");
        }
        if (foto.tipo() != null) {
            FotoProducto delProducto = producto.foto(foto.tipo()).orElseThrow(() -> new ExcepcionDeNegocio(
                    "FOTO_NO_DISPONIBLE", "El producto ya no tiene esa foto. Elige otra o sube una nueva."));
            plano.cambiarFoto(delProducto.getTipo(), delProducto.getClaveS3());
            return;
        }
        // Una foto nueva queda solo en este plano: no cambia el producto ni el catálogo.
        String clave = archivos.confirmar(negocioId, PropositoArchivo.FOTO_PRODUCTO, foto.clave()).clave();
        plano.cambiarFoto(null, clave);
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
        List<VideoVista.PlanoVista> planos = video.getPlanos().stream()
                .filter(plano -> plano.getOrden() != plantilla.cierre().orden())
                .map(plano -> {
                    PlanoPlantilla dePlantilla = plantilla.plano(plano.getOrden());
                    VideoVista.MovimientoVista movimiento = paquete.movimientos().buscar(plano.getMovimiento())
                            .map(m -> new VideoVista.MovimientoVista(m.id(), m.descripcion()))
                            .orElse(new VideoVista.MovimientoVista(plano.getMovimiento(), plano.getMovimiento()));
                    return new VideoVista.PlanoVista(plano.getOrden(), dePlantilla.nombre(), dePlantilla.segundos(),
                            new VideoVista.FotoVista(plano.getTipoFoto(), archivos.urlDeLectura(plano.getClaveFoto())),
                            movimiento, plano.getTexto(), dePlantilla.maxPalabras(), plano.getEstado(),
                            plano.isUsoRespaldo());
                })
                .toList();
        VideoVista.CierreVista cierre = video.plano(plantilla.cierre().orden())
                .map(plano -> cierre(plano, plantilla, datos.perfil(), datos.guion()))
                .orElse(null);
        List<VideoVista.FotoVista> fotos = Arrays.stream(TipoFoto.values())
                .flatMap(tipo -> producto.foto(tipo).stream())
                .map(foto -> new VideoVista.FotoVista(foto.getTipo(), catalogo.urlDeFoto(foto)))
                .toList();
        return new VideoVista(video.getId(), video.getEstado(), tipoVideo(paquete, video),
                new VideoVista.ProductoDelVideo(producto.getId(), producto.getNombre()),
                new VideoVista.PlanDelVideo(video.getPlanAplicado().getNombre(),
                        video.getPlanAplicado().getVideosPorDia(),
                        video.getPlanAplicado().getRegeneracionesPorVideo()),
                video.isGuionDeRespaldo() ? plantilla.avisoDeRespaldo() : null, planos, cierre,
                video.getDescripcionPublicacion(), video.getHashtags(), ValidadorDeGuion.MAXIMO_PUBLICACION, fotos,
                video.getFechaCreacion(), generacion(video));
    }

    private VideoVista.GeneracionVista generacion(Video video) {
        ServicioConsumo.ConsumoDeHoy hoy = consumo.deHoy(video.getNegocioId(),
                video.getPlanAplicado().getVideosPorDia());
        return new VideoVista.GeneracionVista(
                new VideoVista.ConsumoVista(hoy.usados(), hoy.porDia(), hoy.puedeAprobar()),
                archivos.urlDeLectura(video.getClaveFinal()), video.getRegeneracionesUsadas(),
                proveedores.videoDisponible(video.getModeloVideoUsado()), video.getAvisoRevision(),
                video.getEstado() == EstadoVideo.ERROR ? video.getMotivoError() : null);
    }

    private VideoVista.CierreVista cierre(Plano plano, PlantillaVideo plantilla, PerfilNegocio perfil,
            DatosDelGuion guion) {
        PlanoPlantilla dePlantilla = plantilla.cierre();
        String whatsapp = guion.canales().contains(CamposDelPerfil.CANAL_WHATSAPP) ? guion.whatsapp() : null;
        return new VideoVista.CierreVista(plano.getOrden(), dePlantilla.nombre(), dePlantilla.segundos(),
                plano.getPrecio(), plantilla.maximoCaracteresPrecio(), plano.getLlamado(), plano.getUbicacion(),
                whatsapp, archivos.urlDeLectura(perfil.getLogoClave()), perfil.getColorPrimario(),
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
