package com.autoreel.ia.aplicacion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.autoreel.ia.dominio.EstadoModelo;
import com.autoreel.ia.dominio.GeneradorDeClips;
import com.autoreel.ia.dominio.Guionista;
import com.autoreel.ia.dominio.ModeloIA;
import com.autoreel.ia.dominio.ProveedorDeSecretos;
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
    private final Map<String, GeneradorDeClips> generadores = new HashMap<>();
    private final ProveedorDeSecretos secretos;

    public FabricaDeProveedores(List<Guionista> guionistas, List<GeneradorDeClips> generadores,
            ProveedorDeSecretos secretos) {
        for (Guionista guionista : guionistas) {
            if (this.guionistas.put(guionista.adaptador(), guionista) != null) {
                throw new IllegalStateException("Hay dos adaptadores de texto llamados " + guionista.adaptador());
            }
        }
        for (GeneradorDeClips generador : generadores) {
            if (this.generadores.put(generador.adaptador(), generador) != null) {
                throw new IllegalStateException("Hay dos adaptadores de video llamados " + generador.adaptador());
            }
        }
        this.secretos = secretos;
    }

    /**
     * El guionista del modelo, si el modelo es de texto, está activo y su adaptador existe en el código (RN-56).
     * Si no, vacío: el proceso usa su respaldo.
     */
    public Optional<Guionista> guionista(ModeloIA modelo) {
        // El guion se cobra por solicitud (RN-32); un modelo de texto con otra unidad está mal configurado.
        if (!utilizable(modelo, TipoModelo.TEXTO, UnidadCosto.POR_SOLICITUD)) {
            return Optional.empty();
        }
        return Optional.ofNullable(guionistas.get(modelo.getAdaptador()));
    }

    /** El generador de clips del modelo, con las mismas condiciones; los clips se cobran por segundo. */
    public Optional<GeneradorDeClips> generadorDeClips(ModeloIA modelo) {
        if (!utilizable(modelo, TipoModelo.VIDEO, UnidadCosto.POR_SEGUNDO)) {
            return Optional.empty();
        }
        return Optional.ofNullable(generadores.get(modelo.getAdaptador()));
    }

    /** Si el modelo de video puede usarse ahora: adaptador existente, activo y con su credencial configurada. */
    public boolean videoDisponible(ModeloIA modelo) {
        return generadorDeClips(modelo).isPresent() && secretos.obtener(modelo.getNombreSecreto()).isPresent();
    }

    private static boolean utilizable(ModeloIA modelo, TipoModelo tipo, UnidadCosto unidad) {
        return modelo.getTipo() == tipo && modelo.getEstado() == EstadoModelo.ACTIVO
                && modelo.getUnidadCosto() == unidad;
    }
}
