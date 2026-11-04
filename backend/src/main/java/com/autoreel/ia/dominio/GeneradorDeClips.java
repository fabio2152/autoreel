package com.autoreel.ia.dominio;

/**
 * Puerto del modelo de video (sección 8.2): anima una foto con una instrucción de movimiento. La generación es
 * asíncrona: se inicia una tarea y se consulta hasta que termina. Cada proveedor tiene su adaptador.
 */
public interface GeneradorDeClips {

    /** Identificador del adaptador, igual al campo {@code adaptador} de {@link ModeloIA} (por ejemplo, VEO). */
    String adaptador();

    /**
     * Envía la tarea.
     *
     * @return el identificador de la tarea en el proveedor, para consultarla.
     * @throws ErrorDeProveedor si la credencial no sirve, la configuración es inválida o el proveedor no responde.
     */
    String iniciar(ModeloIA modelo, String credencial, SolicitudDeClip solicitud);

    /** Estado de una tarea enviada. */
    EstadoDeClip consultar(ModeloIA modelo, String credencial, String idTarea);

    /**
     * @param foto         la foto ya normalizada (JPEG vertical 9:16, sin metadatos).
     * @param instruccion  el movimiento, en inglés (de {@code movimientos.json}).
     * @param prohibiciones lo que nunca debe aparecer; va como instrucción negativa si el proveedor la admite.
     * @param segundos     duración pedida del clip.
     */
    record SolicitudDeClip(byte[] foto, String tipoFoto, String instruccion, String prohibiciones, int segundos) {
    }

    sealed interface EstadoDeClip permits EnCurso, Listo, Fallido {
    }

    record EnCurso() implements EstadoDeClip {
    }

    record Listo(byte[] video, String tipoContenido) implements EstadoDeClip {
    }

    /** El proveedor terminó sin clip (filtro de contenido o error de la tarea). */
    record Fallido(String motivo) implements EstadoDeClip {
    }
}
