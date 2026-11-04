package com.autoreel.video.dominio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lo que escribe el modelo de texto (o el guion de respaldo): los planos 1 a 3, la descripción de la publicación y
 * los hashtags. El cierre lo arma el sistema.
 */
public record GuionPropuesto(List<PlanoPropuesto> planos, String descripcion, List<String> hashtags) {

    public record PlanoPropuesto(Integer orden, String movimiento, String texto) {
    }

    public GuionPropuesto {
        planos = planos == null ? List.of() : List.copyOf(planos);
        hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
    }

    /** Textos sin espacios sobrantes y hashtags sin repetidos (sin distinguir mayúsculas), antes de validar. */
    public GuionPropuesto limpio() {
        List<PlanoPropuesto> limpios = planos.stream()
                .map(plano -> new PlanoPropuesto(plano.orden(), recortar(plano.movimiento()), recortar(plano.texto())))
                .toList();
        Set<String> vistos = new LinkedHashSet<>();
        List<String> unicos = new ArrayList<>();
        for (String hashtag : hashtags) {
            String limpio = recortar(hashtag);
            if (limpio != null && vistos.add(limpio.toLowerCase(Locale.ROOT))) {
                unicos.add(limpio);
            }
        }
        return new GuionPropuesto(limpios, recortar(descripcion), unicos);
    }

    public PlanoPropuesto plano(int orden) {
        return planos.stream().filter(plano -> plano.orden() != null && plano.orden() == orden).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El guion no tiene el plano " + orden));
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.strip();
    }
}
