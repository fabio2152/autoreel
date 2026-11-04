package com.autoreel.nicho.dominio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Temporada o fecha especial con la que se etiquetan los productos (secciones 7.2 y 7.7).
 *
 * @param clase TEMPORADA (un período), FECHA_ESPECIAL (puede anunciarse como "próxima") o SIEMPRE ("todo el año").
 */
public record Temporada(String id, String nombre, Clase clase, Regla regla) {

    public enum Clase {
        TEMPORADA,
        FECHA_ESPECIAL,
        SIEMPRE
    }

    public enum TipoRegla {
        /** Desde una fecha hasta otra (mes-día), y puede cruzar el año: verano va del 21/12 al 20/03. */
        RANGO,
        /** Uno o varios días fijos del año (mes-día). */
        FECHAS,
        /** El n-ésimo domingo de un mes: Día de la Madre, Día del Padre. */
        N_ESIMO_DOMINGO,
        /** Un mes completo. */
        MES,
        SIEMPRE
    }

    /** Las fechas se escriben como "MM-dd". */
    public record Regla(TipoRegla tipo, String desde, String hasta, List<String> fechas, Integer mes, Integer n) {
    }

    public boolean vigenteEn(LocalDate fecha) {
        MonthDay dia = MonthDay.from(fecha);
        return switch (regla.tipo()) {
            case SIEMPRE -> true;
            case MES -> fecha.getMonthValue() == regla.mes();
            case FECHAS -> regla.fechas().stream().map(Temporada::mesDia).anyMatch(dia::equals);
            case N_ESIMO_DOMINGO -> fecha.equals(nEsimoDomingo(fecha.getYear()));
            case RANGO -> {
                MonthDay desde = mesDia(regla.desde());
                MonthDay hasta = mesDia(regla.hasta());
                yield desde.isAfter(hasta)
                        ? !dia.isBefore(desde) || !dia.isAfter(hasta)
                        : !dia.isBefore(desde) && !dia.isAfter(hasta);
            }
        };
    }

    /** Primer día en que vuelve a empezar, estrictamente después de {@code fecha}. Vacío para "todo el año". */
    public Optional<LocalDate> proximoInicio(LocalDate fecha) {
        return Stream.of(fecha.getYear(), fecha.getYear() + 1)
                .flatMap(this::iniciosDelAnio)
                .filter(inicio -> inicio.isAfter(fecha))
                .min(LocalDate::compareTo);
    }

    private Stream<LocalDate> iniciosDelAnio(int anio) {
        return switch (regla.tipo()) {
            case SIEMPRE -> Stream.empty();
            case MES -> Stream.of(LocalDate.of(anio, regla.mes(), 1));
            case FECHAS -> Stream.of(mesDia(regla.fechas().getFirst()).atYear(anio));
            case N_ESIMO_DOMINGO -> Stream.of(nEsimoDomingo(anio));
            case RANGO -> Stream.of(mesDia(regla.desde()).atYear(anio));
        };
    }

    private LocalDate nEsimoDomingo(int anio) {
        return LocalDate.of(anio, regla.mes(), 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(regla.n(), DayOfWeek.SUNDAY));
    }

    static MonthDay mesDia(String texto) {
        return MonthDay.parse("--" + texto);
    }
}
