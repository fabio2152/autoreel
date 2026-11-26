package com.autoreel.video.api;

/**
 * "Regenerar plano", desde la edición del plano en la revisión.
 *
 * @param texto el texto en pantalla nuevo, si cambió en la misma edición; vacío si no. El clip nuevo se compone con él.
 */
public record SolicitudRegeneracion(String texto) {
}
