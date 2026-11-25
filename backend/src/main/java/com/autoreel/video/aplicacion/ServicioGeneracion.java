package com.autoreel.video.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.autoreel.ia.dominio.GeneradorDeClips.SolicitudDeExtension;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import com.autoreel.nicho.dominio.Movimientos;
import com.autoreel.nicho.dominio.PlantillaVideo;
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
 * Genera los clips de un video (sección 8.2). Un plano tiene dos intentos (RN-30); si no consigue su clip, no hay
 * respaldo sin IA (decisión del usuario, 2026-11-06): una regeneración conserva el clip anterior y un video sin todos
 * sus clips pasa a ERROR con el video del día devuelto (RN-27). Las llamadas al
 * proveedor van fuera de toda transacción; cada cambio de un plano se guarda en su propia transacción corta, así un
 * corte nunca deja una tarea enviada sin registrar ni un plano reenviado sin límite.
 */
@Service
public class ServicioGeneracion {

    private static final Logger log = LoggerFactory.getLogger(ServicioGeneracion.class);

    /** Tiempo que el procesador tiene tomado un video. */
    static final Duration RECLAMO = Duration.ofMinutes(5);
    /** Sin el clip a los 10 minutos de enviarlo, el intento se da por perdido. */
    static final Duration ESPERA_POR_CLIP = Duration.ofMinutes(10);
    /** Red de seguridad para un plano que quedó pendiente después de un envío. */
    static final Duration PLAZO_TOTAL = Duration.ofMinutes(20);
    /** RN-30: un plano tiene dos intentos. */
    static final int INTENTOS_POR_CLIP = 2;
    private static final int SEGUNDOS_POR_DEFECTO = 4;
    /** Lo que agrega Veo al extender un clip. */
    static final int SEGUNDOS_DE_EXTENSION = 7;
    private static final String PREFIJO_DE_EXTENSION =
            "Continue this exact shot seamlessly from its last frame, with the same camera motion, light and style. ";
    private static final long TAMANO_MAXIMO = 200L * 1024 * 1024;
    public static final String MOTIVO_SIN_CLIPS =
            "No pudimos crear los clips con IA. Te devolvimos el video del día; inténtalo de nuevo en un rato.";
    public static final String AVISO_REGENERACION =
            "No pudimos crear el clip nuevo. Dejamos el anterior y no se descontó la regeneración.";

    /**
     * @param transiciones cada toma termina en la foto de la siguiente (modo "tomas largas", 2026-11-25).
     * @param extender     cada clip se extiende después de generarse (plano extendido, 2026-11-25).
     */
    private record Trabajo(Long videoId, Long negocioId, ModeloIA modelo, Movimientos movimientos,
            String creatividad, boolean transiciones, boolean extender, List<PlanoATrabajar> planos) {

        Optional<PlanoATrabajar> siguiente(PlanoATrabajar plano) {
            return planos.stream().filter(otro -> otro.orden() == plano.orden() + 1).findFirst();
        }
    }

