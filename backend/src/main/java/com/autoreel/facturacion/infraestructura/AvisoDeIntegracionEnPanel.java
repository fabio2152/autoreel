package com.autoreel.facturacion.infraestructura;

import com.autoreel.facturacion.aplicacion.ServicioNotificaciones;
import com.autoreel.ia.dominio.AvisoDeIntegracion;
import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ModeloIA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RN-57: una falla del proveedor queda en el registro de la aplicación y como notificación INTEGRACION_CON_ERROR en el
 * panel (una por modelo y por día). Si la notificación no se puede guardar, el proceso que avisó sigue igual.
 */
@Component
public class AvisoDeIntegracionEnPanel implements AvisoDeIntegracion {

    private static final Logger log = LoggerFactory.getLogger(AvisoDeIntegracionEnPanel.class);

    private final ServicioNotificaciones notificaciones;

    public AvisoDeIntegracionEnPanel(ServicioNotificaciones notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Override
    public void avisar(ModeloIA modelo, ErrorDeProveedor.Tipo tipo, String detalle) {
        log.error("Integración con error: modelo {} ({}), {}: {}", modelo.getNombre(), modelo.getAdaptador(), tipo,
                detalle);
        try {
            notificaciones.integracionConError(modelo.getId(), modelo.getNombre() + ": " + motivo(tipo) + " " + detalle);
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar la notificación de integración del modelo {}", modelo.getNombre(), e);
        }
    }

    private static String motivo(ErrorDeProveedor.Tipo tipo) {
        return switch (tipo) {
            case CREDENCIAL_INVALIDA -> "la credencial no funciona.";
            case CONFIGURACION_INVALIDA -> "el modelo está mal configurado.";
            case NO_DISPONIBLE -> "el proveedor no está disponible.";
        };
    }
}
