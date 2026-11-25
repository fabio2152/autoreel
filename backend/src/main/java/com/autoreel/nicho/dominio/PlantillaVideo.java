package com.autoreel.nicho.dominio;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plantilla del video de 15 segundos (sección 7.4, guion flexible del 2026-11-25): la IA elige una de las estructuras
 * (cuántas tomas con IA y cuánto dura cada una) y el sistema agrega el cierre con el logo. La primera toma es siempre
 * el gancho.
 *
 * @param duracionVideo             segundos del video completo; cada estructura debe sumarlos exactos.
 * @param estructuras               las opciones que puede elegir la IA; la primera es la que se usa si falta.
 * @param modoDeTomasPorDefecto     el modo de tomas si el chat no lo pidió (videos anteriores al 2026-11-25).
 * @param modosDeToma               lo que el negocio elige en el chat ("¿Cómo quieres las tomas?"): qué estructuras
 *                                  puede usar la IA y si las tomas se encadenan con transiciones.
 * @param maxPalabrasCierre         de la frase del cierre.
 * @param llamados                  llamado a la acción por canal del onboarding, en orden de prioridad; "{distrito}"
 *                                  se reemplaza por el distrito del negocio.
 * @param hashtagsBase              hashtags del nicho que se le sugieren al modelo de texto.
 * @param maximoCaracteresPrecio    largo máximo del precio y de la frase del cierre.
 * @param maximoCaracteresUbicacion largo máximo de la ubicación que propone la IA para el cierre.
 * @param tipografias               archivo de la carpeta {@code fuentes/} del paquete para cada estilo (P-05).
 */
