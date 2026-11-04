package com.autoreel.video.dominio;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.autoreel.nicho.dominio.Texto;

/**
 * Todo lo que el guion puede usar: el perfil del negocio, el producto y las respuestas del chat. Es también la
 * lista de fuentes de la RN-22: un número o una promoción que no aparezca aquí es inventado.
 *
 * @param tipoVideo          valor del tipo de video (por ejemplo, PROMOCION).
 * @param tipoVideoTexto     el mismo, como lo vio el usuario ("Promoción").
 * @param precio             precio o promoción escrito por el usuario; vacío si no escribió.
 * @param palabrasProhibidas palabras o temas que el negocio no quiere en sus videos.
 */
public record DatosDelGuion(String tipoVideo, String tipoVideoTexto, String producto, String descripcionProducto,
        String especial, String precio, String extra, String nombreNegocio, String estilo, String estiloTexto,
        String especialidad, String publico, String ciudad, String distrito, String distintivo, String horario,
        String whatsapp, List<String> canales, List<String> palabrasProhibidas) {

    public DatosDelGuion {
        precio = precio == null || precio.isBlank() ? null : precio.strip();
        canales = canales == null ? List.of() : List.copyOf(canales);
        palabrasProhibidas = palabrasProhibidas == null ? List.of() : List.copyOf(palabrasProhibidas);
    }

    /** Separa lo que escribió el negocio en el onboarding ("dieta, light; vegano") en frases. */
    public static List<String> separarPalabrasProhibidas(String texto) {
        if (texto == null) {
            return List.of();
        }
        return Arrays.stream(texto.split("[,;\\n]")).map(String::strip).filter(frase -> !frase.isEmpty()).toList();
    }

    /** Textos de donde pueden salir números, precios y promociones (RN-22). */
    public List<String> fuentes() {
        return Stream.of(tipoVideoTexto, producto, descripcionProducto, especial, precio, extra, nombreNegocio,
                especialidad, distintivo, horario, whatsapp, ciudad, distrito).filter(Objects::nonNull).toList();
    }

    /** El hashtag del distrito, sin "#": "San Isidro" → "sanisidro". */
    public String distritoHashtag() {
        return Texto.normalizar(distrito).replace(" ", "");
    }
}
