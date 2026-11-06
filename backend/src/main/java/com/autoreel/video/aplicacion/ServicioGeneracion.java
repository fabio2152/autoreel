package com.autoreel.video.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.almacenamiento.dominio.ArchivoAlmacenado;
import com.autoreel.costos.aplicacion.ServicioCostos;
import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.ia.aplicacion.FabricaDeProveedores;
import com.autoreel.ia.dominio.AvisoDeIntegracion;
import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.GeneradorDeClips;
import com.autoreel.ia.dominio.GeneradorDeClips.EnCurso;
import com.autoreel.ia.dominio.GeneradorDeClips.EstadoDeClip;
import com.autoreel.ia.dominio.GeneradorDeClips.Fallido;
import com.autoreel.ia.dominio.GeneradorDeClips.Listo;
import com.autoreel.ia.dominio.GeneradorDeClips.SolicitudDeClip;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import com.autoreel.nicho.dominio.Movimientos;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import com.autoreel.video.dominio.EstadoPlano;
import com.autoreel.video.dominio.EstadoVideo;
import com.autoreel.video.dominio.MotivoRespaldoPlano;
import com.autoreel.video.dominio.Plano;
import com.autoreel.video.dominio.Video;
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
 * Genera los clips de un video (sección 8.2) y aplica el plan de respaldo (sección 8.5, RN-30). Las llamadas al
 * proveedor van fuera de toda transacción; cada cambio de un plano se guarda en su propia transacción corta, así un
 * corte nunca deja una tarea enviada sin registrar ni un plano reenviado sin límite.
 */
@Service
public class ServicioGeneracion {

    private static final Logger log = LoggerFactory.getLogger(ServicioGeneracion.class);

    /** Tiempo que el procesador tiene tomado un video. */
    static final Duration RECLAMO = Duration.ofMinutes(5);
    /** Sin el clip a los 10 minutos de enviarlo, el plano usa el respaldo (8.5). */
    static final Duration ESPERA_POR_CLIP = Duration.ofMinutes(10);
    /** Red de seguridad para un plano que quedó pendiente después de un envío. */
    static final Duration PLAZO_TOTAL = Duration.ofMinutes(20);
    /** RN-30: un plano que falla dos veces usa el respaldo. */
    static final int INTENTOS_POR_CLIP = 2;
    private static final int SEGUNDOS_POR_DEFECTO = 4;
    private static final long TAMANO_MAXIMO = 200L * 1024 * 1024;

    private record Trabajo(Long videoId, Long negocioId, ModeloIA modelo, Movimientos movimientos,
            List<PlanoATrabajar> planos) {
    }

    private record PlanoATrabajar(int orden, EstadoPlano estado, int intentos, String idTarea, Instant enviadoEn,
            String claveFoto, String movimiento) {
    }

    private final VideoRepositorio videos;
    private final NegocioRepositorio negocios;
    private final RegistroDeNichos nichos;
    private final FabricaDeProveedores proveedores;
    private final ProveedorDeSecretos secretos;
    private final AvisoDeIntegracion aviso;
    private final ServicioCostos costos;
    private final AlmacenDeArchivos almacen;
    private final PreparadorDeFotos preparador;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final TransactionTemplate lectura;