public record PlantillaVideo(int duracionVideo, List<Estructura> estructuras, String modoDeTomasPorDefecto,
        List<ModoDeTomas> modosDeToma, int maxPalabrasCierre,
        LinkedHashMap<String, String> llamados, List<String> hashtagsBase, int maximoCaracteresPrecio,
        int maximoCaracteresUbicacion, Map<String, String> tipografias) {

    /** Nombre del primer plano, el gancho. */
    public static final String NOMBRE_GANCHO = "Gancho";
    public static final String NOMBRE_CIERRE = "Cierre";

    /**
     * Una forma de repartir los 15 segundos.
     *
     * @param tomas       segundos de cada toma con IA, en orden (la primera es el gancho).
     * @param cierre      segundos del cierre con el logo.
     * @param maxPalabras del texto en pantalla de cada toma.
     * @param extender    la toma dura más que un clip del modelo: se genera un clip y luego se extiende (Veo).
     */
    public record Estructura(String id, String nombre, String descripcion, List<Integer> tomas, int cierre,
            int maxPalabras, boolean extender) {

        public Estructura {
            tomas = tomas == null ? List.of() : List.copyOf(tomas);
        }

        public int totalSegundos() {
            return tomas.stream().mapToInt(Integer::intValue).sum() + cierre;
        }

        /** Orden del cierre: el plano que sigue a la última toma. */
        public int ordenDelCierre() {
            return tomas.size() + 1;
        }

        /** Segundos de la toma {@code orden} (1 en adelante). */
        public int segundos(int orden) {
            return tomas.get(orden - 1);
        }
    }

    /**
     * Cómo quiere el negocio las tomas del video (paso "tomas" del chat, 2026-11-25).
     *
     * @param estructuras  las que puede elegir la IA con este modo.
     * @param transiciones cada toma termina en la foto de la siguiente (el modelo de video recibe la foto final).
     * @param indicacion   lo que se le dice a la IA en el guion.
     */
    public record ModoDeTomas(String id, String nombre, List<String> estructuras, boolean transiciones,
            String indicacion) {

        public ModoDeTomas {
            estructuras = estructuras == null ? List.of() : List.copyOf(estructuras);
        }
    }

    public PlantillaVideo {
        estructuras = List.copyOf(estructuras);
        modosDeToma = modosDeToma == null ? List.of() : List.copyOf(modosDeToma);
        hashtagsBase = List.copyOf(hashtagsBase);
        tipografias = tipografias == null ? Map.of() : Map.copyOf(tipografias);
    }

    public Optional<Estructura> estructura(String id) {
        return estructuras.stream().filter(estructura -> estructura.id().equals(id)).findFirst();
    }

    /** La estructura de un video; si no tiene (o ya no existe en el paquete), la primera. */
    public Estructura estructuraOPrimera(String id) {
        return estructura(id).orElse(estructuras.getFirst());
    }

    /** El modo de tomas que eligió el negocio; si no eligió (o ya no existe), el modo por defecto. */
    public ModoDeTomas modoDeTomas(String id) {
        return modosDeToma.stream().filter(modo -> modo.id().equals(id)).findFirst()
                .orElseGet(() -> modosDeToma.stream().filter(modo -> modo.id().equals(modoDeTomasPorDefecto))
                        .findFirst().orElseThrow());
    }

    /** Las estructuras que puede elegir la IA con un modo de tomas, en el orden de la plantilla. */
    public List<Estructura> estructurasPara(ModoDeTomas modo) {
        return estructuras.stream().filter(estructura -> modo.estructuras().contains(estructura.id())).toList();
    }

    /** Nombre de un plano para el storyboard: el gancho, las tomas numeradas y el cierre. */
    public static String nombreDelPlano(int orden) {
        return orden == 1 ? NOMBRE_GANCHO : "Toma " + orden;
    }

    /** Llamados a la acción posibles para los canales del negocio, con el distrito ya puesto. */
    public List<String> llamadosPara(List<String> canales, String distrito) {
        return llamados.entrySet().stream()
                .filter(llamado -> canales.contains(llamado.getKey()))
                .map(llamado -> llamado.getValue().replace("{distrito}", distrito))
                .toList();
    }

    /** El llamado del canal de mayor prioridad que tenga el negocio. */
    public Optional<String> llamadoPrincipal(List<String> canales, String distrito) {
        return llamadosPara(canales, distrito).stream().findFirst();
    }

    public void validar(List<Opcion> canalesDelOnboarding, List<Opcion> estilos) {
        estilos.forEach(estilo -> exigir(tipografias.containsKey(estilo.valor()),
                "falta la tipografía del estilo " + estilo.valor()));
        exigir(!estructuras.isEmpty(), "no hay estructuras");
        Set<String> ids = new HashSet<>();
        for (Estructura estructura : estructuras) {
            exigir(ids.add(estructura.id()), "estructura repetida: " + estructura.id());
            exigir(estructura.nombre() != null && estructura.descripcion() != null,
                    "a la estructura " + estructura.id() + " le falta el nombre o la descripción");
            exigir(!estructura.tomas().isEmpty() && estructura.tomas().size() <= 4,
                    "la estructura " + estructura.id() + " debe tener de 1 a 4 tomas");
            int maximo = estructura.extender() ? 15 : 8;
            exigir(estructura.tomas().stream().allMatch(segundos -> segundos >= 2 && segundos <= maximo),
                    "las tomas de " + estructura.id() + " deben durar de 2 a " + maximo + " segundos");
            exigir(estructura.cierre() >= 2, "el cierre de " + estructura.id() + " es muy corto");
            exigir(estructura.totalSegundos() == duracionVideo,
                    "la estructura " + estructura.id() + " no suma " + duracionVideo + " segundos");
            exigir(estructura.maxPalabras() > 0, "falta el máximo de palabras de " + estructura.id());
        }
        exigir(!modosDeToma.isEmpty(), "no hay modos de tomas");
        Set<String> modos = new HashSet<>();
        for (ModoDeTomas modo : modosDeToma) {
            exigir(modos.add(modo.id()), "modo de tomas repetido: " + modo.id());
            exigir(!modo.estructuras().isEmpty() && modo.estructuras().stream().allMatch(ids::contains),
                    "el modo " + modo.id() + " usa una estructura que no existe");
            exigir(modo.indicacion() != null && !modo.indicacion().isBlank(),
                    "al modo " + modo.id() + " le falta la indicación");
            exigir(!modo.transiciones() || estructurasPara(modo).stream().allMatch(e -> e.tomas().size() > 1
                            && !e.extender()), "el modo " + modo.id() + " encadena tomas: sus estructuras deben tener varias tomas");
        }
        exigir(modos.contains(modoDeTomasPorDefecto), "el modo de tomas por defecto no existe");
        exigir(maxPalabrasCierre > 0, "falta el máximo de palabras de la frase del cierre");
        canalesDelOnboarding.forEach(canal -> exigir(llamados.containsKey(canal.valor()),
                "falta el llamado del canal " + canal.valor()));
        exigir(hashtagsBase.size() >= 3 && hashtagsBase.size() <= 6, "debe haber entre 3 y 6 hashtags base");
        exigir(maximoCaracteresPrecio > 0, "falta el máximo de caracteres del precio");
        exigir(maximoCaracteresUbicacion > 0, "falta el máximo de caracteres de la ubicación");
    }

    private static void exigir(boolean condicion, String problema) {
        if (!condicion) {
            throw new IllegalStateException("Plantilla de video inválida: " + problema);
        }
    }
}
