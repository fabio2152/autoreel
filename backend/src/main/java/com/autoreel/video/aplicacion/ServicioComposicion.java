package com.autoreel.video.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.almacenamiento.dominio.ArchivoAlmacenado;
import com.autoreel.negocio.dominio.PerfilNegocio;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PlantillaVideo;
import com.autoreel.video.dominio.ComponedorDeVideo;
import com.autoreel.video.dominio.ComponedorDeVideo.CierreAComponer;
import com.autoreel.video.dominio.ComponedorDeVideo.Composicion;
import com.autoreel.video.dominio.ComponedorDeVideo.PlanoAComponer;
import com.autoreel.video.dominio.EstadoPlano;
import com.autoreel.video.dominio.EstadoVideo;
import com.autoreel.video.dominio.Plano;
import com.autoreel.video.dominio.Video;
import com.autoreel.video.infraestructura.PistaMusicalRepositorio;
import com.autoreel.video.infraestructura.PreparadorDeFotos;
import com.autoreel.video.infraestructura.VideoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Compone el video final (sección 8.3) y lo deja listo para revisar. Si la primera composición falla dos veces, el
 * video pasa a ERROR y se devuelve el video del día (RN-27); si falla una recomposición (por un cambio en la
 * revisión), el video vuelve a la revisión con su MP4 anterior.
 */
@Service
public class ServicioComposicion {

    private static final Logger log = LoggerFactory.getLogger(ServicioComposicion.class);

    static final int INTENTOS = 2;
    private static final long TAMANO_MAXIMO = 200L * 1024 * 1024;
    static final String AVISO_RECOMPOSICION_FALLIDA =
            "No pudimos aplicar tu último cambio; tu video anterior sigue disponible. Inténtalo de nuevo.";
    static final String MOTIVO_ERROR = "No pudimos armar el video. Te devolvimos el video del día.";

    private final VideoRepositorio videos;
    private final LectorDeDatosDelVideo lector;
    private final PistaMusicalRepositorio pistas;
    private final ComponedorDeVideo componedor;
    private final PreparadorDeFotos preparador;
    private final AlmacenDeArchivos almacen;
    private final ServicioConsumo consumo;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final TransactionTemplate lectura;

