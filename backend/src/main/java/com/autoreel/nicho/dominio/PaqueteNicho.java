package com.autoreel.nicho.dominio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Todo lo propio de un rubro vive en su paquete (sección 7): un nicho nuevo es otro paquete, sin cambiar el resto
 * del sistema. Por ahora expone lo que usan el onboarding y el catálogo; el guion del chat, la plantilla de video,
 * los movimientos, el vocabulario y la música se agregan en sus fases.
 */
public interface PaqueteNicho {

    /** Una fecha especial se considera "próxima" durante los 15 días anteriores (sección 7.7). */
    int DIAS_FECHA_PROXIMA = 15;

    String id();

    String nombre();

    List<Opcion> estilos();

    List<Pregunta> preguntasOnboarding();

    List<Temporada> temporadas();

    default boolean admiteEstilo(String estilo) {
        return estilos().stream().anyMatch(opcion -> opcion.valor().equals(estilo));
    }

    default boolean admiteTemporada(String id) {
        return temporadas().stream().anyMatch(temporada -> temporada.id().equals(id));
    }

    /** Temporadas y fechas especiales en curso. "Todo el año" no cuenta, para no empatar con la temporada actual. */
    default List<Temporada> temporadasVigentes(LocalDate hoy) {
        return temporadas().stream()
                .filter(temporada -> temporada.clase() != Temporada.Clase.SIEMPRE && temporada.vigenteEn(hoy))
                .toList();
    }

    /** Fechas especiales que empiezan en los próximos 15 días, de la más cercana a la más lejana. */
    default List<Temporada> fechasEspecialesProximas(LocalDate hoy) {
        LocalDate limite = hoy.plusDays(DIAS_FECHA_PROXIMA);
        return temporadas().stream()
                .filter(temporada -> temporada.clase() == Temporada.Clase.FECHA_ESPECIAL && !temporada.vigenteEn(hoy))
                .filter(temporada -> temporada.proximoInicio(hoy).filter(inicio -> !inicio.isAfter(limite)).isPresent())
                .sorted((a, b) -> Long.compare(
                        ChronoUnit.DAYS.between(hoy, a.proximoInicio(hoy).orElseThrow()),
                        ChronoUnit.DAYS.between(hoy, b.proximoInicio(hoy).orElseThrow())))
                .toList();
    }
}
