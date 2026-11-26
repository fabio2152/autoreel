package com.autoreel.video.aplicacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.almacenamiento.dominio.ArchivoAlmacenado;
import com.autoreel.costos.aplicacion.ServicioCostos;
import com.autoreel.costos.dominio.ConceptoCosto;
import com.autoreel.ia.aplicacion.FabricaDeProveedores;
import com.autoreel.ia.dominio.AvisoDeIntegracion;
import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.Guionista;
import com.autoreel.ia.dominio.Guionista.ImagenAdjunta;
import com.autoreel.ia.dominio.Guionista.RespuestaDelModelo;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
import com.autoreel.nicho.dominio.Movimientos;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.video.dominio.Plano;
import com.autoreel.video.dominio.ValidadorDeGuion;
import com.autoreel.video.dominio.Video;
import com.autoreel.video.infraestructura.PreparadorDeFotos;
import com.autoreel.video.infraestructura.VideoRepositorio;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reescribe la toma de un plano con las correcciones del negocio (2026-11-25). El modelo de texto del video recibe las
 * instrucciones del paquete de nicho con la toma actual y las correcciones, más la foto del plano, y devuelve la toma
 * nueva. Se usa una sola vez por cada cambio de las correcciones: al regenerar el plano en la revisión o, si se
 * guardaron en el storyboard, antes de enviar su primer clip. La llamada va fuera de toda transacción; su costo
 * (GUION) se registra en una transacción corta aunque la respuesta no sirva (RN-32).
 */
@Component
public class CorrectorDeTomas {

    private static final Logger log = LoggerFactory.getLogger(CorrectorDeTomas.class);
    private static final long TAMANO_MAXIMO_FOTO = 30L * 1024 * 1024;
    /** Igual a la columna {@code plano.negativos_correccion}. */
    static final int MAXIMO_CARACTERES_NEGATIVOS = 300;

    /**
     * Lo necesario para reescribir la toma, leído dentro de una transacción.
     *
     * @param modelo el modelo de texto del video.
     */
    public record Pedido(Long videoId, Long negocioId, ModeloIA modelo, PaqueteNicho paquete, String toma,
            String descripcionToma, String correcciones, String claveFoto) {
    }

    /** @param negativos lo que no debe aparecer, en inglés, para el prompt negativo; vacío si no hay. */
    public record TomaCorregida(String toma, String descripcionToma, String negativos) {
    }

    private final FabricaDeProveedores proveedores;
    private final ProveedorDeSecretos secretos;
    private final AvisoDeIntegracion aviso;
    private final ServicioCostos costos;
    private final VideoRepositorio videos;
    private final AlmacenDeArchivos almacen;
    private final PreparadorDeFotos preparador;
    private final ObjectMapper json;
    private final TransactionTemplate transaccion;

