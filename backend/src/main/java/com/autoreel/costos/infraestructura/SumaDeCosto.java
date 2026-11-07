package com.autoreel.costos.infraestructura;

import java.math.BigDecimal;

import com.autoreel.costos.dominio.ConceptoCosto;

/** Suma de un concepto de costo de un negocio en un período, con los videos distintos que lo generaron. */
public record SumaDeCosto(Long negocioId, ConceptoCosto concepto, BigDecimal total, Long videos) {
}
