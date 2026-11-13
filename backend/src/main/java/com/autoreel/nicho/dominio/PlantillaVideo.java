package com.autoreel.nicho.dominio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plantilla del video de 15 segundos (sección 7.4): cuatro planos fijos; el último (cierre) no tiene clip de IA.
 *
 * @param llamados                  llamado a la acción por canal del onboarding, en orden de prioridad; "{distrito}"
 *                                  se reemplaza por el distrito del negocio.
 * @param hashtagsBase              hashtags del nicho que se le sugieren al modelo de texto.
 * @param maximoCaracteresPrecio    largo máximo del precio y de la frase del cierre.
 * @param maximoCaracteresUbicacion largo máximo de la ubicación que propone la IA para el cierre.
 * @param tipografias               archivo de la carpeta {@code fuentes/} del paquete para cada estilo (P-05).
 */
public record PlantillaVideo(List<PlanoPlantilla> planos, LinkedHashMap<String, String> llamados,
        List<String> hashtagsBase, int maximoCaracteresPrecio, int maximoCaracteresUbicacion,
        Map<String, String> tipografias) {

    /**
     * @param foto                 foto del producto que usa el plano (DETALLE, ENTERO o PORCION); vacía en el cierre.
     * @param maxPalabras          del texto en pantalla; en el cierre, de la frase.
     * @param conIa                el cierre no usa clip de IA: lo arma el sistema con los textos que propone la IA.
     * @param movimientoDeRespaldo movimiento que se le sugiere al modelo para este plano.
     */
    public record PlanoPlantilla(int orden, String nombre, String foto, int maxPalabras, int segundos,
            boolean conIa, String movimientoDeRespaldo) {
    }

    public PlantillaVideo {
        planos = List.copyOf(planos);
        hashtagsBase = List.copyOf(hashtagsBase);
        tipografias = tipografias == null ? Map.of() : Map.copyOf(tipografias);
    }

    public PlanoPlantilla plano(int orden) {
        return planos.stream().filter(plano -> plano.orden() == orden).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe el plano " + orden));
    }

    /** Planos con clip de IA (todos menos el cierre). */
    public List<PlanoPlantilla> planosConIa() {
        return planos.stream().filter(PlanoPlantilla::conIa).toList();
    }

    public PlanoPlantilla cierre() {
        return planos.stream().filter(plano -> !plano.conIa()).findFirst().orElseThrow();
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
        exigir(planos.size() == 4, "la plantilla debe tener 4 planos");
        exigir(planos.stream().filter(p -> !p.conIa()).count() == 1, "debe haber un solo plano sin IA (el cierre)");
        for (PlanoPlantilla plano : planosConIa()) {
            exigir(plano.foto() != null && plano.maxPalabras() > 0, "plano " + plano.orden() + " sin foto o sin límite");
        }
        exigir(cierre().maxPalabras() > 0, "falta el máximo de palabras de la frase del cierre");
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