    /**
     * @param toma     la toma que escribió la IA; vacía en los videos anteriores, que usan {@code movimiento}.
     * @param segundos lo que dura el plano en el video.
     */
    private record PlanoATrabajar(int orden, EstadoPlano estado, int intentos, String idTarea, Instant enviadoEn,
            String claveFoto, String toma, String movimiento, int segundos, boolean extensionEnCurso) {
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
    private final ServicioConsumo consumo;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final TransactionTemplate lectura;

    public ServicioGeneracion(VideoRepositorio videos, NegocioRepositorio negocios, RegistroDeNichos nichos,
            FabricaDeProveedores proveedores, ProveedorDeSecretos secretos, AvisoDeIntegracion aviso,
            ServicioCostos costos, AlmacenDeArchivos almacen, PreparadorDeFotos preparador, ServicioConsumo consumo,
            Clock reloj, PlatformTransactionManager transacciones) {
        this.videos = videos;
        this.negocios = negocios;
        this.nichos = nichos;
        this.proveedores = proveedores;
        this.secretos = secretos;
        this.aviso = aviso;
        this.costos = costos;
        this.almacen = almacen;
        this.preparador = preparador;
        this.consumo = consumo;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** Videos generando sus clips que nadie está procesando. */
    public List<Long> pendientes(int maximo) {
        return lectura.execute(estado -> videos.enProceso(EstadoVideo.GENERANDO, reloj.instant(), Limit.of(maximo)));
    }

    /** Una pasada sobre los planos del video: envía o consulta; con todos listos, a componer. */
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
                // Si otro plano ya dejó el video en ERROR, no se piden más clips (costarían sin servir).
                if (!Boolean.TRUE.equals(lectura.execute(estado -> videos.findById(videoId)
                        .map(video -> video.getEstado() == EstadoVideo.GENERANDO).orElse(false)))) {
                    break;
                }
                try {
                    if (plazoVencido(plano)) {
                        sinClip(trabajo, plano, MotivoRespaldoPlano.TIEMPO_AGOTADO, false);
                    } else if (generador.isEmpty() || credencial.isEmpty()) {
                        MotivoRespaldoPlano motivo = generador.isEmpty() ? MotivoRespaldoPlano.MODELO_INACTIVO
                                : MotivoRespaldoPlano.SIN_CREDENCIAL;
                        if (!avisado) {
                            aviso.avisar(trabajo.modelo(), ErrorDeProveedor.Tipo.CONFIGURACION_INVALIDA,
                                    generador.isEmpty() ? "El modelo de video no está activo o no tiene adaptador"
                                            : "No hay credencial configurada para " + trabajo.modelo().getNombreSecreto());
                            avisado = true;
                        }
                        sinClip(trabajo, plano, motivo, plano.estado() == EstadoPlano.GENERANDO);
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
     * envío, el plano se queda sin clip. No cuenta la espera en la cola (con un proveedor síncrono, un video puede esperar a que
     * terminen otros) ni los planos enviados: esos se consultan primero y tienen su propia espera de 10 minutos.
     */
    private boolean plazoVencido(PlanoATrabajar plano) {
        return plano.estado() == EstadoPlano.PENDIENTE && plano.intentos() > 0 && plano.enviadoEn() != null
                && reloj.instant().isAfter(plano.enviadoEn().plus(PLAZO_TOTAL));
    }

    /** @return si se avisó al administrador. */
    private boolean enviar(Trabajo trabajo, PlanoATrabajar plano, GeneradorDeClips generador, String credencial) {
        if (plano.intentos() >= INTENTOS_POR_CLIP) {
            sinClip(trabajo, plano, MotivoRespaldoPlano.CLIP_FALLIDO, false);
            return false;
        }
        byte[] foto = preparador.vertical(leerArchivo(plano.claveFoto()));
        String toma = plano.toma() != null ? plano.toma() : trabajo.movimientos().buscar(plano.movimiento())
                .map(Movimientos.Movimiento::instruccion)
                .orElseThrow(() -> new IllegalStateException("Movimiento desconocido: " + plano.movimiento()));
        int segundos = segundosDelClip(trabajo.modelo(), plano.segundos());
        // Transiciones: el clip termina en la foto de la toma siguiente; la última toma termina libre.
        byte[] fotoFinal = trabajo.transiciones() ? trabajo.siguiente(plano)
                .map(siguiente -> preparador.vertical(leerArchivo(siguiente.claveFoto()))).orElse(null) : null;
        // Se cuenta el intento antes de llamar: si algo se corta después, no se reenvía sin límite.
        conPlano(trabajo, plano.orden(), (video, entidad) -> entidad.prepararEnvio(reloj.instant()));
        try {
            String idTarea = generador.iniciar(trabajo.modelo(), credencial, new SolicitudDeClip(foto, "image/jpeg",
                    trabajo.movimientos().promptDeToma(toma, trabajo.creatividad()),
                    trabajo.movimientos().negativos(trabajo.creatividad()), segundos, fotoFinal));
            conPlano(trabajo, plano.orden(), (video, entidad) -> entidad.enviado(idTarea));
            return false;
        } catch (ErrorDeProveedor e) {
            aviso.avisar(trabajo.modelo(), e.getTipo(), e.getMessage());
            if (e.getTipo() == ErrorDeProveedor.Tipo.NO_DISPONIBLE) {
                // Cuenta como intento: con dos, el plano se queda sin clip.
                conPlano(trabajo, plano.orden(), (video, entidad) -> {
                    entidad.reintentar();
                    if (entidad.getIntentos() >= INTENTOS_POR_CLIP) {
                        sinClip(trabajo, video, entidad, MotivoRespaldoPlano.PROVEEDOR_NO_DISPONIBLE);
                    }
                });
            } else {
                sinClip(trabajo, plano, motivo(e.getTipo()), false);
            }
            return true;
        }
    }

    /** @return si se avisó al administrador. */
    private boolean consultar(Trabajo trabajo, PlanoATrabajar plano, GeneradorDeClips generador, String credencial) {
        boolean sinTiempo = plano.enviadoEn() == null
                || reloj.instant().isAfter(plano.enviadoEn().plus(ESPERA_POR_CLIP));
        if (plano.extensionEnCurso()) {
            consultarExtension(trabajo, plano, generador, credencial, sinTiempo);
            return false;
        }
        EstadoDeClip estado;
        try {
            estado = generador.consultar(trabajo.modelo(), credencial, plano.idTarea());
        } catch (ErrorDeProveedor e) {
            if (e.getTipo() != ErrorDeProveedor.Tipo.NO_DISPONIBLE) {
                aviso.avisar(trabajo.modelo(), e.getTipo(), e.getMessage());
                sinClip(trabajo, plano, motivo(e.getTipo()), true);
                return true;
            }
            // Pasajero: la tarea sigue en curso en el proveedor; se vuelve a consultar hasta los 10 minutos.
            if (sinTiempo) {
                sinClip(trabajo, plano, MotivoRespaldoPlano.TIEMPO_AGOTADO, true);
            }
            return false;
        }
        switch (estado) {
            case Listo listo -> {
                guardarClip(trabajo, plano, listo);
                if (trabajo.extender()) {
                    extender(trabajo, plano, generador, credencial, listo.video());
                }
            }
            case EnCurso enCurso -> {
                if (sinTiempo) {
                    // La tarea ya se envió: el proveedor la cobra aunque no se use (RN-32).
                    sinClip(trabajo, plano, MotivoRespaldoPlano.TIEMPO_AGOTADO, true);
                }
            }
            case Fallido fallido -> {
                log.info("El clip del plano {} del video {} falló: {}", plano.orden(), trabajo.videoId(),
                        fallido.motivo());
                conPlano(trabajo, plano.orden(), (video, entidad) -> {
                    entidad.reintentar();
                    if (entidad.getIntentos() >= INTENTOS_POR_CLIP) {
                        sinClip(trabajo, video, entidad, MotivoRespaldoPlano.CLIP_FALLIDO);
                    }
                });
            }
        }
        return false;
    }

    /**
     * Plano extendido: con el clip base ya guardado, pide a Veo que lo continúe. Si no se puede, el plano se queda con
     * el clip base (la composición congela su último cuadro hasta completar la toma).
     */
    private void extender(Trabajo trabajo, PlanoATrabajar plano, GeneradorDeClips generador, String credencial,
            byte[] clipBase) {
        String toma = plano.toma() != null ? plano.toma() : "";
        try {
            String idTarea = generador.extender(trabajo.modelo(), credencial, new SolicitudDeExtension(clipBase,
                    PREFIJO_DE_EXTENSION + trabajo.movimientos().promptDeToma(toma, trabajo.creatividad()),
                    trabajo.movimientos().negativos(trabajo.creatividad())));
            conPlano(trabajo, plano.orden(), (video, entidad) -> entidad.extendiendo(idTarea, reloj.instant()));
        } catch (RuntimeException e) {
            // Cualquier falla deja el plano con su clip base: no se vuelve a pagar el clip.
            log.info("No se pudo extender el clip del plano {} del video {}: {}", plano.orden(), trabajo.videoId(),
                    e.getMessage());
        }
    }

    /** La extensión en curso: si llega, reemplaza al clip base; si falla o se agota el tiempo, se conserva el base. */
    private void consultarExtension(Trabajo trabajo, PlanoATrabajar plano, GeneradorDeClips generador,
            String credencial, boolean sinTiempo) {
        EstadoDeClip estado;
        try {
            estado = generador.consultar(trabajo.modelo(), credencial, plano.idTarea());
        } catch (ErrorDeProveedor e) {
            if (e.getTipo() != ErrorDeProveedor.Tipo.NO_DISPONIBLE || sinTiempo) {
                extensionFallida(trabajo, plano, "error del proveedor: " + e.getMessage());
            }
            return;
        }
        switch (estado) {
            case Listo listo -> guardarExtension(trabajo, plano, listo);
            case EnCurso enCurso -> {
                if (sinTiempo) {
                    extensionFallida(trabajo, plano, "se agotó el tiempo");
                }
            }
            case Fallido fallido -> extensionFallida(trabajo, plano, fallido.motivo());
        }
    }

    private void guardarExtension(Trabajo trabajo, PlanoATrabajar plano, Listo listo) {
        String clave = "negocios/" + trabajo.negocioId() + "/videos/" + trabajo.videoId() + "/clip-" + plano.orden()
                + "-extendido-" + UUID.randomUUID() + ".mp4";
        almacen.guardar(clave, listo.video(), listo.tipoContenido());
        conPlano(trabajo, plano.orden(), (video, entidad) -> {
            video.sumarCosto(costos.registrar(trabajo.negocioId(), trabajo.videoId(), trabajo.modelo(),
                    ConceptoCosto.CLIP, BigDecimal.valueOf(SEGUNDOS_DE_EXTENSION)));
            borrarAlConfirmar(entidad.clipListo(clave));
        });
    }

    /** La extensión ya se envió: el proveedor la cobra aunque falle (RN-32). */
    private void extensionFallida(Trabajo trabajo, PlanoATrabajar plano, String motivo) {
        log.info("La extensión del plano {} del video {} no llegó ({}): se usa el clip base", plano.orden(),
                trabajo.videoId(), motivo);
        conPlano(trabajo, plano.orden(), (video, entidad) -> {
            video.sumarCosto(costos.registrar(trabajo.negocioId(), trabajo.videoId(), trabajo.modelo(),
                    ConceptoCosto.CLIP, BigDecimal.valueOf(SEGUNDOS_DE_EXTENSION)));
            entidad.extensionFallida();
        });
    }

    private void guardarClip(Trabajo trabajo, PlanoATrabajar plano, Listo listo) {
        String clave = "negocios/" + trabajo.negocioId() + "/videos/" + trabajo.videoId() + "/clip-" + plano.orden()
                + "-" + UUID.randomUUID() + ".mp4";
        almacen.guardar(clave, listo.video(), listo.tipoContenido());
        conPlano(trabajo, plano.orden(), (video, entidad) -> {
            registrarCosto(trabajo, video, entidad, listo.segundosGenerados());
            borrarAlConfirmar(entidad.clipListo(clave));
        });
    }

    /**
     * @param cobrar si la tarea ya estaba enviada: el proveedor la cobra aunque el clip no se use (RN-32).
     */
    private void sinClip(Trabajo trabajo, PlanoATrabajar plano, MotivoRespaldoPlano motivo, boolean cobrar) {
        conPlano(trabajo, plano.orden(), (video, entidad) -> {
            if (cobrar) {
                registrarCosto(trabajo, video, entidad);
            }
            sinClip(trabajo, video, entidad, motivo);
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
                    registrarCosto(trabajo, video, entidad);
                }
                entidad.reintentar();
                if (entidad.getIntentos() >= INTENTOS_POR_CLIP) {
                    sinClip(trabajo, video, entidad, MotivoRespaldoPlano.CLIP_FALLIDO);
                }
            });
        } catch (RuntimeException e) {
            log.error("No se pudo registrar la falla del plano {} del video {}", plano.orden(), trabajo.videoId(), e);
        }
    }

    /**
     * El plano no consiguió su clip. Si era una regeneración, conserva el clip anterior y la regeneración no se cobra.
     * Si no, el video no se puede armar: pasa a ERROR, los clips ya pedidos se cobran (RN-32) y se devuelve el video
     * del día (RN-27).
     */
    private void sinClip(Trabajo trabajo, Video video, Plano plano, MotivoRespaldoPlano motivo) {
        log.info("El plano {} del video {} se quedó sin clip: {}", plano.getOrden(), video.getId(), motivo);
        if (plano.conservarClipAnterior()) {
            video.regeneracionFallida(AVISO_REGENERACION);
            return;
        }
        for (Plano otro : video.planosConFoto()) {
            if (otro != plano && otro.getEstado() == EstadoPlano.GENERANDO) {
                registrarCosto(trabajo, video, otro);
            }
        }
        video.marcarError(MOTIVO_SIN_CLIPS, reloj.instant());
        if (video.devolverConsumo()) {
            consumo.devolver(video.getNegocioId());
        }
    }

    private void registrarCosto(Trabajo trabajo, Video video, Plano plano) {
        registrarCosto(trabajo, video, plano, null);
    }

    /**
     * RN-32: el costo de un clip. Si el proveedor informa cuántos segundos generó (y cobra), se usan esos; si no, la
     * duración pedida.
     */
    private void registrarCosto(Trabajo trabajo, Video video, Plano plano, Double segundosGenerados) {
        BigDecimal segundos = segundosGenerados != null && segundosGenerados > 0
                ? BigDecimal.valueOf(segundosGenerados).setScale(3, java.math.RoundingMode.HALF_UP)
                : BigDecimal.valueOf(segundosDelClip(trabajo.modelo(), plano.getSegundos()));
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
                        plano.getIdTareaProveedor(), plano.getEnviadoEn(), plano.getClaveFoto(), plano.getToma(),
                        plano.getMovimiento(), plano.getSegundos(), plano.isExtensionEnCurso()))
                .toList();
        boolean transiciones = paquete.plantillaVideo().modoDeTomas(video.getModoTomas()).transiciones();
        boolean extender = paquete.plantillaVideo().estructura(video.getEstructura())
                .map(PlantillaVideo.Estructura::extender).orElse(false);
        return new Trabajo(video.getId(), video.getNegocioId(), video.getModeloVideoUsado(), paquete.movimientos(),
                video.getCreatividad(), transiciones, extender, planos);
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

    /**
     * Duración que se le pide al proveedor para un plano: la más corta que admite el modelo y que cubre el plano (más
     * el recorte inicial)
     * ({@code duracionesClip} en sus parámetros, por ejemplo [4, 6, 8] en Veo). Si ninguna alcanza, la más larga: la
     * composición congela el último cuadro. Un modelo sin esa lista usa {@code duracionClipSegundos}.
     */
    static int segundosDelClip(ModeloIA modelo, int segundosDelPlano) {
        Map<String, Object> parametros = modelo.getParametros() == null ? Map.of() : modelo.getParametros();
        // El inicio del clip se salta en la composición: el clip debe cubrir la toma más ese recorte.
        double necesarios = segundosDelPlano + recorteInicial(modelo);
        List<Integer> duraciones = new ArrayList<>();
        if (parametros.get("duracionesClip") instanceof List<?> lista) {
            lista.stream().filter(Number.class::isInstance).map(valor -> ((Number) valor).intValue())
                    .forEach(duraciones::add);
        }
        if (duraciones.isEmpty()) {
            duraciones.add(parametros.get("duracionClipSegundos") instanceof Number numero ? numero.intValue()
                    : SEGUNDOS_POR_DEFECTO);
        }
        duraciones.sort(null);
        return duraciones.stream().filter(duracion -> duracion >= necesarios).findFirst()
                .orElse(duraciones.getLast());
    }

    /**
     * Segundos del inicio de cada clip que la composición salta ({@code recorteInicialSegundos} en los parámetros del
     * modelo; 0 si no lo tiene). Veo empieza con la foto casi quieta y luego salta a su animación (2026-11-25).
     */
    public static double recorteInicial(ModeloIA modelo) {
        Object valor = modelo.getParametros() == null ? null : modelo.getParametros().get("recorteInicialSegundos");
        return valor instanceof Number numero && numero.doubleValue() > 0 ? numero.doubleValue() : 0;
    }

    private static MotivoRespaldoPlano motivo(ErrorDeProveedor.Tipo tipo) {
        return switch (tipo) {
            case CREDENCIAL_INVALIDA -> MotivoRespaldoPlano.CREDENCIAL_INVALIDA;
            case CONFIGURACION_INVALIDA -> MotivoRespaldoPlano.CONFIGURACION_INVALIDA;
            case NO_DISPONIBLE -> MotivoRespaldoPlano.PROVEEDOR_NO_DISPONIBLE;
        };
    }
}
