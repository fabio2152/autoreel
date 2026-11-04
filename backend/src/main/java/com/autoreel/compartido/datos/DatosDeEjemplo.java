package com.autoreel.compartido.datos;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.autoreel.admin.api.SolicitudCrearNegocio;
import com.autoreel.admin.aplicacion.ServicioGestionNegocios;
import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos;
import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import com.autoreel.auth.dominio.ReglasDeContrasena;
import com.autoreel.auth.dominio.Usuario;
import com.autoreel.auth.infraestructura.UsuarioRepositorio;
import com.autoreel.catalogo.api.SolicitudProducto;
import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.facturacion.dominio.Plan;
import com.autoreel.facturacion.infraestructura.PlanRepositorio;
import com.autoreel.negocio.aplicacion.ServicioPerfil;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sección 16: en el perfil de desarrollo crea "Pastelería Dulce Encanto" (Miraflores), con su perfil completo y tres
 * productos con imágenes de relleno generadas aquí. Usa los mismos servicios que la aplicación, así que sus datos
 * pasan por las mismas validaciones. Se crea una sola vez (por correo) y, si algo falla, solo avisa.
 */
@Component
@Profile("dev")
@Order(10)
public class DatosDeEjemplo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDeEjemplo.class);
    private static final Color ROSA = new Color(0xBE123C);
    private static final Color CREMA = new Color(0xFDE68A);

    private record ProductoDeEjemplo(String nombre, String descripcion, List<String> temporadas, Color color) {
    }

    private static final List<ProductoDeEjemplo> PRODUCTOS = List.of(
            new ProductoDeEjemplo("Torta Tres Leches de Fresa",
                    "Bizcocho húmedo de tres leches, relleno de fresas frescas y cubierto con crema batida.",
                    List.of("VERANO"), new Color(0xF472B6)),
            new ProductoDeEjemplo("Cheesecake de Maracuyá",
                    "Base de galleta, crema de queso suave y una capa de maracuyá natural.",
                    List.of("TODO_EL_ANIO"), new Color(0xF59E0B)),
            new ProductoDeEjemplo("Alfajores de Maicena",
                    "Alfajores que se deshacen en la boca, rellenos de manjar blanco y con azúcar impalpable.",
                    List.of("TODO_EL_ANIO"), new Color(0xA16207)));

    private final UsuarioRepositorio usuarios;
    private final PlanRepositorio planes;
    private final ServicioGestionNegocios negocios;
    private final ServicioPerfil perfiles;
    private final ServicioCatalogo catalogo;
    private final ServicioDeArchivos archivos;
    private final AlmacenDeArchivos almacen;
    private final PasswordEncoder codificador;
    private final TransactionTemplate transaccion;
    private final Clock reloj;
    private final String correo;
    private final String contrasena;

    public DatosDeEjemplo(UsuarioRepositorio usuarios, PlanRepositorio planes, ServicioGestionNegocios negocios,
            ServicioPerfil perfiles, ServicioCatalogo catalogo, ServicioDeArchivos archivos, AlmacenDeArchivos almacen,
            PasswordEncoder codificador, TransactionTemplate transaccion, Clock reloj,
            @Value("${autoreel.demo.correo:}") String correo, @Value("${autoreel.demo.contrasena:}") String contrasena) {
        this.usuarios = usuarios;
        this.planes = planes;
        this.negocios = negocios;
        this.perfiles = perfiles;
        this.catalogo = catalogo;
        this.archivos = archivos;
        this.almacen = almacen;
        this.codificador = codificador;
        this.transaccion = transaccion;
        this.reloj = reloj;
        this.correo = correo;
        this.contrasena = contrasena;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (correo.isBlank() || contrasena.isBlank()) {
            log.warn("Sin DEMO_NEGOCIO_EMAIL y DEMO_NEGOCIO_PASSWORD no se crea el negocio de ejemplo.");
            return;
        }
        if (usuarios.existsByCorreo(Usuario.normalizarCorreo(correo))) {
            return;
        }
        try {
            ReglasDeContrasena.validar(contrasena, contrasena);
            transaccion.executeWithoutResult(estado -> crear());
            log.info("Negocio de ejemplo creado ({})", Usuario.normalizarCorreo(correo));
        } catch (RuntimeException e) {
            log.warn("No se pudo crear el negocio de ejemplo: {}", e.getMessage());
        }
    }

    private void crear() {
        Plan lite = planes.findAll().stream().filter(plan -> plan.getNombre().equals("Lite")).findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe el plan Lite"));
        Long negocioId = negocios.crear(new SolicitudCrearNegocio(RegistroDeNichos.PASTELERIA,
                "Pastelería Dulce Encanto", correo, lite.getId(), new BigDecimal("49.00"),
                LocalDate.now(reloj).plusDays(30))).negocioId();
        Usuario usuario = usuarios.buscarPorCorreo(Usuario.normalizarCorreo(correo)).orElseThrow();
        usuario.cambiarContrasena(codificador.encode(contrasena));

        Map<String, Object> perfil = new LinkedHashMap<>();
        perfil.put("logo", subir(negocioId, PropositoArchivo.LOGO, logo(), "image/png"));
        perfil.put("colorPrimario", "#be123c");
        perfil.put("colorSecundario", "#fde68a");
        perfil.put("estilo", "CASERO");
        perfil.put("respuestas", Map.of(
                "especialidad", List.of("TORTAS_PERSONALIZADAS", "POSTRES_INDIVIDUALES"),
                "publico", List.of("FAMILIAS", "CELEBRACIONES"),
                "ciudad", "Lima",
                "distrito", "Miraflores",
                "canales", List.of("WHATSAPP", "TIENDA"),
                "whatsapp", "987654321",
                "horario", "Lunes a sábado de 9 a. m. a 8 p. m.",
                "distintivo", "Recetas caseras con fruta de temporada"));
        perfiles.actualizar(negocioId, perfil);
        perfiles.completarOnboarding(negocioId);

        for (ProductoDeEjemplo producto : PRODUCTOS) {
            Map<TipoFoto, String> fotos = new EnumMap<>(TipoFoto.class);
            for (TipoFoto tipo : TipoFoto.values()) {
                fotos.put(tipo, subir(negocioId, PropositoArchivo.FOTO_PRODUCTO, foto(producto, tipo), "image/jpeg"));
            }
            catalogo.crear(negocioId, new SolicitudProducto(producto.nombre(), producto.descripcion(),
                    producto.temporadas(), fotos));
        }
    }

    /** Deja el archivo en una clave de subida, como si lo hubiera subido el navegador. */
    private String subir(Long negocioId, PropositoArchivo proposito, byte[] contenido, String tipo) {
        String clave = archivos.firmarSubida(negocioId, proposito, tipo, contenido.length).clave();
        almacen.guardar(clave, contenido, tipo);
        return clave;
    }

    private static byte[] logo() {
        BufferedImage imagen = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = lienzo(imagen);
        g.setColor(ROSA);
        g.fillOval(16, 16, 480, 480);
        g.setColor(CREMA);
        g.setStroke(new BasicStroke(14));
        g.drawOval(56, 56, 400, 400);
        g.setFont(new Font(Font.SERIF, Font.BOLD, 150));
        centrar(g, "DE", 300);
        g.dispose();
        return codificar(imagen, "png");
    }

    private static byte[] foto(ProductoDeEjemplo producto, TipoFoto tipo) {
        BufferedImage imagen = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo(imagen);
        g.setPaint(new GradientPaint(0, 0, producto.color().brighter(), 0, 1920, producto.color().darker()));
        g.fillRect(0, 0, 1080, 1920);
        g.setColor(new Color(255, 255, 255, 230));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
        centrar(g, producto.nombre(), 860);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 56));
        centrar(g, switch (tipo) {
            case ENTERO -> "Foto: producto entero";
            case DETALLE -> "Foto: detalle";
            case PORCION -> "Foto: porción";
        }, 960);
        g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 40));
        centrar(g, "Imagen de ejemplo", 1800);
        g.dispose();
        return codificar(imagen, "jpg");
    }

    private static Graphics2D lienzo(BufferedImage imagen) {
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void centrar(Graphics2D g, String texto, int y) {
        int ancho = g.getFontMetrics().stringWidth(texto);
        g.drawString(texto, (g.getDeviceConfiguration().getBounds().width - ancho) / 2, y);
    }

    private static byte[] codificar(BufferedImage imagen, String formato) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, formato, salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
