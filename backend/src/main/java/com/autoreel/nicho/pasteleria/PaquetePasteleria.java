package com.autoreel.nicho.pasteleria;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.autoreel.nicho.dominio.GuionChat;
import com.autoreel.nicho.dominio.Movimientos;
import com.autoreel.nicho.dominio.Opcion;
import com.autoreel.nicho.dominio.PlantillaVideo;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PistaDelNicho;
import com.autoreel.nicho.dominio.Pregunta;
import com.autoreel.nicho.dominio.RegistroDeNichos;
import com.autoreel.nicho.dominio.Temporada;
import com.autoreel.nicho.dominio.Texto;
import com.autoreel.nicho.dominio.Vocabulario;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
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

    private record ArchivoMusica(List<PistaDelNicho> pistas) {
    }

    private final List<Opcion> estilos;
    private final List<Pregunta> preguntas;
    private final List<Temporada> temporadas;
    private final GuionChat guionDelChat;
    private final PlantillaVideo plantillaVideo;
    private final Movimientos movimientos;
    private final Vocabulario vocabulario;
    private final String instruccionesGuion;
    private final List<PistaDelNicho> pistas;
    /** Las tipografías se leen una vez: FFmpeg las necesita como archivo, y en el JAR no hay ruta de archivo. */
    private final Map<String, byte[]> tipografias = new HashMap<>();

    public PaquetePasteleria(ObjectMapper json) {
        ArchivoOnboarding onboarding = leer(json, "onboarding.json", ArchivoOnboarding.class);
        this.estilos = List.copyOf(onboarding.estilos());
        this.preguntas = List.copyOf(onboarding.preguntas());
        this.temporadas = List.copyOf(leer(json, "temporadas.json", ArchivoTemporadas.class).temporadas());
        this.guionDelChat = leer(json, "chat.json", GuionChat.class);
        this.plantillaVideo = leer(json, "plantilla.json", PlantillaVideo.class);
        this.movimientos = leer(json, "movimientos.json", Movimientos.class);
        this.vocabulario = leer(json, "vocabulario.json", Vocabulario.class);
        this.instruccionesGuion = leerTexto("guion.md");
        this.pistas = List.copyOf(leer(json, "musica.json", ArchivoMusica.class).pistas());
        plantillaVideo.tipografias().forEach((estilo, archivo) ->
                tipografias.put(estilo, leerBytes("fuentes/" + archivo)));
        validar();
        guionDelChat.validar();
        validarVideo();
    }

    @Override
    public GuionChat guionDelChat() {
        return guionDelChat;
    }

    @Override
    public PlantillaVideo plantillaVideo() {
        return plantillaVideo;
    }

    @Override
    public Movimientos movimientos() {
        return movimientos;
    }

    @Override
    public Vocabulario vocabulario() {
        return vocabulario;
    }

    @Override
    public String instruccionesGuion() {
        return instruccionesGuion;
    }

    @Override
    public byte[] tipografia(String estilo) {
        byte[] tipografia = tipografias.get(estilo);
        if (tipografia == null) {
            throw new IllegalArgumentException("No hay tipografía para el estilo " + estilo);
        }
        return tipografia.clone();
    }

    @Override
    public List<PistaDelNicho> pistas() {
        return pistas;
    }

    @Override
    public byte[] leerPista(PistaDelNicho pista) {
        return leerBytes("musica/" + pista.archivo());
    }

    private void validarVideo() {
        List<Opcion> canales = preguntas.stream().filter(p -> p.id().equals("canales")).findFirst()
                .map(Pregunta::opciones).orElseThrow(() -> new IllegalStateException("Falta la pregunta canales"));
        plantillaVideo.validar(canales, guionDelChat.paso("tipoVideo").opciones(), estilos);
        for (Opcion estilo : estilos) {
            exigir(pistas.stream().anyMatch(pista -> pista.estilo().equals(estilo.valor())),
                    "falta una pista de música del estilo " + estilo.valor());
        }
        for (PistaDelNicho pista : pistas) {
            exigir(new ClassPathResource(CARPETA + "musica/" + pista.archivo()).exists(),
                    "no existe la pista " + pista.archivo());
            exigir(pista.duracionSegundos() >= 15, "la pista " + pista.archivo() + " dura menos de 15 segundos");
        }
        for (var plano : plantillaVideo.planosConIa()) {
            exigir(movimientos.buscar(plano.movimientoDeRespaldo()).isPresent(),
                    "el movimiento de respaldo del plano " + plano.orden() + " no existe");
        }
        // Los textos fijos del respaldo deben cumplir el propio vocabulario del nicho.
        List<String> fijos = new ArrayList<>(plantillaVideo.ganchosDeRespaldo().values());
        plantillaVideo.planosConIa().forEach(plano -> fijos.add(plano.textoDeRespaldo()));
        fijos.add(plantillaVideo.descripcionFijaDeRespaldo());
        fijos.addAll(plantillaVideo.llamados().values());
        for (String texto : fijos) {
            for (String frase : vocabulario.prohibidas()) {
                exigir(!Texto.contieneFrase(texto, frase), "el texto de respaldo \"" + texto + "\" usa \"" + frase + "\"");
            }
            for (String frase : vocabulario.prohibidasSalvoQueLasDeclareElProducto()) {
                exigir(!Texto.contieneFrase(texto, frase), "el texto de respaldo \"" + texto + "\" usa \"" + frase + "\"");
            }
        }
        exigir(!instruccionesGuion.isBlank(), "faltan las instrucciones del guion");
    }

    private static byte[] leerBytes(String archivo) {
        try (InputStream entrada = new ClassPathResource(CARPETA + archivo).getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + CARPETA + archivo, e);
        }
    }

    private static String leerTexto(String archivo) {
        try (InputStream entrada = new ClassPathResource(CARPETA + archivo).getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + CARPETA + archivo, e);
        }
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
            // Un campo mal escrito en el archivo debe fallar al arrancar, no ignorarse. Un sí/no que no se escribe
            // (por ejemplo, "omitible") vale false.
            return json.readerFor(tipo)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .readValue(entrada);
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
