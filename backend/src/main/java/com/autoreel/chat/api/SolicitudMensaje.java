package com.autoreel.chat.api;

import java.util.Map;

import com.autoreel.catalogo.dominio.TipoFoto;

/**
 * Respuesta del usuario. Lleva el paso al que responde y exactamente una forma de respuesta.
 *
 * @param paso            si no coincide con el paso actual, la pantalla estaba desactualizada (409 PASO_CAMBIADO).
 * @param texto           respuesta libre (se agrupa con los mensajes de los próximos 4 s).
 * @param opcion          botón elegido.
 * @param productoId      producto del catálogo.
 * @param productoNuevo   producto subido en el chat, con sus tres fotos.
 * @param omitir          "No, gracias" en una pregunta opcional.
 */
public record SolicitudMensaje(String paso, String texto, String opcion, Long productoId,
        ProductoNuevo productoNuevo, Boolean omitir) {

    /** @param guardarEnCatalogo RN-14: si es false, el producto solo se usa en este video. */
    public record ProductoNuevo(String nombre, String descripcion, Map<TipoFoto, String> fotos,
            boolean guardarEnCatalogo) {
    }

    public int formasDeRespuesta() {
        int formas = 0;
        if (texto != null) formas++;
        if (opcion != null) formas++;
        if (productoId != null) formas++;
        if (productoNuevo != null) formas++;
        if (Boolean.TRUE.equals(omitir)) formas++;
        return formas;
    }
}
