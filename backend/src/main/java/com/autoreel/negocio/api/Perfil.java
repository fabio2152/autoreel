package com.autoreel.negocio.api;

import java.util.List;
import java.util.Map;

/**
 * Perfil del negocio tal como lo ve el frontend. Las imágenes llevan una URL temporal de lectura.
 *
 * @param avisos avisos de la última modificación (por ejemplo, LOGO_SIN_TRANSPARENCIA o FOTO_LOCAL_HORIZONTAL).
 */
public record Perfil(String nombre, String nicho, Imagen logo, boolean logoSinTransparencia, String colorPrimario,
        String colorSecundario, String estilo, Imagen fotoLocal, Map<String, Object> respuestas,
        boolean onboardingCompleto, List<String> avisos) {

    public record Imagen(String clave, String url) {
    }
}
