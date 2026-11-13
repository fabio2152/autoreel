package com.autoreel.video.api;

import java.time.Instant;
import java.util.List;

import com.autoreel.catalogo.dominio.TipoFoto;
import com.autoreel.video.dominio.EstadoPlano;
import com.autoreel.video.dominio.EstadoVideo;

/**
 * El video en todas sus etapas (RF-21 a RF-25): storyboard, generación, revisión y aprobado. Mientras el guion está
 * pendiente, {@code planos} viene vacío.
 *
 * @param avisoDeRespaldo     texto para el usuario si se usó el guion de respaldo; vacío si lo escribió la IA.
 * @param fotosDelProducto    las fotos del producto, para cambiar la de un plano.
 * @param maximoPublicacion   caracteres de la descripción con sus hashtags (RN-24).
 * @param generacion          consumo, regeneraciones y video final.
 */
public record VideoVista(Long id, EstadoVideo estado, String tipoVideo, ProductoDelVideo producto,
        PlanDelVideo plan, String avisoDeRespaldo, List<PlanoVista> planos, CierreVista cierre, String descripcion,
        List<String> hashtags, int maximoPublicacion, List<FotoVista> fotosDelProducto, Instant fechaCreacion,
        GeneracionVista generacion) {

    public record ProductoDelVideo(Long id, String nombre) {
    }

    public record PlanDelVideo(String nombre, int videosPorDia, int regeneracionesPorVideo) {
    }

    /**
     * Planos con foto (1 a 3).
     *
     * @param estado       el clip: vacío en el storyboard; PENDIENTE, GENERANDO, LISTO o RESPALDO después.
     * @param usoRespaldo  solo en videos creados antes del 2026-11-06: el plano usó el acercamiento de FFmpeg (ADR 0020).
     */
    public record PlanoVista(int orden, String nombre, int segundos, FotoVista foto, MovimientoVista movimiento,
            String texto, int maxPalabras, EstadoPlano estado, boolean usoRespaldo) {
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

    /**
     * @param videosHoy             videos aprobados hoy (día de Lima) y si se puede aprobar otro (RN-26).
     * @param urlVideo              el MP4 final para reproducir; vacío hasta que esté listo.
     * @param regeneracionesUsadas  de este video (RN-28).
     * @param iaDisponible          si el modelo de video se puede usar ahora; si no, no se ofrece regenerar.
     * @param avisoRevision         por ejemplo, que no se pudo aplicar un cambio.
     * @param motivoError           si el video terminó en ERROR, en palabras simples.
     * @param puedeReintentar       si el video falló al generarse y se puede volver a intentar.
     */
    public record GeneracionVista(ConsumoVista videosHoy, String urlVideo, int regeneracionesUsadas,
            boolean iaDisponible, String avisoRevision, String motivoError, boolean puedeReintentar) {
    }

    public record ConsumoVista(int usados, int porDia, boolean puedeAprobar) {
    }
}
