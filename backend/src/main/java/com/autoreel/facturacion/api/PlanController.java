package com.autoreel.facturacion.api;

import java.util.List;

import com.autoreel.facturacion.aplicacion.ServicioPlanes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-10: planes del panel (Ajustes → Planes). */
@RestController
@RequestMapping("/api/admin/planes")
public class PlanController {

    private final ServicioPlanes servicio;

    public PlanController(ServicioPlanes servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<PlanResumen> listar() {
        return servicio.listar();
    }

    @PostMapping
    public ResponseEntity<PlanResumen> crear(@Valid @RequestBody SolicitudPlan solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(solicitud));
    }

    @PutMapping("/{id}")
    public PlanResumen actualizar(@PathVariable Long id, @Valid @RequestBody SolicitudPlan solicitud) {
        return servicio.actualizar(id, solicitud);
    }
}
