package com.autoreel.almacenamiento.api;

import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos;
import com.autoreel.almacenamiento.aplicacion.ServicioDeArchivos.SubidaFirmada;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import com.autoreel.compartido.seguridad.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArchivosController {

    public record SolicitudSubida(
            @NotNull(message = "Indica para qué es el archivo.") PropositoArchivo proposito,
            @NotBlank(message = "Indica el tipo de archivo.") String tipoContenido,
            @NotNull(message = "Indica el tamaño del archivo.") Long tamano) {
    }

    private final ServicioDeArchivos servicio;

    public ArchivosController(ServicioDeArchivos servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/api/archivos/url-subida")
    public ResponseEntity<SubidaFirmada> urlDeSubida(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody SolicitudSubida solicitud) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.firmarSubida(usuario.negocioId(),
                solicitud.proposito(), solicitud.tipoContenido(), solicitud.tamano()));
    }
}
