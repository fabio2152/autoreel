package com.autoreel.catalogo.aplicacion;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos;
import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos.ImagenGuardada;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import com.autoreel.catalogo.api.ProductoVista;
import com.autoreel.catalogo.api.SolicitudProducto;
import com.autoreel.catalogo.dominio.EstadoProducto;
import com.autoreel.catalogo.dominio.FotoProducto;
import com.autoreel.catalogo.dominio.Producto;
import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.catalogo.infraestructura.ProductoRepositorio;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import com.autoreel.negocio.dominio.Negocio;
import com.autoreel.negocio.infraestructura.NegocioRepositorio;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de productos del negocio (RF-18). Todas las operaciones reciben el negocio de la sesión y un producto
 * de otro negocio se trata como inexistente (RNF-05, V08).
 */
@Service
public class ServicioCatalogo {

    public static final String AVISO_FOTO_HORIZONTAL = "FOTO_HORIZONTAL";
    static final int LONGITUD_MAXIMA_NOMBRE = 50;
    static final int LONGITUD_MAXIMA_DESCRIPCION = 200;
    static final String TEMPORADA_POR_DEFECTO = "TODO_EL_ANIO";

    private final ProductoRepositorio productos;
    private final NegocioRepositorio negocios;
    private final RegistroDeNichos nichos;
    private final ServicioDeArchivos archivos;
    private final Clock reloj;

