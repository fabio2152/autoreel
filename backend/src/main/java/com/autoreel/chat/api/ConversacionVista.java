package com.autoreel.chat.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.autoreel.chat.dominio.AutorMensaje;
import com.autoreel.chat.dominio.EstadoConversacion;
import com.autoreel.nicho.dominio.GuionChat.TipoPaso;
import com.autoreel.nicho.dominio.Opcion;

/**
 * Todo lo que la pantalla del chat necesita en una sola respuesta.
 *
 * @param paso                pregunta que espera respuesta; vacío si la conversación terminó.
 * @param procesarEnMs        milisegundos hasta que se cierre la respuesta agrupada; vacío si no hay mensajes
 *                            pendientes. El frontend llama a {@code procesar} cuando llega a cero.
 * @param productos           carrusel, solo en el paso de producto (V11: activos, en el catálogo y completos).
 * @param productosIncompletos productos del catálogo que no aparecen por faltarles fotos.
 * @param resumen             respuestas legibles al terminar.
 * @param videoId             el video que nació al terminar la conversación; vacío mientras está en curso.
 */
public record ConversacionVista(Long id, EstadoConversacion estado, Paso paso, List<Mensaje> mensajes,
        Long procesarEnMs, Map<String, Object> respuestas, String aviso, int maximoCaracteres,
        List<ProductoCarrusel> productos, int productosIncompletos, List<Resumen> resumen, Long videoId) {

    /**
     * @param omitible    se puede responder "No, gracias" (no es obligatoria en este momento).
     * @param sugerencia  texto que se ofrece como punto de partida (la descripción del producto).
     */
    public record Paso(String id, TipoPaso tipo, String texto, List<Opcion> opciones, boolean obligatoria,
            boolean omitible, String sugerencia, String ejemplo, boolean admiteTexto) {
    }

    /** @param pendiente texto del usuario que todavía puede agruparse con el siguiente. */
    public record Mensaje(Long id, AutorMensaje autor, String texto, Instant fechaHora, boolean pendiente) {
    }

    public record ProductoCarrusel(Long id, String nombre, String descripcion, String foto) {
    }

    public record Resumen(String pregunta, String respuesta) {
    }
}
