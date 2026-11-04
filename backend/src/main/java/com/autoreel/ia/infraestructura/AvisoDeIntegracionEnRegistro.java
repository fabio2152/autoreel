package com.autoreel.ia.infraestructura;

import com.autoreel.ia.dominio.AvisoDeIntegracion;
import com.autoreel.ia.dominio.ErrorDeProveedor;
import com.autoreel.ia.dominio.ModeloIA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Aviso de RN-57 en el registro de la aplicación, hasta que la fase 7 agregue las notificaciones del panel. */
@Component
public class AvisoDeIntegracionEnRegistro implements AvisoDeIntegracion {

    private static final Logger log = LoggerFactory.getLogger(AvisoDeIntegracionEnRegistro.class);

    @Override
    public void avisar(ModeloIA modelo, ErrorDeProveedor.Tipo tipo, String detalle) {
        log.error("Integración con error: modelo {} ({}), {}: {}", modelo.getNombre(), modelo.getAdaptador(), tipo,
                detalle);
    }
}
