package com.autoreel.chat.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.catalogo.aplicacion.ServicioCatalogo.Carrusel;
import com.autoreel.catalogo.dominio.Producto;
import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.chat.api.ConversacionVista;
import com.autoreel.chat.api.SolicitudMensaje;
import com.autoreel.chat.dominio.AgrupadorDeMensajes;
import com.autoreel.chat.dominio.Conversacion;
import com.autoreel.chat.dominio.ConversacionCompletada;
import com.autoreel.chat.dominio.EstadoConversacion;
import com.autoreel.chat.dominio.EvaluadorDeRespuesta;
import com.autoreel.chat.dominio.EvaluadorDeRespuesta.Aceptada;
import com.autoreel.chat.dominio.EvaluadorDeRespuesta.Evaluacion;
import com.autoreel.chat.dominio.EvaluadorDeRespuesta.Rechazada;
import com.autoreel.chat.dominio.MensajeChat;
import com.autoreel.chat.infraestructura.ConversacionRepositorio;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.negocio.dominio.Negocio;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import com.autoreel.nicho.dominio.GuionChat;
import com.autoreel.nicho.dominio.GuionChat.PasoChat;
import com.autoreel.nicho.dominio.GuionChat.TipoPaso;
import com.autoreel.nicho.dominio.Opcion;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.video.infraestructura.VideoRepositorio;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat del video del día (RF-19, RF-20): preguntas fijas del paquete de nicho, sin IA. El backend lleva el reloj
 * de la agrupación de mensajes (RN-17), así la regla no depende del navegador.
 */
@Service
public class ServicioChat {

    private static final String RESPUESTA_OMITIDA = "No, gracias";

