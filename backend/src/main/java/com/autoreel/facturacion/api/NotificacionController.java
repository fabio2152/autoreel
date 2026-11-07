package com.autoreel.facturacion.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.autoreel.facturacion.aplicacion.ServicioNotificaciones;
import com.autoreel.facturacion.dominio.NotificacionAdmin;
import com.autoreel.facturacion.dominio.TipoNotificacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Campana de notificaciones del panel (RF-09). */
@RestController
@RequestMapping("/api/admin/notificaciones")
public class NotificacionController {

    public record NotificacionVista(Long id, TipoNotificacion tipo, Long negocioId, LocalDate periodo, String mensaje,
            Instant fecha, boolean leida) {

        static NotificacionVista de(NotificacionAdmin notificacion) {
            return new NotificacionVista(notificacion.getId(), notificacion.getTipo(), notificacion.getNegocioId(),
                    notificacion.getPeriodo(), notificacion.getMensaje(), notificacion.getFecha(),
                    notificacion.isLeida());
        }
    }

    public record BandejaVista(long sinLeer, List<NotificacionVista> notificaciones) {
    }

    private final ServicioNotificaciones servicio;

    public NotificacionController(ServicioNotificaciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public BandejaVista listar() {
        ServicioNotificaciones.Bandeja bandeja = servicio.bandeja();
        return new BandejaVista(bandeja.sinLeer(),
                bandeja.notificaciones().stream().map(NotificacionVista::de).toList());
    }

    @PostMapping("/{id}/leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        servicio.marcarLeida(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leidas")
    public ResponseEntity<Void> marcarTodasLeidas() {
        servicio.marcarTodasLeidas();
        return ResponseEntity.noContent().build();
    }
}
