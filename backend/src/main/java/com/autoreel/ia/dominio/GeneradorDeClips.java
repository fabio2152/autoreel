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
     * RF-11, "Probar conexión": verifica la credencial (y el modelo, si el proveedor lo permite) sin generar un clip,
     * que costaría.
     *
     * @throws ErrorDeProveedor si no funciona; el mensaje dice por qué y nunca incluye la credencial.
     */
    void probar(ModeloIA modelo, String credencial);

    /**
     * @param foto         la foto ya normalizada (JPEG vertical 9:16, sin metadatos).
     * @param prompt    el prompt completo en inglés, armado con la plantilla de toma, el movimiento y las prohibiciones
     *                  del paquete de nicho; el adaptador lo envía tal cual.
     * @param negativos lo que nunca debe aparecer, como lista de sustantivos; va como prompt negativo si el proveedor
     *                  lo admite.
     * @param segundos  duración pedida del clip.
     */
    record SolicitudDeClip(byte[] foto, String tipoFoto, String prompt, String negativos, int segundos) {
    }

    sealed interface EstadoDeClip permits EnCurso, Listo, Fallido {
    }

    record EnCurso() implements EstadoDeClip {
    }

    /**
     * @param segundosGenerados duración que cobra el proveedor, si la informa; vacía si se cobra la duración pedida.
     */
    record Listo(byte[] video, String tipoContenido, Double segundosGenerados) implements EstadoDeClip {
    }

    /** El proveedor terminó sin clip (filtro de contenido o error de la tarea). */
    record Fallido(String motivo) implements EstadoDeClip {
    }
}