    private final ConversacionRepositorio conversaciones;
    private final NegocioRepositorio negocios;
    private final RegistroDeNichos nichos;
    private final ServicioCatalogo catalogo;
    private final VideoRepositorio videos;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public ServicioChat(ConversacionRepositorio conversaciones, NegocioRepositorio negocios, RegistroDeNichos nichos,
            ServicioCatalogo catalogo, VideoRepositorio videos, ApplicationEventPublisher eventos, Clock reloj) {
        this.conversaciones = conversaciones;
        this.negocios = negocios;
        this.nichos = nichos;
        this.catalogo = catalogo;
        this.videos = videos;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * Devuelve la conversación en curso o empieza una. Con {@code empezarDeNuevo}, abandona la anterior (RN-19).
     * RN-09: sin el onboarding completo no se pueden crear videos.
     */
    @Transactional
    public ConversacionVista iniciar(Long negocioId, boolean empezarDeNuevo) {
        Negocio negocio = negocios.bloquear(negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos tu negocio."));
        if (!negocio.isOnboardingCompleto()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "ONBOARDING_INCOMPLETO",
                    "Completa el perfil de tu negocio para crear tu primer video.");
        }
        Instant ahora = reloj.instant();
        if (!empezarDeNuevo) {
            Optional<Conversacion> enCurso = conversaciones.buscarEnCurso(negocioId);
            if (enCurso.isPresent()) {
                return vista(enCurso.get(), ahora);
            }
        } else {
            conversaciones.abandonarEnCurso(negocioId, ahora);
        }
        GuionChat guion = guion(negocio);
        PasoChat primero = guion.pasos().getFirst();
        Conversacion conversacion = new Conversacion(negocios.getReferenceById(negocioId), primero.id(), ahora);
        conversacion.decirSistema(primero.texto(), ahora);
        return vista(conversaciones.save(conversacion), ahora);
    }

    /** La conversación en curso o, si no hay, la última que se completó hoy (día de Lima). */
    @Transactional(readOnly = true)
    public Optional<ConversacionVista> actual(Long negocioId) {
        Instant ahora = reloj.instant();
        LocalDate hoy = LocalDate.now(reloj);
        return conversaciones.buscarEnCurso(negocioId)
                .or(() -> conversaciones.findFirstByNegocioIdAndEstadoOrderByFechaActualizacionDesc(negocioId,
                                EstadoConversacion.COMPLETA)
                        .filter(c -> LocalDate.ofInstant(c.getFechaActualizacion(), reloj.getZone()).equals(hoy)))
                .map(conversacion -> vista(conversacion, ahora));
    }

    @Transactional
    public ConversacionVista responder(Long negocioId, Long conversacionId, SolicitudMensaje solicitud) {
        Conversacion conversacion = bloquear(negocioId, conversacionId);
        GuionChat guion = guion(conversacion.getNegocio());
        Instant ahora = reloj.instant();
        if (!conversacion.getPasoActual().equals(solicitud.paso())) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "PASO_CAMBIADO",
                    "La conversación avanzó. Actualizamos la pantalla.");
        }
        if (solicitud.formasDeRespuesta() != 1) {
            throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "Envía una sola respuesta a la vez.");
        }
        PasoChat paso = guion.paso(conversacion.getPasoActual());

        if (solicitud.texto() != null) {
            escribir(conversacion, guion, paso, solicitud.texto(), ahora);
        } else if (solicitud.opcion() != null) {
            exigirTipo(paso, TipoPaso.BOTONES);
            Opcion opcion = paso.opcion(solicitud.opcion()).orElseThrow(() -> new ExcepcionDeNegocio(
                    "OPCION_INVALIDA", "Esa opción no está disponible. Elige una de las que ves arriba."));
            conversacion.decirUsuario(opcion.texto(), ahora);
            aceptar(conversacion, guion, paso, opcion.valor(), ahora);
        } else if (solicitud.productoId() != null) {
            exigirTipo(paso, TipoPaso.PRODUCTO);
            Producto producto = catalogo.paraVideo(conversacion.getNegocio().getId(), solicitud.productoId())
                    .filter(Producto::isEnCatalogo)
                    .orElseThrow(() -> new ExcepcionDeNegocio("PRODUCTO_NO_DISPONIBLE",
                            "Ese producto no está disponible: necesita estar activo y tener sus tres fotos."));
            conversacion.decirUsuario(producto.getNombre(), ahora);
            aceptar(conversacion, guion, paso, producto.getId(), ahora);
        } else if (solicitud.productoNuevo() != null) {
            exigirTipo(paso, TipoPaso.PRODUCTO);
            SolicitudMensaje.ProductoNuevo nuevo = solicitud.productoNuevo();
            Producto producto = catalogo.crearDesdeChat(conversacion.getNegocio().getId(), nuevo.nombre(),
                    nuevo.descripcion(), nuevo.fotos(), nuevo.guardarEnCatalogo());
            conversacion.decirUsuario(producto.getNombre(), ahora);
            aceptar(conversacion, guion, paso, producto.getId(), ahora);
        } else {
            exigirTipo(paso, TipoPaso.TEXTO);
            if (!paso.omitible()) {
                throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "PASO_NO_ADMITE_RESPUESTA",
                        "Esta pregunta necesita una respuesta.");
            }
            // Lo que haya escrito y no se agrupó todavía se descarta: eligió no responder.
            conversacion.marcarProcesados(conversacion.pendientes());
            conversacion.decirUsuario(RESPUESTA_OMITIDA, ahora);
            aplicar(conversacion, guion, paso,
                    EvaluadorDeRespuesta.omitir(guion, paso, conversacion.getRespuestas()), ahora);
        }
        // Los mensajes nuevos se guardan ahora, para devolverlos con su id.
        conversaciones.flush();
        return vista(conversacion, ahora);
    }

    /** Cierra la respuesta agrupada si ya pasaron 4 segundos sin mensajes nuevos. Se puede llamar varias veces. */
    @Transactional
    public ConversacionVista procesar(Long negocioId, Long conversacionId) {
        Conversacion conversacion = bloquear(negocioId, conversacionId);
        Instant ahora = reloj.instant();
        procesarPendientes(conversacion, guion(conversacion.getNegocio()), ahora);
        conversaciones.flush();
        return vista(conversacion, ahora);
    }

    private void escribir(Conversacion conversacion, GuionChat guion, PasoChat paso, String texto, Instant ahora) {
        String limpio = texto.strip();
        if (limpio.isEmpty()) {
            throw new ExcepcionDeNegocio("SOLICITUD_INVALIDA", "Escribe tu respuesta antes de enviarla.");
        }
        if (paso.tipo() == TipoPaso.BOTONES && paso.opcionLibre() != null) {
            escribirEnBotones(conversacion, guion, paso, limpio, ahora);
            return;
        }
        exigirTipo(paso, TipoPaso.TEXTO);
        if (limpio.length() > guion.maximoCaracteres()) {
            throw new ExcepcionDeNegocio("RESPUESTA_MUY_LARGA", guion.mensajes().muyLarga());
        }
        // Si lo anterior ya se cerró (pasaron 4 s), se procesa primero: este texto es otra respuesta.
        procesarPendientes(conversacion, guion, ahora);
        if (!conversacion.enCurso()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "CONVERSACION_CERRADA", "Esta conversación ya terminó.");
        }
        conversacion.escribirUsuario(limpio, ahora);
    }

    /**
     * El usuario escribió en lugar de tocar un botón (2026-11-06): se revisa como cualquier texto (RN-18), se guarda
     * aparte y se sigue con la opción libre del paso. No se agrupa: es una sola respuesta.
     */
    private void escribirEnBotones(Conversacion conversacion, GuionChat guion, PasoChat paso, String texto,
            Instant ahora) {
        conversacion.decirUsuario(texto, ahora);
        switch (EvaluadorDeRespuesta.evaluar(guion, paso, conversacion.getRespuestas(), texto)) {
            case Aceptada aceptada when aceptada.valor() != null -> {
                conversacion.responder(paso.claveLibre(), aceptada.valor());
                aceptar(conversacion, guion, paso, paso.opcionLibre(), ahora);
            }
            case Aceptada sinTexto -> {
                conversacion.decirSistema(guion.mensajes().muyCorta(), ahora);
                conversacion.decirSistema(paso.texto(), ahora);
            }
            case Rechazada rechazada -> {
                conversacion.decirSistema(rechazada.mensaje(), ahora);
                conversacion.decirSistema(paso.texto(), ahora);
            }
        }
    }

    private void procesarPendientes(Conversacion conversacion, GuionChat guion, Instant ahora) {
        var grupos = AgrupadorDeMensajes.agrupar(conversacion.pendientes(), MensajeChat::getFechaHora, ahora);
        for (List<MensajeChat> grupo : grupos.cerrados()) {
            conversacion.marcarProcesados(grupo);
            if (!conversacion.enCurso()) {
                continue;
            }
            PasoChat paso = guion.paso(conversacion.getPasoActual());
            if (paso.tipo() != TipoPaso.TEXTO) {
                continue;
            }
            String texto = String.join(" ", grupo.stream().map(MensajeChat::getTexto).toList());
            aplicar(conversacion, guion, paso,
                    EvaluadorDeRespuesta.evaluar(guion, paso, conversacion.getRespuestas(), texto), ahora);
        }
    }

    private void aplicar(Conversacion conversacion, GuionChat guion, PasoChat paso, Evaluacion evaluacion,
            Instant ahora) {
        switch (evaluacion) {
            case Aceptada aceptada -> aceptar(conversacion, guion, paso, aceptada.valor(), ahora);
            case Rechazada rechazada -> {
                conversacion.decirSistema(rechazada.mensaje(), ahora);
                conversacion.decirSistema(paso.texto(), ahora);
            }
        }
    }

    /** Guarda la respuesta y hace la siguiente pregunta, o cierra la conversación. */
    private void aceptar(Conversacion conversacion, GuionChat guion, PasoChat paso, Object valor, Instant ahora) {
        conversacion.responder(paso.id(), valor);
        Optional<PasoChat> siguiente = guion.siguiente(paso.id());
        if (siguiente.isPresent()) {
            preguntar(conversacion, guion, siguiente.get(), ahora);
            return;
        }
        PasoChat pasoProducto = pasoProducto(guion);
        boolean productoDisponible = conversacion.getRespuestas().get(pasoProducto.id()) instanceof Number id
                && catalogo.paraVideo(conversacion.getNegocio().getId(), id.longValue()).isPresent();
        if (!productoDisponible) {
            // Se archivó o perdió una foto mientras tanto (RN-12, RN-13): hay que elegir otro.
            conversacion.decirSistema(guion.mensajes().productoNoDisponible(), ahora);
            preguntar(conversacion, guion, pasoProducto, ahora);
            return;
        }
        conversacion.completar(ahora);
        conversacion.decirSistema(guion.mensajes().cierre(), ahora);
        // El video nace en esta misma transacción; su guion se escribe después, sin hacer esperar al chat.
        eventos.publishEvent(new ConversacionCompletada(conversacion.getId(), conversacion.getNegocio().getId(),
                new HashMap<>(conversacion.getRespuestas())));
    }

    private void preguntar(Conversacion conversacion, GuionChat guion, PasoChat paso, Instant ahora) {
        conversacion.irAPaso(paso.id());
        conversacion.decirSistema(paso.texto(), ahora);
        if (paso.tipo() == TipoPaso.PRODUCTO
                && catalogo.carrusel(conversacion.getNegocio().getId()).productos().isEmpty()) {
            conversacion.decirSistema(guion.mensajes().catalogoVacio(), ahora);
        }
    }

    private ConversacionVista vista(Conversacion conversacion, Instant ahora) {
        GuionChat guion = guion(conversacion.getNegocio());
        Long negocioId = conversacion.getNegocio().getId();
        Map<String, Object> respuestas = conversacion.getRespuestas();
        var agrupados = AgrupadorDeMensajes.agrupar(conversacion.pendientes(), MensajeChat::getFechaHora, ahora);
        Long procesarEnMs = conversacion.pendientes().isEmpty() ? null
                : agrupados.cierreEn() == null ? 0L : agrupados.cierreEn().toMillis();

        ConversacionVista.Paso paso = null;
        List<ConversacionVista.ProductoCarrusel> productos = List.of();
        int incompletos = 0;
        if (conversacion.enCurso()) {
            PasoChat actual = guion.paso(conversacion.getPasoActual());
            boolean obligatoria = actual.esObligatoria(respuestas);
            paso = new ConversacionVista.Paso(actual.id(), actual.tipo(), actual.texto(), actual.opciones(),
                    obligatoria, actual.omitible() && !obligatoria, sugerencia(conversacion, actual, guion),
                    actual.ejemplo(), actual.admiteTexto());
            if (actual.tipo() == TipoPaso.PRODUCTO) {
                Carrusel carrusel = catalogo.carrusel(negocioId);
                productos = carrusel.productos().stream().map(this::paraCarrusel).toList();
                incompletos = carrusel.incompletos();
            }
        }
        List<ConversacionVista.Mensaje> mensajes = conversacion.getMensajes().stream()
                .map(m -> new ConversacionVista.Mensaje(m.getId(), m.getAutor(), m.getTexto(), m.getFechaHora(),
                        !m.isProcesado()))
                .toList();
        boolean completa = conversacion.getEstado() == EstadoConversacion.COMPLETA;
        List<ConversacionVista.Resumen> resumen = completa ? resumen(conversacion, guion) : List.of();
        Long videoId = completa ? videos.idDeConversacion(conversacion.getId()).orElse(null) : null;
        return new ConversacionVista(conversacion.getId(), conversacion.getEstado(), paso, mensajes, procesarEnMs,
                respuestas, guion.mensajes().aviso(), guion.maximoCaracteres(), productos, incompletos, resumen,
                videoId);
    }

    private ConversacionVista.ProductoCarrusel paraCarrusel(Producto producto) {
        String foto = producto.foto(TipoFoto.ENTERO).map(catalogo::urlDeFoto).orElse(null);
        return new ConversacionVista.ProductoCarrusel(producto.getId(), producto.getNombre(),
                producto.getDescripcion(), foto);
    }

    private String sugerencia(Conversacion conversacion, PasoChat paso, GuionChat guion) {
        if (paso.sugerencia() != GuionChat.Sugerencia.DESCRIPCION_PRODUCTO) {
            return null;
        }
        return productoElegido(conversacion, guion).map(Producto::getDescripcion).orElse(null);
    }

    private Optional<Producto> productoElegido(Conversacion conversacion, GuionChat guion) {
        Object id = conversacion.getRespuestas().get(pasoProducto(guion).id());
        return id instanceof Number numero
                ? catalogo.elegidoParaVideo(conversacion.getNegocio().getId(), numero.longValue())
                : Optional.empty();
    }

    private List<ConversacionVista.Resumen> resumen(Conversacion conversacion, GuionChat guion) {
        List<ConversacionVista.Resumen> resumen = new ArrayList<>();
        for (PasoChat paso : guion.pasos()) {
            Object valor = conversacion.getRespuestas().get(paso.id());
            String texto = switch (paso.tipo()) {
                case BOTONES -> conversacion.getRespuestas().get(paso.claveLibre()) instanceof String libre ? libre
                        : valor == null ? "—"
                        : paso.opcion(String.valueOf(valor)).map(Opcion::texto).orElse(String.valueOf(valor));
                case PRODUCTO -> productoElegido(conversacion, guion).map(Producto::getNombre).orElse("—");
                case TEXTO -> valor == null ? "—" : String.valueOf(valor);
            };
            resumen.add(new ConversacionVista.Resumen(paso.texto(), texto));
        }
        return resumen;
    }

    private static PasoChat pasoProducto(GuionChat guion) {
        return guion.pasos().stream().filter(paso -> paso.tipo() == TipoPaso.PRODUCTO).findFirst().orElseThrow();
    }

    private static void exigirTipo(PasoChat paso, TipoPaso tipo) {
        if (paso.tipo() != tipo) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "PASO_NO_ADMITE_RESPUESTA",
                    "Esta pregunta se responde de otra forma. Usa las opciones de la pantalla.");
        }
    }

    private Conversacion bloquear(Long negocioId, Long conversacionId) {
        Conversacion conversacion = conversaciones.bloquearDeNegocio(conversacionId, negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos esa conversación."));
        if (!conversacion.enCurso()) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "CONVERSACION_CERRADA", "Esta conversación ya terminó.");
        }
        return conversacion;
    }

    private GuionChat guion(Negocio negocio) {
        return nichos.de(negocio.getNicho()).guionDelChat();
    }
}
