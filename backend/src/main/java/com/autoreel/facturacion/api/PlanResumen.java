package com.autoreel.facturacion.api;

import java.math.BigDecimal;

import com.autoreel.facturacion.dominio.Plan;

/** @param negocios cuántos negocios lo tienen en su suscripción. */
public record PlanResumen(Long id, String nombre, String descripcion, int videosPorDia, int regeneracionesPorVideo,
        Long modeloTextoId, String modeloTexto, Long modeloVideoId, String modeloVideo, BigDecimal precioReferencial,
        boolean activo, long negocios) {

    public static PlanResumen de(Plan plan, long negocios) {
        return new PlanResumen(plan.getId(), plan.getNombre(), plan.getDescripcion(), plan.getVideosPorDia(),
                plan.getRegeneracionesPorVideo(), plan.getModeloTexto().getId(), plan.getModeloTexto().getNombre(),
                plan.getModeloVideo().getId(), plan.getModeloVideo().getNombre(), plan.getPrecioReferencial(),
                plan.isActivo(), negocios);
    }
}
