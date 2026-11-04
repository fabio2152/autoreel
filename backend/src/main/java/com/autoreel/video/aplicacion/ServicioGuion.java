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
import com.autoreel.video.dominio.GuionDeRespaldo;
import com.autoreel.video.dominio.GuionPropuesto;
import com.autoreel.video.dominio.MotivoRespaldo;
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
 * Escribe el guion de un video (sección 8.1, RN-21 a RN-25). Las llamadas al modelo van fuera de toda transacción;
 * cada paso que toca la base de datos usa una transacción corta.
 */
@Service
public class ServicioGuion {

    private static final Logger log = LoggerFactory.getLogger(ServicioGuion.class);

    /** RN-25: un intento y hasta dos reintentos. */
    static final int INTENTOS = 3;
    /** Después de este plazo no se empieza otro intento: se usa el respaldo. */
    static final Duration PLAZO_TOTAL = Duration.ofSeconds(60);
    /** Tiempo que el video queda tomado; si vence sin terminar, el siguiente proceso usa el respaldo. */
    static final Duration RECLAMO = Duration.ofMinutes(3);

    private record Reclamo(boolean obtenido, boolean interrumpido) {
    }

    private record Contexto(Long videoId, Long negocioId, ModeloIA modelo, PaqueteNicho paquete,
            DatosDelGuion datos) {
    }

    /** @param guionDelModelo última respuesta del modelo que se pudo leer, para auditoría. */
    private record Resultado(GuionPropuesto guion, MotivoRespaldo motivoRespaldo,
            Map<String, Object> guionDelModelo) {
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

    /** Videos que esperan su guion y nadie está procesando. */
    public List<Long> pendientes(int maximo) {
        return lectura.execute(estado -> videos.pendientesDeGuion(reloj.instant(), Limit.of(maximo)));
    }

    /**
     * Escribe el guion del video y lo deja en STORYBOARD. Si otro proceso lo tiene tomado, no hace nada. Si un
     * proceso anterior se cortó, usa el respaldo directamente para que el video nunca quede atascado.
     */
    public void procesar(Long videoId) {
        Reclamo reclamo = transaccion.execute(estado -> reclamar(videoId));
        if (reclamo == null || !reclamo.obtenido()) {
            return;
        }
        if (reclamo.interrumpido()) {
            try {
                Contexto contexto = lectura.execute(estado -> leer(videoId));
                finalizar(contexto, new Resultado(GuionDeRespaldo.armar(contexto.datos(), contexto.paquete()),
                        MotivoRespaldo.PROCESO_INTERRUMPIDO, null));
            } catch (RuntimeException e) {
                log.error("No se pudo armar el guion de respaldo del video {}", videoId, e);
                marcarError(videoId, "No se pudo preparar la propuesta: " + e.getMessage());
            }
            return;
        }
        Contexto contexto = lectura.execute(estado -> leer(videoId));
        finalizar(contexto, escribir(contexto));
    }

    private Reclamo reclamar(Long videoId) {
        Instant ahora = reloj.instant();
        boolean yaSeIntento = videos.reclamadoHasta(videoId).isPresent();
        boolean obtenido = videos.reclamar(videoId, ahora, ahora.plus(RECLAMO)) == 1;
        return new Reclamo(obtenido, obtenido && yaSeIntento);
    }

    private Resultado escribir(Contexto contexto) {
        ModeloIA modelo = contexto.modelo();
        Optional<Guionista> guionista = proveedores.guionista(modelo);
        if (guionista.isEmpty()) {
            aviso.avisar(modelo, ErrorDeProveedor.Tipo.CONFIGURACION_INVALIDA,
                    "El modelo no está activo, no es de texto o su adaptador no existe");
            return respaldo(contexto, MotivoRespaldo.MODELO_INACTIVO, null);
        }
        Optional<String> credencial = secretos.obtener(modelo.getNombreSecreto());
        if (credencial.isEmpty()) {
            aviso.avisar(modelo, ErrorDeProveedor.Tipo.CREDENCIAL_INVALIDA,
                    "No hay credencial configurada para el secreto " + modelo.getNombreSecreto());
            return respaldo(contexto, MotivoRespaldo.SIN_CREDENCIAL, null);
        }
        String esquema = InstruccionesDelGuion.esquema(contexto.datos(), contexto.paquete(), json);
        List<String> errores = List.of();
        Map<String, Object> ultimaLeida = null;
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
                return respaldo(contexto, motivo(e.getTipo()), ultimaLeida);
            } catch (RuntimeException e) {
                log.error("Falla inesperada del modelo de texto en el video {}", contexto.videoId(), e);
                aviso.avisar(modelo, ErrorDeProveedor.Tipo.NO_DISPONIBLE, "Falla inesperada: " + e.getMessage());
                return respaldo(contexto, MotivoRespaldo.PROVEEDOR_NO_DISPONIBLE, ultimaLeida);
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
            ultimaLeida = comoMapa(respuesta.texto());
            GuionPropuesto guion = leido.get().limpio();
            errores = ValidadorDeGuion.validar(guion, contexto.datos(), contexto.paquete());
            if (errores.isEmpty()) {
                return new Resultado(guion, null, ultimaLeida);
            }
            log.info("Guion inválido del video {} (intento {}): {}", contexto.videoId(), intento, errores);
        }
        return respaldo(contexto, MotivoRespaldo.GUION_INVALIDO, ultimaLeida);
    }