    public ServicioComposicion(VideoRepositorio videos, LectorDeDatosDelVideo lector, PistaMusicalRepositorio pistas,
            ComponedorDeVideo componedor, PreparadorDeFotos preparador, AlmacenDeArchivos almacen,
            ServicioConsumo consumo, Clock reloj, PlatformTransactionManager transacciones) {
        this.videos = videos;
        this.lector = lector;
        this.pistas = pistas;
        this.componedor = componedor;
        this.preparador = preparador;
        this.almacen = almacen;
        this.consumo = consumo;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    public List<Long> pendientes(int maximo) {
        return lectura.execute(estado ->
                videos.enProceso(EstadoVideo.COMPONIENDO, reloj.instant(), Limit.of(maximo)));
    }

    public void procesar(Long videoId) {
        // El intento se cuenta al reclamar: un corte a mitad de la composición también cuenta.
        Boolean seguir = transaccion.execute(estado -> {
            Instant ahora = reloj.instant();
            if (videos.reclamarTrabajo(videoId, EstadoVideo.COMPONIENDO, ahora,
                    ahora.plus(ServicioGeneracion.RECLAMO)) != 1) {
                return false;
            }
            Video video = videos.bloquear(videoId).orElseThrow();
            if (video.getIntentosComposicion() >= INTENTOS) {
                fallar(video);
                return false;
            }
            video.contarIntentoDeComposicion();
            return true;
        });
        if (!Boolean.TRUE.equals(seguir)) {
            return;
        }
        try {
            Composicion composicion = lectura.execute(estado -> armar(videoId));
            // Las descargas pudieron tardar: el reclamo se renueva para que dure toda la ejecución de FFmpeg.
            transaccion.executeWithoutResult(estado -> videos.renovarReclamo(videoId, EstadoVideo.COMPONIENDO,
                    reloj.instant().plus(ServicioGeneracion.RECLAMO)));
            byte[] mp4 = componedor.componer(composicion);
            Long negocioId = lectura.execute(estado -> videos.findById(videoId).orElseThrow().getNegocioId());
            String clave = "negocios/" + negocioId + "/videos/" + videoId + "/final-" + UUID.randomUUID() + ".mp4";
            almacen.guardar(clave, mp4, "video/mp4");
            transaccion.executeWithoutResult(estado -> {
                Video video = videos.bloquear(videoId).orElseThrow();
                if (video.getEstado() != EstadoVideo.COMPONIENDO) {
                    almacen.eliminar(clave);
                    return;
                }
                borrarAlConfirmar(video.composicionLista(clave, reloj.instant()));
            });
        } catch (RuntimeException e) {
            log.error("No se pudo componer el video {}", videoId, e);
            transaccion.executeWithoutResult(estado -> videos.bloquear(videoId)
                    .filter(video -> video.getEstado() == EstadoVideo.COMPONIENDO)
                    .ifPresent(video -> {
                        if (video.getIntentosComposicion() >= INTENTOS) {
                            fallar(video);
                        } else {
                            video.liberarTrabajo();
                        }
                    }));
        }
    }

    private void fallar(Video video) {
        Instant ahora = reloj.instant();
        if (video.getClaveFinal() != null) {
            video.recomposicionFallida(AVISO_RECOMPOSICION_FALLIDA, ahora);
            return;
        }
        video.marcarError(MOTIVO_ERROR, ahora);
        if (video.devolverConsumo()) {
            consumo.devolver(video.getNegocioId());
        }
    }

    private Composicion armar(Long videoId) {
        Video video = videos.findById(videoId).orElseThrow();
        LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
        PaqueteNicho paquete = datos.paquete();
        PlantillaVideo plantilla = paquete.plantillaVideo();
        PerfilNegocio perfil = datos.perfil();

        List<PlanoAComponer> planos = new ArrayList<>();
        for (Plano plano : video.planosConFoto()) {
            int segundos = plantilla.plano(plano.getOrden()).segundos();
            boolean conClip = plano.getEstado() == EstadoPlano.LISTO && plano.getClaveClip() != null;
            byte[] clip = conClip ? leer(plano.getClaveClip()) : null;
            byte[] foto = conClip ? null : preparador.vertical(leer(plano.getClaveFoto()));
            planos.add(new PlanoAComponer(plano.getOrden(), segundos, clip, foto, plano.getTexto()));
        }

        Plano cierre = video.plano(plantilla.cierre().orden()).orElseThrow();
        boolean conFotoDelLocal = perfil.getFotoLocalClave() != null;
        byte[] fondo = conFotoDelLocal ? preparador.vertical(leer(perfil.getFotoLocalClave()))
                : preparador.degradado(perfil.getColorPrimario(), perfil.getColorSecundario());
        byte[] logo = perfil.getLogoClave() == null ? null : leer(perfil.getLogoClave());
        String whatsapp = datos.guion().canales().contains(CamposDelPerfil.CANAL_WHATSAPP)
                ? datos.guion().whatsapp() : null;
        CierreAComponer cierreAComponer = new CierreAComponer(plantilla.cierre().segundos(), fondo, conFotoDelLocal,
                logo, cierre.getPrecio(), cierre.getLlamado(), cierre.getUbicacion(), whatsapp);

        byte[] musica = video.getPistaMusicalId() == null ? null
                : pistas.findById(video.getPistaMusicalId()).map(pista -> leer(pista.getClave())).orElse(null);
        String estilo = paquete.admiteEstilo(perfil.getEstilo()) ? perfil.getEstilo()
                : paquete.estilos().getFirst().valor();
        return new Composicion(planos, cierreAComponer, musica, paquete.tipografia(estilo));
    }

    private byte[] leer(String clave) {
        return almacen.leer(clave, TAMANO_MAXIMO).map(ArchivoAlmacenado::contenido)
                .orElseThrow(() -> new IllegalStateException("No está el archivo " + clave));
    }

    private void borrarAlConfirmar(String clave) {
        if (clave == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    almacen.eliminar(clave);
                } catch (RuntimeException e) {
                    log.warn("No se pudo borrar el video anterior {}", clave, e);
                }
            }
        });
    }
}
