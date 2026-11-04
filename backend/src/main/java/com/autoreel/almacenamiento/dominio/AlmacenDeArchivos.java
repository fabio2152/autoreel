package com.autoreel.almacenamiento.dominio;

import java.time.Duration;
import java.util.Optional;

/**
 * Puerto de almacenamiento de archivos (sección 12.2). En producción lo implementa Amazon S3; en desarrollo, un
 * servicio compatible con S3 (SeaweedFS). El resto del sistema no sabe cuál se usa.
 */
public interface AlmacenDeArchivos {

    /** URL para que el navegador suba el archivo directamente, con el tipo y el tamaño exactos. */
    String urlDeSubida(String clave, String tipoContenido, long tamano, Duration vigencia);

    /** URL temporal para leer un archivo privado. */
    String urlDeDescarga(String clave, Duration vigencia);

    /** URL temporal que el navegador descarga como archivo con ese nombre (RF-25). */
    String urlDeDescargaComoAdjunto(String clave, String nombreArchivo, Duration vigencia);

    /**
     * Lee el archivo si existe. Si pesa más de {@code bytesMaximos}, no descarga el contenido: devuelve solo el
     * tamaño, para que quien llama rechace el archivo sin leerlo entero.
     */
    Optional<ArchivoAlmacenado> leer(String clave, long bytesMaximos);

    /**
     * Copia el archivo solo si no cambió desde que se leyó (misma etiqueta). Devuelve false si cambió o ya no existe.
     */
    boolean copiarSiNoCambio(String origen, String destino, String etiqueta);

    void guardar(String clave, byte[] contenido, String tipoContenido);

    void eliminar(String clave);
}
