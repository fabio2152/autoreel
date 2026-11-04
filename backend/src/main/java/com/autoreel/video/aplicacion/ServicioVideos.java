package com.autoreel.video.aplicacion;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.catalogo.dominio.FotoProducto;
import com.autoreel.catalogo.dominio.Producto;
import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
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
    private final Clock reloj;

    public ServicioVideos(VideoRepositorio videos, LectorDeDatosDelVideo lector, ServicioCatalogo catalogo,
            ServicioDeArchivos archivos, RegistroDeNichos nichos, NegocioRepositorio negocios, Clock reloj) {
        this.videos = videos;
        this.lector = lector;
        this.catalogo = catalogo;
        this.archivos = archivos;
        this.nichos = nichos;
        this.negocios = negocios;
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
     * Cambia el texto o la foto de un plano, o el precio del cierre. V21: un texto por encima del límite se rechaza
     * con el máximo. Los textos que escribe el usuario no pasan por la RN-22: son suyos.
     */
    @Transactional
    public VideoVista editarPlano(Long negocioId, Long videoId, int orden, SolicitudPlano solicitud) {
        Video video = editable(negocioId, videoId);
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
        video.marcarActualizado(reloj.instant());
        return vista(video);
    }

    @Transactional
    public VideoVista descartar(Long negocioId, Long videoId) {
        Video video = editable(negocioId, videoId);
        video.descartar(reloj.instant());
        return vista(video);
    }

    private Video editable(Long negocioId, Long videoId) {
        Video video = videos.bloquearDeNegocio(videoId, negocioId).orElseThrow(ServicioVideos::noEncontrado);
        if (video.getEstado() != EstadoVideo.STORYBOARD) {
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
                    "Quita los emojis y los saltos de línea: no se pueden mostrar en el video.");
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
                            movimiento, plano.getTexto(), dePlantilla.maxPalabras());
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
                        video.getPlanAplicado().getVideosPorDia()),
                video.isGuionDeRespaldo() ? plantilla.avisoDeRespaldo() : null, planos, cierre,
                video.getDescripcionPublicacion(), video.getHashtags(), ValidadorDeGuion.MAXIMO_PUBLICACION, fotos,
                video.getFechaCreacion());
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
