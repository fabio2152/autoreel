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
     * Continúa un clip que ya generó este mismo proveedor (plano extendido, 2026-11-25). La tarea se consulta con
     * {@link #consultar}; el resultado es el clip completo (el original más la extensión).
     *
     * @throws ErrorDeProveedor si el modelo no admite extender clips o el proveedor no responde.
     */
    default String extender(ModeloIA modelo, String credencial, SolicitudDeExtension solicitud) {
        throw new ErrorDeProveedor(ErrorDeProveedor.Tipo.CONFIGURACION_INVALIDA,
                "El modelo " + modelo.getNombre() + " no admite extender clips");
    }

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
     * @param fotoFinal la foto en la que debe terminar el clip (transiciones entre fotos, 2026-11-25); vacía si el
     *                  clip termina libre. Solo la usan los proveedores que la admiten (Veo).
     */
    record SolicitudDeClip(byte[] foto, String tipoFoto, String prompt, String negativos, int segundos,
            byte[] fotoFinal) {

        public SolicitudDeClip(byte[] foto, String tipoFoto, String prompt, String negativos, int segundos) {
            this(foto, tipoFoto, prompt, negativos, segundos, null);
        }
    }

    /**
     * @param video     el clip que generó el proveedor, tal cual (MP4).
     * @param prompt    la toma completa, como en {@link SolicitudDeClip}.
     * @param negativos lo que nunca debe aparecer.
     */
    record SolicitudDeExtension(byte[] video, String prompt, String negativos) {
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
