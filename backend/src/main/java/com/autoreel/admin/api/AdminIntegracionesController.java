package com.autoreel.admin.api;

import com.autoreel.admin.api.IntegracionDtos.CredencialVista;
import com.autoreel.admin.api.IntegracionDtos.Integraciones;
import com.autoreel.admin.api.IntegracionDtos.ModeloVista;
import com.autoreel.admin.api.IntegracionDtos.ResultadoPrueba;
import com.autoreel.admin.api.IntegracionDtos.SolicitudCredencial;
import com.autoreel.admin.api.IntegracionDtos.SolicitudModelo;
import com.autoreel.admin.aplicacion.ServicioIntegraciones;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ajustes → Integraciones (sección 13): modelos de IA y credenciales. */
@RestController
@RequestMapping("/api/admin")
public class AdminIntegracionesController {

    private final ServicioIntegraciones servicio;

    public AdminIntegracionesController(ServicioIntegraciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/integraciones")
    public Integraciones listar() {
        return servicio.listar();
    }

    @PostMapping("/modelos-ia")
    public ResponseEntity<ModeloVista> crear(@Valid @RequestBody SolicitudModelo solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(solicitud));
    }

    @PutMapping("/modelos-ia/{id}")
    public ModeloVista actualizar(@PathVariable Long id, @Valid @RequestBody SolicitudModelo solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    @PostMapping("/modelos-ia/{id}/probar")
    public ResultadoPrueba probar(@PathVariable Long id) {
        return servicio.probar(id);
    }

    @PostMapping("/modelos-ia/{id}/activar")
    public ModeloVista activar(@PathVariable Long id) {
        return servicio.activar(id);
    }

    @PostMapping("/modelos-ia/{id}/desactivar")
    public ModeloVista desactivar(@PathVariable Long id) {
        return servicio.desactivar(id);
    }

    /** El valor viaja una sola vez, del navegador al servidor; la respuesta nunca lo incluye. */
    @PutMapping("/credenciales")
    public ResponseEntity<CredencialVista> guardarCredencial(@Valid @RequestBody SolicitudCredencial solicitud) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.guardarCredencial(solicitud));
    }

    @DeleteMapping("/credenciales")
    public CredencialVista borrarCredencial(@RequestParam String nombre) {
        return servicio.borrarCredencial(nombre);
    }
}
