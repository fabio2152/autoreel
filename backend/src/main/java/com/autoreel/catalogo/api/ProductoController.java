package com.autoreel.catalogo.api;

import java.util.List;

import com.autoreel.catalogo.aplicacion.ServicioCatalogo;
import com.autoreel.catalogo.dominio.EstadoProducto;
import com.autoreel.compartido.seguridad.UsuarioAutenticado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ServicioCatalogo servicio;

    public ProductoController(ServicioCatalogo servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<ProductoVista> listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(defaultValue = "ACTIVO") EstadoProducto estado) {
        return servicio.listar(usuario.negocioId(), estado);
    }

    @GetMapping("/{id}")
    public ProductoVista obtener(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return servicio.obtener(usuario.negocioId(), id);
    }

    @PostMapping
    public ResponseEntity<ProductoVista> crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestBody SolicitudProducto solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(usuario.negocioId(), solicitud));
    }

    @PutMapping("/{id}")
    public ProductoVista actualizar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @RequestBody SolicitudProducto solicitud) {
        return servicio.actualizar(usuario.negocioId(), id, solicitud);
    }

    @PostMapping("/{id}/archivar")
    public ProductoVista archivar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return servicio.archivar(usuario.negocioId(), id);
    }

    @PostMapping("/{id}/reactivar")
    public ProductoVista reactivar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return servicio.reactivar(usuario.negocioId(), id);
    }
}
