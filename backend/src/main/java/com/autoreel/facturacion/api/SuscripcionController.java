package com.autoreel.facturacion.api;

import java.util.List;

import com.autoreel.compartido.seguridad.UsuarioAutenticado;
import com.autoreel.facturacion.api.SuscripcionDtos.BeneficioVista;
import com.autoreel.facturacion.api.SuscripcionDtos.PagoRegistrado;
import com.autoreel.facturacion.api.SuscripcionDtos.PagoVista;
import com.autoreel.facturacion.api.SuscripcionDtos.SolicitudBeneficio;
import com.autoreel.facturacion.api.SuscripcionDtos.SolicitudCambioPlan;
import com.autoreel.facturacion.api.SuscripcionDtos.SolicitudPago;
import com.autoreel.facturacion.aplicacion.ServicioSuscripciones;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pagos, cambio de plan y beneficios de un negocio (sección 13). */
@RestController
@RequestMapping("/api/admin/negocios/{negocioId}")
public class SuscripcionController {

    private final ServicioSuscripciones servicio;

    public SuscripcionController(ServicioSuscripciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/pagos")
    public List<PagoVista> pagos(@PathVariable Long negocioId) {
        return servicio.pagos(negocioId);
    }

    @PostMapping("/pagos")
    public ResponseEntity<PagoRegistrado> registrarPago(@PathVariable Long negocioId,
            @Valid @RequestBody SolicitudPago solicitud, @AuthenticationPrincipal UsuarioAutenticado admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrarPago(negocioId, solicitud, admin.id()));
    }

    @PutMapping("/plan")
    public ResponseEntity<Void> cambiarPlan(@PathVariable Long negocioId,
            @Valid @RequestBody SolicitudCambioPlan solicitud) {
        servicio.cambiarPlan(negocioId, solicitud);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/beneficios")
    public List<BeneficioVista> beneficios(@PathVariable Long negocioId) {
        return servicio.beneficios(negocioId);
    }

    @PostMapping("/beneficios")
    public ResponseEntity<BeneficioVista> otorgarBeneficio(@PathVariable Long negocioId,
            @Valid @RequestBody SolicitudBeneficio solicitud, @AuthenticationPrincipal UsuarioAutenticado admin) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicio.otorgarBeneficio(negocioId, solicitud, admin.id()));
    }

    @DeleteMapping("/beneficios/{beneficioId}")
    public ResponseEntity<Void> terminarBeneficio(@PathVariable Long negocioId, @PathVariable Long beneficioId) {
        servicio.terminarBeneficio(negocioId, beneficioId);
        return ResponseEntity.noContent().build();
    }
}
