package com.autoreel.video.dominio;

/** Estado del clip de un plano con IA (sección 8.2). Vacío en el storyboard y en el cierre. */
public enum EstadoPlano {
    /** Falta enviarlo al proveedor. */
    PENDIENTE,
    /** Enviado; se consulta hasta que termine. */
    GENERANDO,
    /** El clip está guardado. */
    LISTO,
    /** Usa el acercamiento hecho por FFmpeg sobre la foto (sección 8.5, RN-30). */
    RESPALDO
}
