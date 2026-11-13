package com.autoreel.video.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.catalogo.dominio.FotoProducto;
import com.autoreel.catalogo.dominio.Producto;
import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.costos.aplicacion.ServicioCostos;
import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.ia.aplicacion.FabricaDeProveedores;
import com.autoreel.ia.dominio.AvisoDeIntegracion;
import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.Guionista;
import com.autoreel.ia.dominio.Guionista.RespuestaDelModelo;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PlantillaVideo;
import com.autoreel.nicho.dominio.PlantillaVideo.PlanoPlantilla;
import com.autoreel.video.dominio.DatosDelGuion;
import com.autoreel.video.dominio.EstadoVideo;
import com.autoreel.video.dominio.GuionPropuesto;
import com.autoreel.video.dominio.ValidadorDeGuion;
import com.autoreel.video.dominio.Video;
import com.autoreel.video.infraestructura.VideoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe el guion de un video con IA (sección 8.1, RN-21 a RN-25). No hay guion de respaldo (2026-11-12): si el
 * proveedor de texto falla, el video sigue esperando y se reintenta solo durante unos minutos; después queda en ERROR y
 * el usuario puede reintentarlo sin gastar su video del día. Las llamadas al modelo van fuera de toda transacción;
 * cada paso que toca la base de datos usa una transacción corta.
 */
@Service
public class ServicioGuion {

    private static final Logger log = LoggerFactory.getLogger(ServicioGuion.class);

    /** RN-25: un intento y hasta dos reintentos cuando la respuesta no pasa el validador. */
    static final int INTENTOS = 3;
    /** Después de este plazo no se empieza otro intento de la misma ronda. */
    static final Duration PLAZO_TOTAL = Duration.ofSeconds(60);
    /** Tiempo que el video queda tomado; si vence sin terminar (por ejemplo, un reinicio), se vuelve a intentar. */
    static final Duration RECLAMO = Duration.ofMinutes(3);
    /** Si el proveedor falla, espera antes del siguiente intento automático. */
    public static final Duration ESPERA_ENTRE_INTENTOS = Duration.ofSeconds(45);
    /** Plazo de los intentos automáticos, desde la creación del video o el último "Reintentar". */
    public static final Duration PLAZO_DE_REINTENTOS = Duration.ofMinutes(5);

    public static final String MOTIVO_SIN_IA = "No pudimos escribir tu guion con IA. Inténtalo de nuevo en un rato.";
    public static final String MOTIVO_GUION_INVALIDO =
            "No logramos escribir un guion que cumpla las reglas. Inténtalo de nuevo.";
    /** No se arregla reintentando: no se llama al modelo ni se ofrece "Reintentar". */
    public static final String MOTIVO_SIN_FOTOS =
            "El producto de este video ya no existe o no tiene fotos. Crea otro video desde el inicio.";
    /** Una falla nuestra (no del proveedor de IA): no se reintenta sola para no volver a pagar el guion. */
    public static final String MOTIVO_FALLA_INTERNA =
            "Tuvimos un problema al preparar tu guion. Inténtalo de nuevo.";

    private record Contexto(Long videoId, Long negocioId, ModeloIA modelo, PaqueteNicho paquete,
            DatosDelGuion datos) {
    }

    /** Lo que pasó al pedir el guion. */
    private sealed interface Resultado {
    }

    /** @param guionDelModelo la respuesta del modelo, para auditoría. */
    private record Escrito(GuionPropuesto guion, Map<String, Object> guionDelModelo) implements Resultado {
    }

    /** El proveedor no respondió (o no está configurado): se vuelve a intentar más tarde. */
    private record SinProveedor() implements Resultado {
    }

    /** Los intentos devolvieron guiones que no pasaron el validador. */
    private record Invalido() implements Resultado {
    }

    private final VideoRepositorio videos;
    private final LectorDeDatosDelVideo lector;
    private final ServicioCatalogo catalogo;
    private final FabricaDeProveedores proveedores;
    private final ProveedorDeSecretos secretos;
    private final AvisoDeIntegracion aviso;
    private final ServicioCostos costos;
    private final ObjectMapper json;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final TransactionTemplate lectura;

