package com.autoreel.nicho.pasteleria;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.autoreel.nicho.dominio.Opcion;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.Pregunta;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.nicho.dominio.Temporada;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Paquete del nicho Pastelería (sección 7). Su contenido vive en {@code resources/nichos/pasteleria/}; se valida al
 * arrancar para que un error en esos archivos no llegue a producción.
 */
@Component
public class PaquetePasteleria implements PaqueteNicho {

    private static final String CARPETA = "nichos/pasteleria/";

    private record ArchivoOnboarding(List<Opcion> estilos, List<Pregunta> preguntas) {
    }

    private record ArchivoTemporadas(List<Temporada> temporadas) {
    }

    private final List<Opcion> estilos;
    private final List<Pregunta> preguntas;
    private final List<Temporada> temporadas;

    public PaquetePasteleria(ObjectMapper json) {
        ArchivoOnboarding onboarding = leer(json, "onboarding.json", ArchivoOnboarding.class);
        this.estilos = List.copyOf(onboarding.estilos());
        this.preguntas = List.copyOf(onboarding.preguntas());
        this.temporadas = List.copyOf(leer(json, "temporadas.json", ArchivoTemporadas.class).temporadas());
        validar();
    }

    @Override
    public String id() {
        return RegistroDeNichos.PASTELERIA;
    }

    @Override
    public String nombre() {
        return "Pastelería";
    }

    @Override
    public List<Opcion> estilos() {
        return estilos;
    }

    @Override
    public List<Pregunta> preguntasOnboarding() {
        return preguntas;
    }

    @Override
    public List<Temporada> temporadas() {
        return temporadas;
    }

    private static <T> T leer(ObjectMapper json, String archivo, Class<T> tipo) {
        try (InputStream entrada = new ClassPathResource(CARPETA + archivo).getInputStream()) {
            return json.readValue(entrada, tipo);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + CARPETA + archivo, e);
        }
    }

    private void validar() {
        Set<String> ids = new HashSet<>();
        for (Pregunta pregunta : preguntas) {
            exigir(ids.add(pregunta.id()), "pregunta repetida: " + pregunta.id());
            exigir(pregunta.paso() == 2 || pregunta.paso() == 3, "paso inválido en " + pregunta.id());
            exigir(pregunta.tipo() != Pregunta.TipoPregunta.SELECCION_MULTIPLE || !pregunta.opciones().isEmpty(),
                    "la pregunta " + pregunta.id() + " no tiene opciones");
        }
        for (Pregunta pregunta : preguntas) {
            if (pregunta.obligatoriaSi() != null) {
                exigir(ids.contains(pregunta.obligatoriaSi().pregunta()),
                        "condición de " + pregunta.id() + " apunta a una pregunta que no existe");
            }
        }
        Set<String> idsTemporadas = new HashSet<>();
        temporadas.forEach(t -> exigir(idsTemporadas.add(t.id()), "temporada repetida: " + t.id()));
        exigir(!estilos.isEmpty(), "no hay estilos");
    }

    private static void exigir(boolean condicion, String problema) {
        if (!condicion) {
            throw new IllegalStateException("Paquete de nicho Pastelería inválido: " + problema);
        }
    }
}
