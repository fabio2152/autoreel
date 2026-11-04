package com.autoreel.almacenamiento.dominio;

/**
 * @param contenido vacío ({@code null}) cuando el archivo superó el tamaño máximo pedido y no se descargó.
 * @param etiqueta  identifica esta versión del archivo (ETag de S3): sirve para copiarlo solo si no cambió.
 */
public record ArchivoAlmacenado(byte[] contenido, String tipoContenido, long tamano, String etiqueta) {
}
