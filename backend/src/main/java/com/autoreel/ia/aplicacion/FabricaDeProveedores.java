package com.autoreel.ia.aplicacion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.ia.dominio.EstadoModelo;
import com.autoreel.ia.dominio.Guionista;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.TipoModelo;
import com.autoreel.ia.dominio.UnidadCosto;
import org.springframework.stereotype.Component;

/**
 * Entrega la implementación del proveedor que corresponde a un modelo de IA (sección 11.5). El modelo sale del
 * video (RN-54), no del plan actual, para que un cambio de plan no afecte a un video en proceso (RN-49).
 */
@Component
public class FabricaDeProveedores {

    private final Map<String, Guionista> guionistas = new HashMap<>();

    public FabricaDeProveedores(List<Guionista> implementaciones) {
        for (Guionista guionista : implementaciones) {
            if (guionistas.put(guionista.adaptador(), guionista) != null) {
                throw new IllegalStateException("Hay dos adaptadores de texto llamados " + guionista.adaptador());
            }
        }
    }

    /**
     * El guionista del modelo, si el modelo es de texto, está activo y su adaptador existe en el código (RN-56).
     * Si no, vacío: el proceso usa su respaldo.
     */
    public Optional<Guionista> guionista(ModeloIA modelo) {
        // El guion se cobra por solicitud (RN-32); un modelo de texto con otra unidad está mal configurado.
        if (modelo.getTipo() != TipoModelo.TEXTO || modelo.getEstado() != EstadoModelo.ACTIVO
                || modelo.getUnidadCosto() != UnidadCosto.POR_SOLICITUD) {
            return Optional.empty();
        }
        return Optional.ofNullable(guionistas.get(modelo.getAdaptador()));
    }
}