    public CorrectorDeTomas(FabricaDeProveedores proveedores, ProveedorDeSecretos secretos,
            AvisoDeIntegracion aviso, ServicioCostos costos, VideoRepositorio videos, AlmacenDeArchivos almacen,
            PreparadorDeFotos preparador, ObjectMapper json, PlatformTransactionManager transacciones) {
        this.proveedores = proveedores;
        this.secretos = secretos;
        this.aviso = aviso;
        this.costos = costos;
        this.videos = videos;
        this.almacen = almacen;
        this.preparador = preparador;
        this.json = json;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /**
     * Arma el pedido de un plano con correcciones pendientes. Se llama dentro de una transacción: inicializa el
     * modelo de texto del video para usarlo después, fuera de ella.
     */
    static Pedido pedido(Video video, Plano plano, PaqueteNicho paquete) {
        // Los videos anteriores al guion flexible no tienen toma escrita: se parte del movimiento del catálogo.
        Optional<Movimientos.Movimiento> movimiento = Optional.ofNullable(plano.getMovimiento())
                .flatMap(paquete.movimientos()::buscar);
        String toma = plano.getToma() != null ? plano.getToma()
                : movimiento.map(Movimientos.Movimiento::instruccion).orElse("");
        String descripcion = plano.getDescripcionToma() != null ? plano.getDescripcionToma()
                : movimiento.map(Movimientos.Movimiento::descripcion).orElse("");
        ModeloIA modelo = (ModeloIA) Hibernate.unproxy(video.getModeloTextoUsado());
        return new Pedido(video.getId(), video.getNegocioId(), modelo, paquete, toma, descripcion,
                plano.getCorrecciones(), plano.getClaveFoto());
    }

    /** @return la toma nueva; vacía si el modelo no está disponible o su respuesta no sirve (se avisa o se registra). */
    public Optional<TomaCorregida> corregir(Pedido pedido) {
        ModeloIA modelo = pedido.modelo();
        Optional<Guionista> guionista = proveedores.guionista(modelo);
        if (guionista.isEmpty()) {
            aviso.avisar(modelo, ErrorDeProveedor.Tipo.CONFIGURACION_INVALIDA,
                    "El modelo no está activo, no es de texto o su adaptador no existe");
            return Optional.empty();
        }
        Optional<String> credencial = secretos.obtener(modelo.getNombreSecreto());
        if (credencial.isEmpty()) {
            aviso.avisar(modelo, ErrorDeProveedor.Tipo.CREDENCIAL_INVALIDA,
                    "No hay credencial configurada para el secreto " + modelo.getNombreSecreto());
            return Optional.empty();
        }
        RespuestaDelModelo respuesta;
        try {
            List<ImagenAdjunta> foto = List.of(new ImagenAdjunta(preparador.paraLaIa(leerFoto(pedido.claveFoto())),
                    "image/jpeg"));
            respuesta = guionista.get().escribir(modelo, credencial.get(), instrucciones(pedido), esquema(), foto);
        } catch (ErrorDeProveedor e) {
            aviso.avisar(modelo, e.getTipo(), e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.error("No se pudo corregir la toma de un plano del video {}", pedido.videoId(), e);
            return Optional.empty();
        }
        // El proveedor cobra la respuesta aunque no sirva (RN-32).
        registrarCosto(pedido);
        Optional<TomaCorregida> corregida = leer(respuesta);
        if (corregida.isEmpty()) {
            log.info("La toma corregida del video {} no sirve: {}", pedido.videoId(),
                    respuesta.problema() != null ? respuesta.problema() : respuesta.texto());
        }
        return corregida;
    }

    private static String instrucciones(Pedido pedido) {
        // Las correcciones van al final: si el negocio escribió un marcador, no se vuelve a reemplazar.
        return pedido.paquete().instruccionesCorreccion()
                .replace("{toma}", pedido.toma())
                .replace("{descripcionToma}", pedido.descripcionToma())
                .replace("{correcciones}", pedido.correcciones());
    }

    private String esquema() {
        Map<String, Object> texto = Map.of("type", "string");
        // En orden fijo: el modelo responde los campos en el orden del esquema (primero la toma).
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("toma", texto);
        propiedades.put("descripcionToma", texto);
        propiedades.put("negativos", texto);
        return json.writeValueAsString(Map.of(
                "type", "object",
                "properties", propiedades,
                "required", List.of("toma", "descripcionToma", "negativos")));
    }

    private Optional<TomaCorregida> leer(RespuestaDelModelo respuesta) {
        if (respuesta.texto() == null) {
            return Optional.empty();
        }
        JsonNode raiz;
        try {
            raiz = json.readTree(respuesta.texto());
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (raiz == null || !raiz.isObject() || !raiz.path("toma").isString()
                || !raiz.path("descripcionToma").isString()) {
            return Optional.empty();
        }
        String toma = raiz.path("toma").asString().strip();
        String descripcion = raiz.path("descripcionToma").asString().strip();
        if (toma.isEmpty() || toma.length() > ValidadorDeGuion.MAXIMO_CARACTERES_TOMA || descripcion.isEmpty()) {
            return Optional.empty();
        }
        if (descripcion.length() > ValidadorDeGuion.MAXIMO_CARACTERES_DESCRIPCION_TOMA) {
            descripcion = descripcion.substring(0, ValidadorDeGuion.MAXIMO_CARACTERES_DESCRIPCION_TOMA).strip();
        }
        return Optional.of(new TomaCorregida(toma, descripcion, negativos(raiz.path("negativos"))));
    }

    /** Los negativos en una línea y dentro del largo de la columna; si no vienen, no hay. */
    private static String negativos(JsonNode nodo) {
        if (!nodo.isString()) {
            return null;
        }
        String limpio = nodo.asString().strip().replaceAll("\\s+", " ");
        if (limpio.length() > MAXIMO_CARACTERES_NEGATIVOS) {
            // Se corta en la última coma que entra, para no dejar una palabra a medias.
            limpio = limpio.substring(0, MAXIMO_CARACTERES_NEGATIVOS);
            int coma = limpio.lastIndexOf(',');
            limpio = (coma > 0 ? limpio.substring(0, coma) : limpio).strip();
        }
        return limpio.isEmpty() ? null : limpio;
    }

    private void registrarCosto(Pedido pedido) {
        transaccion.executeWithoutResult(estado -> videos.bloquear(pedido.videoId()).ifPresent(video ->
                video.sumarCosto(costos.registrarSolicitud(pedido.negocioId(), pedido.videoId(), pedido.modelo(),
                        ConceptoCosto.GUION))));
    }

    private byte[] leerFoto(String clave) {
        return almacen.leer(clave, TAMANO_MAXIMO_FOTO).map(ArchivoAlmacenado::contenido)
                .orElseThrow(() -> new IllegalStateException("No está la foto " + clave));
    }
}