    public ServicioGeneracion(VideoRepositorio videos, NegocioRepositorio negocios, RegistroDeNichos nichos,
            FabricaDeProveedores proveedores, ProveedorDeSecretos secretos, AvisoDeIntegracion aviso,
            ServicioCostos costos, AlmacenDeArchivos almacen, PreparadorDeFotos preparador, Clock reloj,
            PlatformTransactionManager transacciones) {
        this.videos = videos;
        this.negocios = negocios;
        this.nichos = nichos;
        this.proveedores = proveedores;
        this.secretos = secretos;
        this.aviso = aviso;
        this.costos = costos;
        this.almacen = almacen;
        this.preparador = preparador;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** Videos generando sus clips que nadie está procesando. */
    public List<Long> pendientes(int maximo) {
        return lectura.execute(estado -> videos.enProceso(EstadoVideo.GENERANDO, reloj.instant(), Limit.of(maximo)));
    }

    /** Una pasada sobre los planos del video: envía, consulta o usa el respaldo; con todos listos, a componer. */
    public void procesar(Long videoId) {
        Instant ahora = reloj.instant();
        Boolean obtenido = transaccion.execute(estado ->
                videos.reclamarTrabajo(videoId, EstadoVideo.GENERANDO, ahora, ahora.plus(RECLAMO)) == 1);
        if (!Boolean.TRUE.equals(obtenido)) {
            return;
        }
        try {
            Trabajo trabajo = lectura.execute(estado -> leer(videoId));
            Optional<GeneradorDeClips> generador = proveedores.generadorDeClips(trabajo.modelo());
            Optional<String> credencial = secretos.obtener(trabajo.modelo().getNombreSecreto());
            boolean avisado = false;
            for (PlanoATrabajar plano : trabajo.planos()) {
                if (plano.estado() == EstadoPlano.LISTO || plano.estado() == EstadoPlano.RESPALDO) {
                    continue;
                }
                try {
                    if (plazoVencido(plano)) {
                        respaldo(trabajo, plano, MotivoRespaldoPlano.TIEMPO_AGOTADO, false);
                    } else if (generador.isEmpty() || credencial.isEmpty()) {
                        MotivoRespaldoPlano motivo = generador.isEmpty() ? MotivoRespaldoPlano.MODELO_INACTIVO
                                : MotivoRespaldoPlano.SIN_CREDENCIAL;
                        if (!avisado) {
                            aviso.avisar(trabajo.modelo(), ErrorDeProveedor.Tipo.CONFIGURACION_INVALIDA,
                                    generador.isEmpty() ? "El modelo de video no está activo o no tiene adaptador"
                                            : "No hay credencial configurada para " + trabajo.modelo().getNombreSecreto());
                            avisado = true;
                        }
                        respaldo(trabajo, plano, motivo, plano.estado() == EstadoPlano.GENERANDO);
                    } else if (plano.estado() == EstadoPlano.PENDIENTE) {
                        avisado |= enviar(trabajo, plano, generador.get(), credencial.get());
                    } else {
                        avisado |= consultar(trabajo, plano, generador.get(), credencial.get());
                    }
                } catch (RuntimeException e) {
                    log.error("Falla al generar el plano {} del video {}", plano.orden(), videoId, e);
                    contarFalla(trabajo, plano);
                }
            }
        } finally {
            transaccion.executeWithoutResult(estado -> videos.bloquear(videoId).ifPresent(video -> {
                if (video.getEstado() == EstadoVideo.GENERANDO && video.clipsTerminados()) {
                    video.pasarAComponer(reloj.instant());
                }
                video.liberarTrabajo();
            }));
        }
    }

    /**
     * Red de seguridad para un plano que ya se intentó enviar y quedó pendiente: pasado el plazo total desde su último
     * envío, usa el respaldo. No cuenta la espera en la cola (con un proveedor síncrono, un video puede esperar a que
     * terminen otros) ni los planos enviados: esos se consultan primero y tienen su propia espera de 10 minutos.
     */
    private boolean plazoVencido(PlanoATrabajar plano) {
        return plano.estado() == EstadoPlano.PENDIENTE && plano.intentos() > 0 && plano.enviadoEn() != null
                && reloj.instant().isAfter(plano.enviadoEn().plus(PLAZO_TOTAL));
    }

    /** @return si se avisó al administrador. */
    private boolean enviar(Trabajo trabajo, PlanoATrabajar plano, GeneradorDeClips generador, String credencial) {
        if (plano.intentos() >= INTENTOS_POR_CLIP) {
            respaldo(trabajo, plano, MotivoRespaldoPlano.CLIP_FALLIDO, false);
            return false;
        }
        byte[] foto = preparador.vertical(leerArchivo(plano.claveFoto()));
        Movimientos.Movimiento movimiento = trabajo.movimientos().buscar(plano.movimiento())
                .orElseThrow(() -> new IllegalStateException("Movimiento desconocido: " + plano.movimiento()));
        int segundos = segundosDelClip(trabajo.modelo());
        // Se cuenta el intento antes de llamar: si algo se corta después, no se reenvía sin límite.
        conPlano(trabajo, plano.orden(), (video, entidad) -> entidad.prepararEnvio(reloj.instant()));
        try {
            String idTarea = generador.iniciar(trabajo.modelo(), credencial, new SolicitudDeClip(foto, "image/jpeg",
                    movimiento.instruccion(), trabajo.movimientos().prohibiciones(), segundos));
            conPlano(trabajo, plano.orden(), (video, entidad) -> entidad.enviado(idTarea));
            return false;
        } catch (ErrorDeProveedor e) {
            aviso.avisar(trabajo.modelo(), e.getTipo(), e.getMessage());
            if (e.getTipo() == ErrorDeProveedor.Tipo.NO_DISPONIBLE) {
                // Cuenta como intento: con dos, respaldo.
                conPlano(trabajo, plano.orden(), (video, entidad) -> {
                    entidad.reintentar();
                    if (entidad.getIntentos() >= INTENTOS_POR_CLIP) {
                        usarRespaldo(video, entidad, MotivoRespaldoPlano.PROVEEDOR_NO_DISPONIBLE);
                    }
                });
            } else {
                respaldo(trabajo, plano, motivo(e.getTipo()), false);
            }
            return true;
        }
    }

    /** @return si se avisó al administrador. */
    private boolean consultar(Trabajo trabajo, PlanoATrabajar plano, GeneradorDeClips generador, String credencial) {
        boolean sinTiempo = plano.enviadoEn() == null
                || reloj.instant().isAfter(plano.enviadoEn().plus(ESPERA_POR_CLIP));
        EstadoDeClip estado;
        try {
            estado = generador.consultar(trabajo.modelo(), credencial, plano.idTarea());
        } catch (ErrorDeProveedor e) {
            if (e.getTipo() != ErrorDeProveedor.Tipo.NO_DISPONIBLE) {
                aviso.avisar(trabajo.modelo(), e.getTipo(), e.getMessage());
                respaldo(trabajo, plano, motivo(e.getTipo()), true);
                return true;
            }
            // Pasajero: la tarea sigue en curso en el proveedor; se vuelve a consultar hasta los 10 minutos.
            if (sinTiempo) {
                respaldo(trabajo, plano, MotivoRespaldoPlano.TIEMPO_AGOTADO, true);
            }
            return false;
        }
        switch (estado) {
            case Listo listo -> guardarClip(trabajo, plano, listo);
            case EnCurso enCurso -> {
                if (sinTiempo) {
                    // La tarea ya se envió: el proveedor la cobra aunque no se use (RN-32).
                    respaldo(trabajo, plano, MotivoRespaldoPlano.TIEMPO_AGOTADO, true);
                }
            }
            case Fallido fallido -> {
                log.info("El clip del plano {} del video {} falló: {}", plano.orden(), trabajo.videoId(),
                        fallido.motivo());
                conPlano(trabajo, plano.orden(), (video, entidad) -> {
                    entidad.reintentar();
                    if (entidad.getIntentos() >= INTENTOS_POR_CLIP) {
                        usarRespaldo(video, entidad, MotivoRespaldoPlano.CLIP_FALLIDO);
                    }
                });
            }
        }
        return false;
    }

    private void guardarClip(Trabajo trabajo, PlanoATrabajar plano, Listo listo) {
        String clave = "negocios/" + trabajo.negocioId() + "/videos/" + trabajo.videoId() + "/clip-" + plano.orden()
                + "-" + UUID.randomUUID() + ".mp4";
        almacen.guardar(clave, listo.video(), listo.tipoContenido());
        conPlano(trabajo, plano.orden(), (video, entidad) -> {
            registrarCosto(trabajo, video, listo.segundosGenerados());
            borrarAlConfirmar(entidad.clipListo(clave));
        });
    }

    /**
     * @param cobrar si la tarea ya estaba enviada: el proveedor la cobra aunque el clip no se use (RN-32).
     */
    private void respaldo(Trabajo trabajo, PlanoATrabajar plano, MotivoRespaldoPlano motivo, boolean cobrar) {
        conPlano(trabajo, plano.orden(), (video, entidad) -> {
            if (cobrar) {
                registrarCosto(trabajo, video);
            }
            usarRespaldo(video, entidad, motivo);
        });
    }

    /** Una excepción inesperada cuenta como intento fallido del plano. */
    private void contarFalla(Trabajo trabajo, PlanoATrabajar plano) {
        try {
            conPlano(trabajo, plano.orden(), (video, entidad) -> {
                if (entidad.getEstado() == EstadoPlano.PENDIENTE && entidad.getIntentos() < plano.intentos() + 1) {
                    entidad.prepararEnvio(reloj.instant());
                }
                if (entidad.getEstado() == EstadoPlano.GENERANDO) {
                    // La tarea ya se había enviado y se abandona: el proveedor la cobra igual (RN-32).
                    registrarCosto(trabajo, video);
                }
                entidad.reintentar();
                if (entidad.getIntentos() >= INTENTOS_POR_CLIP) {
                    usarRespaldo(video, entidad, MotivoRespaldoPlano.CLIP_FALLIDO);
                }
            });
        } catch (RuntimeException e) {
            log.error("No se pudo registrar la falla del plano {} del video {}", plano.orden(), trabajo.videoId(), e);
        }
    }

    private static void usarRespaldo(Video video, Plano plano, MotivoRespaldoPlano motivo) {
        if (plano.usarRespaldo(motivo)) {
            // Una regeneración que termina en respaldo no se cobra (decisión del usuario).
            video.devolverRegeneracion();
        }
    }

    private void registrarCosto(Trabajo trabajo, Video video) {
        registrarCosto(trabajo, video, null);
    }

    /**
     * RN-32: el costo de un clip. Si el proveedor informa cuántos segundos generó (y cobra), se usan esos; si no, la
     * duración pedida.
     */
    private void registrarCosto(Trabajo trabajo, Video video, Double segundosGenerados) {
        BigDecimal segundos = segundosGenerados != null && segundosGenerados > 0
                ? BigDecimal.valueOf(segundosGenerados).setScale(3, java.math.RoundingMode.HALF_UP)
                : BigDecimal.valueOf(segundosDelClip(trabajo.modelo()));
        BigDecimal costo = costos.registrar(trabajo.negocioId(), trabajo.videoId(), trabajo.modelo(),
                ConceptoCosto.CLIP, segundos);
        video.sumarCosto(costo);
    }

    /** Cambia un plano en su propia transacción, con el video bloqueado y solo si sigue generando. */
    private void conPlano(Trabajo trabajo, int orden, java.util.function.BiConsumer<Video, Plano> cambio) {
        transaccion.executeWithoutResult(estado -> {
            Video video = videos.bloquear(trabajo.videoId()).orElseThrow();
            if (video.getEstado() != EstadoVideo.GENERANDO) {
                return;
            }
            video.plano(orden).ifPresent(plano -> cambio.accept(video, plano));
            video.marcarActualizado(reloj.instant());
        });
    }

    private Trabajo leer(Long videoId) {
        Video video = videos.buscarConModeloDeVideo(videoId).orElseThrow();
        String nicho = negocios.findById(video.getNegocioId()).orElseThrow().getNicho();
        PaqueteNicho paquete = nichos.de(nicho);
        List<PlanoATrabajar> planos = video.planosConFoto().stream()
                .map(plano -> new PlanoATrabajar(plano.getOrden(), plano.getEstado(), plano.getIntentos(),
                        plano.getIdTareaProveedor(), plano.getEnviadoEn(), plano.getClaveFoto(),
                        plano.getMovimiento()))
                .toList();
        return new Trabajo(video.getId(), video.getNegocioId(), video.getModeloVideoUsado(), paquete.movimientos(),
                planos);
    }

    private byte[] leerArchivo(String clave) {
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
                    log.warn("No se pudo borrar el clip anterior {}", clave, e);
                }
            }
        });
    }

    static int segundosDelClip(ModeloIA modelo) {
        Object valor = modelo.getParametros() == null ? null : modelo.getParametros().get("duracionClipSegundos");
        return valor instanceof Number numero ? numero.intValue() : SEGUNDOS_POR_DEFECTO;
    }

    private static MotivoRespaldoPlano motivo(ErrorDeProveedor.Tipo tipo) {
        return switch (tipo) {
            case CREDENCIAL_INVALIDA -> MotivoRespaldoPlano.CREDENCIAL_INVALIDA;
            case CONFIGURACION_INVALIDA -> MotivoRespaldoPlano.CONFIGURACION_INVALIDA;
            case NO_DISPONIBLE -> MotivoRespaldoPlano.PROVEEDOR_NO_DISPONIBLE;
        };
    }
}
