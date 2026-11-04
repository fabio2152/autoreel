package com.autoreel.video.api;

import java.time.Instant;
import java.util.List;

import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.video.dominio.EstadoVideo;

/**
 * El storyboard (RF-21, RF-22): todo lo que la pantalla necesita en una sola respuesta. Mientras el guion está
 * pendiente, {@code planos} viene vacío.
 *
 * @param avisoDeRespaldo     texto para el usuario si se usó el guion de respaldo; vacío si lo escribió la IA.
 * @param fotosDelProducto    las fotos del producto, para cambiar la de un plano.
 * @param maximoPublicacion   caracteres de la descripción con sus hashtags (RN-24).
 */
public record VideoVista(Long id, EstadoVideo estado, String tipoVideo, ProductoDelVideo producto,
        PlanDelVideo plan, String avisoDeRespaldo, List<PlanoVista> planos, CierreVista cierre, String descripcion,
        List<String> hashtags, int maximoPublicacion, List<FotoVista> fotosDelProducto, Instant fechaCreacion) {

    public record ProductoDelVideo(Long id, String nombre) {
    }

    /** @param videosPorDia el consumo real del día llega en la fase 5. */
    public record PlanDelVideo(String nombre, int videosPorDia) {
    }

    /** Planos con foto (1 a 3). */
    public record PlanoVista(int orden, String nombre, int segundos, FotoVista foto, MovimientoVista movimiento,
            String texto, int maxPalabras) {
    }

    /** @param tipo vacío si el usuario subió una foto que no es del producto. */
    public record FotoVista(TipoFoto tipo, String url) {
    }

    /** @param descripcion el movimiento en palabras simples. */
    public record MovimientoVista(String id, String descripcion) {
    }

    /**
     * El plano final, armado por el sistema: precio, llamado a la acción y ubicación sobre la foto del local o los
     * colores de la marca (sección 7.4).
     *
     * @param whatsapp número a mostrar si el negocio vende por WhatsApp.
     */
    public record CierreVista(int orden, String nombre, int segundos, String precio, int maximoCaracteresPrecio,
            String llamado, String ubicacion, String whatsapp, String logo, String colorPrimario,
            String colorSecundario, String fotoLocal) {
    }
}
