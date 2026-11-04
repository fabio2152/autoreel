package com.autoreel.chat.dominio;

import java.util.Map;

/**
 * Se publica dentro de la transacción en la que el chat termina, para que el video nazca junto con ella.
 *
 * @param respuestas respuestas reunidas por el chat, por id de paso del guion del chat.
 */
public record ConversacionCompletada(Long conversacionId, Long negocioId, Map<String, Object> respuestas) {
}
