package com.autoreel.facturacion.api;

import java.util.List;

import com.autoreel.facturacion.infraestructura.PlanRepositorio;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Planes disponibles. En la fase 1 solo se listan, para elegirlos al crear un negocio;
 * crearlos y editarlos llega en la fase 7.
 */
@RestController
@RequestMapping("/api/admin/planes")
public class PlanController {

    private final PlanRepositorio planes;

    public PlanController(PlanRepositorio planes) {
        this.planes = planes;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PlanResumen> listar() {
        return planes.listarConModelos().stream().map(PlanResumen::de).toList();
    }
}