    public ServicioCatalogo(ProductoRepositorio productos, NegocioRepositorio negocios, RegistroDeNichos nichos,
            ServicioDeArchivos archivos, Clock reloj) {
        this.productos = productos;
        this.negocios = negocios;
        this.nichos = nichos;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<ProductoVista> listar(Long negocioId, EstadoProducto estado) {
        return productos.listarDeNegocio(negocioId, estado).stream().map(p -> vista(p, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public ProductoVista obtener(Long negocioId, Long productoId) {
        return vista(producto(negocioId, productoId), List.of());
    }

    @Transactional
    public ProductoVista crear(Long negocioId, SolicitudProducto solicitud) {
        Negocio negocio = negocios.findById(negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos tu negocio."));
        PaqueteNicho paquete = nichos.de(negocio.getNicho());
        Producto producto = new Producto(negocio, nombre(solicitud), descripcion(solicitud),
                temporadas(paquete, solicitud), reloj.instant());
        List<String> avisos = aplicarFotos(negocioId, producto, solicitud.fotos());
        return vista(productos.save(producto), avisos);
    }

    /**
     * Carrusel del chat (RF-20, V11): productos activos del catálogo con sus tres fotos, del más reciente al más
     * antiguo, y cuántos se quedaron fuera por faltarles fotos.
     */
    public record Carrusel(List<Producto> productos, int incompletos) {
    }

    @Transactional(readOnly = true)
    public Carrusel carrusel(Long negocioId) {
        List<Producto> activos = productos.listarParaCarrusel(negocioId);
        List<Producto> completos = activos.stream().filter(Producto::estaCompleto).toList();
        return new Carrusel(completos, activos.size() - completos.size());
    }

    /** Producto que puede usarse en un video del negocio: activo y completo, esté o no en el catálogo. */
    @Transactional(readOnly = true)
    public Optional<Producto> paraVideo(Long negocioId, Long productoId) {
        return productos.buscarParaVideo(productoId, negocioId).filter(Producto::disponibleParaVideo);
    }

    /** Producto ya elegido para un video, para mostrarlo aunque después se haya archivado. */
    @Transactional(readOnly = true)
    public Optional<Producto> elegidoParaVideo(Long negocioId, Long productoId) {
        return productos.buscarParaVideo(productoId, negocioId);
    }

    public String urlDeFoto(FotoProducto foto) {
        return archivos.urlDeLectura(foto.getClaveS3());
    }

    /**
     * Producto subido desde el chat (RF-20): exige las tres fotos para usarse en el video y, si el usuario no aceptó
     * guardarlo, queda fuera del catálogo (RN-14). Su temporada es "Todo el año"; puede cambiarse en el catálogo.
     */
    @Transactional
    public Producto crearDesdeChat(Long negocioId, String nombre, String descripcion, Map<TipoFoto, String> fotos,
            boolean guardarEnCatalogo) {
        if (fotos == null || Arrays.stream(TipoFoto.values()).anyMatch(tipo -> fotos.get(tipo) == null
                || fotos.get(tipo).isBlank())) {
            throw new ExcepcionDeNegocio("PRODUCTO_INCOMPLETO",
                    "Para usar el producto en tu video necesitamos sus tres fotos: entero, detalle y porción.");
        }
        Negocio negocio = negocios.findById(negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos tu negocio."));
        PaqueteNicho paquete = nichos.de(negocio.getNicho());
        SolicitudProducto solicitud = new SolicitudProducto(nombre, descripcion, List.of(TEMPORADA_POR_DEFECTO), fotos);
        Producto producto = new Producto(negocio, nombre(solicitud), descripcion(solicitud),
                temporadas(paquete, solicitud), reloj.instant());
        if (!guardarEnCatalogo) {
            producto.dejarFueraDelCatalogo();
        }
        aplicarFotos(negocioId, producto, fotos);
        return productos.save(producto);
    }

    @Transactional
    public ProductoVista actualizar(Long negocioId, Long productoId, SolicitudProducto solicitud) {
        Producto producto = producto(negocioId, productoId);
        PaqueteNicho paquete = nichos.de(producto.getNegocio().getNicho());
        producto.cambiarDatos(nombre(solicitud), descripcion(solicitud), temporadas(paquete, solicitud));
        List<String> avisos = aplicarFotos(negocioId, producto, solicitud.fotos());
        return vista(producto, avisos);
    }

    @Transactional
    public ProductoVista archivar(Long negocioId, Long productoId) {
        Producto producto = producto(negocioId, productoId);
        producto.archivar();
        return vista(producto, List.of());
    }

    @Transactional
    public ProductoVista reactivar(Long negocioId, Long productoId) {
        Producto producto = producto(negocioId, productoId);
        producto.reactivar();
        return vista(producto, List.of());
    }

    /** Cada foto: igual a la guardada, se conserva; subida nueva, se verifica; ausente, se quita. */
    private List<String> aplicarFotos(Long negocioId, Producto producto, Map<TipoFoto, String> fotos) {
        List<String> avisos = new ArrayList<>();
        for (TipoFoto tipo : TipoFoto.values()) {
            String clave = fotos == null ? null : fotos.get(tipo);
            String actual = producto.foto(tipo).map(FotoProducto::getClaveS3).orElse(null);
            if (clave == null || clave.isBlank()) {
                producto.quitarFoto(tipo);
            } else if (!clave.equals(actual)) {
                if (!archivos.esSubida(clave)) {
                    throw new ExcepcionDeNegocio("ARCHIVO_INVALIDO",
                            "La foto no corresponde a tu negocio. Vuelve a subirla.");
                }
                ImagenGuardada foto = archivos.confirmar(negocioId, PropositoArchivo.FOTO_PRODUCTO, clave);
                producto.ponerFoto(tipo, foto.clave(), foto.imagen().ancho(), foto.imagen().alto());
                if (foto.imagen().horizontal() && !avisos.contains(AVISO_FOTO_HORIZONTAL)) {
                    avisos.add(AVISO_FOTO_HORIZONTAL);
                }
            }
        }
        return avisos;
    }

    private static String nombre(SolicitudProducto solicitud) {
        String nombre = solicitud.nombre() == null ? "" : solicitud.nombre().strip();
        if (nombre.isEmpty() || nombre.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new ExcepcionDeNegocio("PRODUCTO_NOMBRE_INVALIDO",
                    "El nombre del producto debe tener entre 1 y " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        return nombre;
    }

    private static String descripcion(SolicitudProducto solicitud) {
        String descripcion = solicitud.descripcion() == null ? "" : solicitud.descripcion().strip();
        if (descripcion.isEmpty() || descripcion.length() > LONGITUD_MAXIMA_DESCRIPCION) {
            throw new ExcepcionDeNegocio("PRODUCTO_DESCRIPCION_INVALIDA",
                    "Describe el sabor, el relleno o los ingredientes en hasta " + LONGITUD_MAXIMA_DESCRIPCION
                            + " caracteres. Es lo único que usaremos para hablar de tu producto.");
        }
        return descripcion;
    }

    private static List<String> temporadas(PaqueteNicho paquete, SolicitudProducto solicitud) {
        if (solicitud.temporadas() == null || solicitud.temporadas().isEmpty()) {
            throw new ExcepcionDeNegocio("PRODUCTO_SIN_TEMPORADA", "Elige al menos una temporada.");
        }
        LinkedHashSet<String> elegidas = new LinkedHashSet<>(solicitud.temporadas());
        elegidas.stream().filter(id -> !paquete.admiteTemporada(id)).findFirst().ifPresent(id -> {
            throw new ExcepcionDeNegocio("TEMPORADA_INVALIDA", "La temporada elegida no existe.");
        });
        return new ArrayList<>(elegidas);
    }

    private Producto producto(Long negocioId, Long productoId) {
        return productos.buscarDeNegocio(productoId, negocioId)
                .orElseThrow(() -> ExcepcionDeNegocio.noEncontrado("No encontramos ese producto."));
    }

    public ProductoVista vista(Producto producto, List<String> avisos) {
        List<ProductoVista.Foto> fotos = producto.getFotos().stream()
                .sorted(Comparator.comparing(FotoProducto::getTipo))
                .map(foto -> new ProductoVista.Foto(foto.getTipo(), foto.getClaveS3(),
                        archivos.urlDeLectura(foto.getClaveS3()), foto.getAncho(), foto.getAlto(), foto.esHorizontal()))
                .toList();
        return new ProductoVista(producto.getId(), producto.getNombre(), producto.getDescripcion(),
                producto.getTemporadas(), producto.getEstado(), producto.estaCompleto(), fotos,
                producto.getFechaCreacion(), avisos);
    }
}