    public ServicioGuion(VideoRepositorio videos, LectorDeDatosDelVideo lector, ServicioCatalogo catalogo,
            FabricaDeProveedores proveedores, ProveedorDeSecretos secretos, AvisoDeIntegracion aviso,
            ServicioCostos costos, ObjectMapper json, Clock reloj, PlatformTransactionManager transacciones) {
        this.videos = videos;
        this.lector = lector;
        this.catalogo = catalogo;
        this.proveedores = proveedores;
        this.secretos = secretos;
        this.aviso = aviso;
        this.costos = costos;
        this.json = json;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** Videos que esperan su guion, que nadie está procesando y cuya espera entre intentos ya pasó. */
    public List<Long> pendientes(int maximo) {
        return lectura.execute(estado -> videos.pendientesDeGuion(reloj.instant(), Limit.of(maximo)));
    }

    /**
     * Escribe el guion del video y lo deja en STORYBOARD. Si otro proceso lo tiene tomado, no hace nada. Si el
     * proveedor falla, el video sigue esperando su guion y se reintenta después de una espera; vencido el plazo,
     * queda en ERROR.
     */
    public void procesar(Long videoId) {
        Boolean obtenido = transaccion.execute(estado -> {
            Instant ahora = reloj.instant();
            return videos.reclamar(videoId, ahora, ahora.plus(RECLAMO)) == 1;
        });
        if (!Boolean.TRUE.equals(obtenido)) {
            return;
        }
        try {
            Optional<Contexto> contexto = lectura.execute(estado -> leer(videoId));
            if (contexto.isEmpty()) {
                // Sin producto o sin fotos no hay video posible: no se paga el modelo ni se ofrece reintentar.
                marcarError(videoId, MOTIVO_SIN_FOTOS, true);
                return;
            }
            switch (escribir(contexto.get())) {
                case Escrito escrito -> finalizar(contexto.get(), escrito);
                case SinProveedor sinProveedor -> reintentarMasTarde(videoId);
                case Invalido invalido -> marcarError(videoId, MOTIVO_GUION_INVALIDO, false);
            }
        } catch (RuntimeException e) {
            // Una falla nuestra no deja el video atascado ni se reintenta sola (cada ronda pagaría otra vez el guion):
            // queda en ERROR y el usuario decide si lo vuelve a intentar.
            log.error("No se pudo escribir el guion del video {}", videoId, e);
            marcarError(videoId, MOTIVO_FALLA_INTERNA, false);
        }
    }

    private Resultado escribir(Contexto contexto) {
        ModeloIA modelo = contexto.modelo();
        Optional<Guionista> guionista = proveedores.guionista(modelo);
        if (guionista.isEmpty()) {
            aviso.avisar(modelo, ErrorDeProveedor.Tipo.CONFIGURACION_INVALIDA,
                    "El modelo no está activo, no es de texto o su adaptador no existe");
            return new SinProveedor();
        }
        Optional<String> credencial = secretos.obtener(modelo.getNombreSecreto());
        if (credencial.isEmpty()) {
            aviso.avisar(modelo, ErrorDeProveedor.Tipo.CREDENCIAL_INVALIDA,
                    "No hay credencial configurada para el secreto " + modelo.getNombreSecreto());
            return new SinProveedor();
        }
        String esquema = InstruccionesDelGuion.esquema(contexto.datos(), contexto.paquete(), json);
        List<String> errores = List.of();
        Instant inicio = reloj.instant();
        for (int intento = 1; intento <= INTENTOS; intento++) {
            if (intento > 1 && Duration.between(inicio, reloj.instant()).compareTo(PLAZO_TOTAL) > 0) {
                break;
            }
            String instrucciones = InstruccionesDelGuion.armar(contexto.datos(), contexto.paquete(), errores);
            RespuestaDelModelo respuesta;
            try {
                respuesta = guionista.get().escribir(modelo, credencial.get(), instrucciones, esquema);
            } catch (ErrorDeProveedor e) {
                aviso.avisar(modelo, e.getTipo(), e.getMessage());
                return new SinProveedor();
            } catch (RuntimeException e) {
                log.error("Falla inesperada del modelo de texto en el video {}", contexto.videoId(), e);
                aviso.avisar(modelo, ErrorDeProveedor.Tipo.NO_DISPONIBLE, "Falla inesperada: " + e.getMessage());
                return new SinProveedor();
            }
            // El proveedor cobra la respuesta aunque no sirva (RN-32).
            registrarCosto(contexto);
            if (respuesta.texto() == null) {
                errores = List.of(respuesta.problema());
                continue;
            }
            Optional<GuionPropuesto> leido = InstruccionesDelGuion.leer(respuesta.texto(), json);
            if (leido.isEmpty()) {
                errores = List.of("La respuesta no es un JSON con la forma pedida.");
                continue;
            }
            GuionPropuesto guion = leido.get().limpio();
            errores = ValidadorDeGuion.validar(guion, contexto.datos(), contexto.paquete());
            if (errores.isEmpty()) {
                return new Escrito(guion, comoMapa(respuesta.texto()));
            }
            log.info("Guion inválido del video {} (intento {}): {}", contexto.videoId(), intento, errores);
        }
        return new Invalido();
    }

    private void registrarCosto(Contexto contexto) {
        transaccion.executeWithoutResult(estado -> {
            Video video = videos.bloquear(contexto.videoId()).orElseThrow();
            BigDecimal costo = costos.registrarSolicitud(contexto.negocioId(), contexto.videoId(), contexto.modelo(),
                    ConceptoCosto.GUION);
            video.registrarIntentoDeGuion(costo, reloj.instant());
        });
    }

    /** Crea los planos y deja el video en STORYBOARD, si sigue esperando su guion. */
    private void finalizar(Contexto contexto, Escrito escrito) {
        transaccion.executeWithoutResult(estado -> {
            Video video = videos.bloquear(contexto.videoId()).orElseThrow();
            if (video.getEstado() != EstadoVideo.GUION_PENDIENTE) {
                return;
            }
            Instant ahora = reloj.instant();
            Optional<Producto> producto = catalogo.elegidoParaVideo(video.getNegocioId(), video.getProductoId());
            if (producto.isEmpty() || producto.get().getFotos().isEmpty()) {
                // Se quedó sin fotos mientras se escribía el guion: reintentar no lo arregla.
                video.marcarErrorDefinitivo(MOTIVO_SIN_FOTOS, ahora);
                return;
            }
            PlantillaVideo plantilla = contexto.paquete().plantillaVideo();
            for (PlanoPlantilla plano : plantilla.planosConIa()) {
                TipoFoto tipo = TipoFoto.valueOf(plano.foto());
                FotoProducto foto = producto.get().foto(tipo).orElseGet(() -> producto.get().getFotos().getFirst());
                GuionPropuesto.PlanoPropuesto propuesto = escrito.guion().plano(plano.orden());
                video.agregarPlano(plano.orden(), foto.getTipo(), foto.getClaveS3(), propuesto.movimiento(),
                        propuesto.texto());
            }
            DatosDelGuion datos = contexto.datos();
            GuionPropuesto.CierrePropuesto cierre = escrito.guion().cierre();
            String ubicacion = cierre.ubicacion() != null ? cierre.ubicacion() : datos.distrito();
            video.agregarCierre(plantilla.cierre().orden(), cierre.precio(), cierre.frase(),
                    plantilla.llamadoPrincipal(datos.canales(), datos.distrito()).orElse(null), ubicacion);
            video.mostrarStoryboard(escrito.guion().descripcion(), escrito.guion().hashtags(),
                    escrito.guionDelModelo(), ahora);
        });
    }

    /**
     * El proveedor no respondió: el video sigue esperando su guion y se vuelve a intentar después de la espera. Si el
     * siguiente intento ya caería fuera del plazo, queda en ERROR (no consumió el video del día).
     */
    private void reintentarMasTarde(Long videoId) {
        transaccion.executeWithoutResult(estado -> videos.bloquear(videoId)
                .filter(video -> video.getEstado() == EstadoVideo.GUION_PENDIENTE)
                .ifPresent(video -> {
                    Instant ahora = reloj.instant();
                    Instant siguiente = ahora.plus(ESPERA_ENTRE_INTENTOS);
                    if (siguiente.isAfter(video.inicioDelGuion().plus(PLAZO_DE_REINTENTOS))) {
                        video.marcarError(MOTIVO_SIN_IA, ahora);
                    } else {
                        video.esperarSiguienteIntentoDeGuion(siguiente, ahora);
                    }
                }));
    }

    /** @param definitivo si reintentar no lo arregla: el video no ofrece "Reintentar". */
    private void marcarError(Long videoId, String motivo, boolean definitivo) {
        transaccion.executeWithoutResult(estado -> videos.bloquear(videoId)
                .filter(video -> video.getEstado() == EstadoVideo.GUION_PENDIENTE)
                .ifPresent(video -> {
                    if (definitivo) {
                        video.marcarErrorDefinitivo(motivo, reloj.instant());
                    } else {
                        video.marcarError(motivo, reloj.instant());
                    }
                }));
    }

    /** Los datos del guion; vacío si el producto del video ya no existe o no tiene fotos. */
    private Optional<Contexto> leer(Long videoId) {
        Video video = videos.buscarConModeloDeTexto(videoId).orElseThrow();
        Optional<Producto> producto = catalogo.elegidoParaVideo(video.getNegocioId(), video.getProductoId());
        if (producto.isEmpty() || producto.get().getFotos().isEmpty()) {
            return Optional.empty();
        }
        LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
        return Optional.of(new Contexto(video.getId(), video.getNegocioId(), video.getModeloTextoUsado(),
                datos.paquete(), datos.guion()));
    }

    private Map<String, Object> comoMapa(String respuesta) {
        try {
            return json.readValue(respuesta, new TypeReference<>() {
            });
        } catch (RuntimeException e) {
            return null;
        }
    }
}
