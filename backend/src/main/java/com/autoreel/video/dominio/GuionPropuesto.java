package com.autoreel.video.dominio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lo que escribe el modelo de texto (guion flexible, 2026-11-25): la estructura y el nivel de creatividad que eligió,
 * la idea del video, las tomas (foto, toma de cámara y texto), los textos del cierre, la descripción de la publicación
 * y los hashtags. El llamado a la acción del cierre lo pone el sistema según los canales del negocio.
 *
 * @param idea   el concepto del video en una o dos frases, en español, para el storyboard.
 * @param cierre vacío si el modelo no lo escribió (el validador lo pide).
 */
public record GuionPropuesto(String estructura, String creatividad, String idea, List<PlanoPropuesto> planos,
        CierrePropuesto cierre, String descripcion, List<String> hashtags) {

    /**
     * @param foto            número de la foto del producto (1 a 3) de la que parte la toma.
     * @param toma            la toma de cámara en inglés, para el modelo de video.
     * @param descripcionToma la toma en pocas palabras, en español, para el storyboard.
     * @param texto           el texto en pantalla; vacío si la toma va sin texto (nunca el gancho).
     */
    public record PlanoPropuesto(Integer orden, Integer foto, String toma, String descripcionToma, String texto) {
    }

    /**
     * El texto principal del cierre: el precio, si el negocio escribió uno de verdad, o una frase gancho.
     *
     * @param precio    resumen breve del precio o la promoción del negocio; vacío si no escribió uno.
     * @param frase     frase gancho que se muestra cuando no hay precio.
     * @param ubicacion lugar más preciso que el distrito, tomado de lo que dijo el negocio; vacío si no hay.
     */
    public record CierrePropuesto(String precio, String frase, String ubicacion) {

        /** Sin espacios sobrantes; un texto vacío cuenta como no escrito. */
        CierrePropuesto limpio() {
            return new CierrePropuesto(recortar(precio), recortar(frase), recortar(ubicacion));
        }
    }

    public GuionPropuesto {
        planos = planos == null ? List.of() : List.copyOf(planos);
        hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
    }

    /** Textos sin espacios sobrantes y hashtags sin repetidos (sin distinguir mayúsculas), antes de validar. */
    public GuionPropuesto limpio() {
        List<PlanoPropuesto> limpios = planos.stream()
                .map(plano -> new PlanoPropuesto(plano.orden(), plano.foto(), recortar(plano.toma()),
                        recortar(plano.descripcionToma()), recortar(plano.texto())))
                .toList();
        Set<String> vistos = new LinkedHashSet<>();
        List<String> unicos = new ArrayList<>();
        for (String hashtag : hashtags) {
            String limpio = recortar(hashtag);
            if (limpio != null && vistos.add(limpio.toLowerCase(Locale.ROOT))) {
                unicos.add(limpio);
            }
        }
        return new GuionPropuesto(recortar(estructura), recortar(creatividad), recortar(idea), limpios,
                cierre == null ? null : cierre.limpio(), recortar(descripcion), unicos);
    }

    public PlanoPropuesto plano(int orden) {
        return planos.stream().filter(plano -> plano.orden() != null && plano.orden() == orden).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El guion no tiene el plano " + orden));
    }

    /** Sin espacios al inicio ni al final; vacío si no queda nada. */
    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
