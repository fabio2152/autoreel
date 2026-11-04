package com.autoreel.nicho.dominio;

import java.util.List;
import java.util.Optional;

import com.autoreel.compartido.error.ExcepcionDeNegocio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Nichos disponibles: cada {@link PaqueteNicho} registrado como bean se agrega solo.
 */
@Component
public class RegistroDeNichos {

    public static final String PASTELERIA = "PASTELERIA";

    private final List<PaqueteNicho> paquetes;

    public RegistroDeNichos(List<PaqueteNicho> paquetes) {
        this.paquetes = List.copyOf(paquetes);
    }

    public List<Nicho> todos() {
        return paquetes.stream().map(paquete -> new Nicho(paquete.id(), paquete.nombre())).toList();
    }

    public Optional<PaqueteNicho> buscar(String id) {
        return paquetes.stream().filter(paquete -> paquete.id().equals(id)).findFirst();
    }

    /** Paquete de un negocio existente: su nicho siempre está registrado. */
    public PaqueteNicho de(String id) {
        return buscar(id).orElseThrow(() -> new ExcepcionDeNegocio(HttpStatus.INTERNAL_SERVER_ERROR,
                "NICHO_DESCONOCIDO", "El nicho del negocio no está disponible."));
    }
}
