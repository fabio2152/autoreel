package com.autoreel.facturacion.dominio;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * RN-42: la próxima fecha de pago es el mismo día del mes siguiente. Si ese mes no tiene el día (por ejemplo, el 31
 * en febrero), se usa su último día; el mes siguiente vuelve al día original.
 */
public final class CalculadoraFechaPago {

    private CalculadoraFechaPago() {
    }

    public static LocalDate siguiente(LocalDate fechaPago, int diaPago) {
        YearMonth mes = YearMonth.from(fechaPago).plusMonths(1);
        return mes.atDay(Math.min(diaPago, mes.lengthOfMonth()));
    }
}
