package com.autoreel.admin.api;

import java.util.List;

import com.autoreel.admin.aplicacion.ServicioGestionNegocios;
import com.autoreel.catalogo.api.ProductoVista;
import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.facturacion.dominio.EstadoPago;
import com.autoreel.nicho.dominio.Nicho;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminNegocioController {

    private final ServicioGestionNegocios servicio;
    private final ServicioCatalogo catalogo;
    private final RegistroDeNichos nichos;

    public AdminNegocioController(ServicioGestionNegocios servicio, ServicioCatalogo catalogo,
            RegistroDeNichos nichos) {
        this.servicio = servicio;
        this.catalogo = catalogo;
        this.nichos = nichos;
    }

    /** Catálogo completo del negocio, incluidos los productos archivados (RF-05). */
    @GetMapping("/negocios/{id}/productos")
    public List<ProductoVista> productos(@PathVariable Long id) {
        servicio.detalle(id);
        return catalogo.listar(id, null);
    }

    @GetMapping("/negocios")
    public List<NegocioResumen> listar(@RequestParam(required = false) String busqueda,
            @RequestParam(required = false) EstadoPago estadoPago) {
        return servicio.listar(busqueda, estadoPago);
    }

    @PostMapping("/negocios")
    public ResponseEntity<ContrasenaTemporal> crear(@Valid @RequestBody SolicitudCrearNegocio solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(servicio.crear(solicitud));
    }

    @GetMapping("/negocios/{id}")
    public NegocioDetalle detalle(@PathVariable Long id) {
        return servicio.detalle(id);
    }

    @PostMapping("/negocios/{id}/reiniciar-contrasena")
    public ResponseEntity<ContrasenaTemporal> reiniciarContrasena(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.reiniciarContrasena(id));
    }

    @PostMapping("/negocios/{id}/suspender")
    public ResponseEntity<Void> suspender(@PathVariable Long id) {
        servicio.suspender(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/negocios/{id}/reactivar")
    public ResponseEntity<Void> reactivar(@PathVariable Long id) {
        servicio.reactivar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/nichos")
    public List<Nicho> nichos() {
        return nichos.todos();
    }
}
