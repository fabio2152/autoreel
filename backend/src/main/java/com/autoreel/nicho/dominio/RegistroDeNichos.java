package com.autoreel.nicho;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Nichos disponibles en la plataforma. En la fase 1 solo se necesita su identificador y su nombre;
 * en la fase 2 cada nicho se convierte en un paquete completo (interfaz PaqueteNicho).
 */
@Component
public class RegistroDeNichos {

    public static final String PASTELERIA = "PASTELERIA";

    private final List<Nicho> nichos = List.of(new Nicho(PASTELERIA, "Pastelería"));

    public List<Nicho> todos() {
        return nichos;
    }

    public Optional<Nicho> buscar(String id) {
        return nichos.stream().filter(nicho -> nicho.id().equals(id)).findFirst();
    }
}
