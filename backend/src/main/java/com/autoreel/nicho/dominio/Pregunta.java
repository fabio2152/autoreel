package com.autoreel.nicho.dominio;

import java.util.List;

/**
 * Pregunta del onboarding definida por el paquete de nicho (sección 7.1).
 *
 * @param paso          paso del asistente en que aparece (2 o 3; el paso 1, la marca, es común a todos los nichos).
 * @param obligatoriaSi la pregunta pasa a ser obligatoria si otra respuesta de selección contiene un valor.
 */
public record Pregunta(String id, int paso, String texto, String ayuda, TipoPregunta tipo, boolean obligatoria,
        Integer maxLongitud, List<Opcion> opciones, Condicion obligatoriaSi) {

    public enum TipoPregunta {
        TEXTO,
        SELECCION_MULTIPLE,
        /** Celular peruano de 9 dígitos que empieza con 9. */
        TELEFONO
    }

    public record Condicion(String pregunta, String contiene) {
    }

    public Pregunta {
        opciones = opciones == null ? List.of() : List.copyOf(opciones);
    }

    public boolean admiteValor(String valor) {
        return opciones.stream().anyMatch(opcion -> opcion.valor().equals(valor));
    }
}
