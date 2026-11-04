package com.autoreel.facturacion.api;

import java.math.BigDecimal;

import com.autoreel.facturacion.dominio.Plan;

public record PlanResumen(Long id, String nombre, String descripcion, int videosPorDia, int regeneracionesPorVideo,
        String modeloTexto, String modeloVideo, BigDecimal precioReferencial, boolean activo) {

    static PlanResumen de(Plan plan) {
        return new PlanResumen(plan.getId(), plan.getNombre(), plan.getDescripcion(), plan.getVideosPorDia(),
                plan.getRegeneracionesPorVideo(), plan.getModeloTexto().getNombre(),
                plan.getModeloVideo().getNombre(), plan.getPrecioReferencial(), plan.isActivo());
    }
}
