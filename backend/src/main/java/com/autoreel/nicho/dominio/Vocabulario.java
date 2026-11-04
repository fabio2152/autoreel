package com.autoreel.nicho.dominio;

import java.util.List;
import java.util.Map;

/**
 * Reglas de vocabulario del nicho (sección 7.6).
 *
 * @param tonos                                    tono de los textos por estilo del negocio.
 * @param prohibidasSalvoQueLasDeclareElProducto   afirmaciones de salud o nutrición: solo valen si la descripción
 *                                                 del producto las dice.
 * @param prohibidas                               superlativos absolutos y promesas de entrega o tiempos.
 * @param palabrasDePromocion                      solo pueden aparecer si el negocio escribió una promoción (RN-22).
 */
public record Vocabulario(Map<String, String> tonos, List<String> prohibidasSalvoQueLasDeclareElProducto,
        List<String> prohibidas, List<String> palabrasDePromocion) {

    public Vocabulario {
        prohibidasSalvoQueLasDeclareElProducto = List.copyOf(prohibidasSalvoQueLasDeclareElProducto);
        prohibidas = List.copyOf(prohibidas);
        palabrasDePromocion = List.copyOf(palabrasDePromocion);
    }

    public String tono(String estilo) {
        return tonos.getOrDefault(estilo, "cálido y cercano");
    }
}