    private Resultado respaldo(Contexto contexto, MotivoRespaldo motivo, Map<String, Object> guionDelModelo) {
        return new Resultado(GuionDeRespaldo.armar(contexto.datos(), contexto.paquete()), motivo, guionDelModelo);
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
    private void finalizar(Contexto contexto, Resultado resultado) {
        transaccion.executeWithoutResult(estado -> {
            Video video = videos.bloquear(contexto.videoId()).orElseThrow();
            if (video.getEstado() != EstadoVideo.GUION_PENDIENTE) {
                return;
            }
            Instant ahora = reloj.instant();
            Optional<Producto> producto = catalogo.elegidoParaVideo(video.getNegocioId(), video.getProductoId());
            if (producto.isEmpty() || producto.get().getFotos().isEmpty()) {
                video.marcarError("El producto del video ya no tiene fotos.", ahora);
                return;
            }
            PlantillaVideo plantilla = contexto.paquete().plantillaVideo();
            for (PlanoPlantilla plano : plantilla.planosConIa()) {
                TipoFoto tipo = TipoFoto.valueOf(plano.foto());
                FotoProducto foto = producto.get().foto(tipo).orElseGet(() -> producto.get().getFotos().getFirst());
                GuionPropuesto.PlanoPropuesto escrito = resultado.guion().plano(plano.orden());
                video.agregarPlano(plano.orden(), foto.getTipo(), foto.getClaveS3(), escrito.movimiento(),
                        escrito.texto());
            }
            DatosDelGuion datos = contexto.datos();
            video.agregarCierre(plantilla.cierre().orden(), precioDelCierre(datos.precio(),
                            plantilla.maximoCaracteresPrecio()),
                    plantilla.llamadoPrincipal(datos.canales(), datos.distrito()).orElse(null), datos.distrito());
            video.mostrarStoryboard(resultado.guion().descripcion(), resultado.guion().hashtags(),
                    resultado.guionDelModelo(), resultado.motivoRespaldo(), ahora);
        });
    }

    private void marcarError(Long videoId, String motivo) {
        transaccion.executeWithoutResult(estado -> videos.bloquear(videoId)
                .filter(video -> video.getEstado() == EstadoVideo.GUION_PENDIENTE)
                .ifPresent(video -> video.marcarError(motivo, reloj.instant())));
    }

    /**
     * El precio tal como lo escribió el usuario (V19). Si pasa del máximo del cierre, se propone recortado en el
     * límite de una palabra; el usuario puede editarlo en el storyboard.
     */
    static String precioDelCierre(String precio, int maximo) {
        if (precio == null || precio.isBlank()) {
            return null;
        }
        String limpio = precio.strip();
        if (limpio.length() <= maximo) {
            return limpio;
        }
        String cortado = limpio.substring(0, maximo);
        int espacio = cortado.lastIndexOf(' ');
        String enPalabra = (espacio > 0 && !Character.isWhitespace(limpio.charAt(maximo))
                ? cortado.substring(0, espacio) : cortado).strip();
        // "S/ 65 la torta entera para compartir en" no se lee bien: se quitan los conectores del final.
        String sinConectores = GuionDeRespaldo.sinConectoresAlFinal(enPalabra);
        return sinConectores.isEmpty() ? enPalabra : sinConectores;
    }

    private Contexto leer(Long videoId) {
        Video video = videos.buscarConModeloDeTexto(videoId).orElseThrow();
        LectorDeDatosDelVideo.DatosDelVideo datos = lector.leer(video);
        return new Contexto(video.getId(), video.getNegocioId(), video.getModeloTextoUsado(), datos.paquete(),
                datos.guion());
    }

    private Map<String, Object> comoMapa(String respuesta) {
        try {
            return json.readValue(respuesta, new TypeReference<>() {
            });
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static MotivoRespaldo motivo(ErrorDeProveedor.Tipo tipo) {
        return switch (tipo) {
            case CREDENCIAL_INVALIDA -> MotivoRespaldo.CREDENCIAL_INVALIDA;
            case CONFIGURACION_INVALIDA -> MotivoRespaldo.CONFIGURACION_INVALIDA;
            case NO_DISPONIBLE -> MotivoRespaldo.PROVEEDOR_NO_DISPONIBLE;
        };
    }
}
