package com.autoreel.costos.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;

/** Un registro de costo reducido a lo que necesita el gráfico del admin: de quién, cuándo y cuánto. */
public record CostoRegistrado(Long negocioId, Instant fechaHora, BigDecimal costoUsd) {
}
