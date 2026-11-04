package com.autoreel.nicho.dominio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plantilla del video de 15 segundos (sección 7.4): cuatro planos fijos, el último sin IA.
 *
 * @param llamados           llamado a la acción por canal del onboarding, en orden de prioridad; "{distrito}" se
 *                           reemplaza por el distrito del negocio.
 * @param ganchosDeRespaldo  texto fijo del gancho por tipo de video, para el guion de respaldo (RN-25).
 * @param descripcionDeRespaldo     descripción del respaldo con los datos del usuario ({producto}, {especial},
 *                                  {llamado}).
 * @param descripcionFijaDeRespaldo descripción sin datos del usuario, si la anterior no pasa el validador.
 */
public record PlantillaVideo(List<PlanoPlantilla> planos, LinkedHashMap<String, String> llamados,
        Map<String, String> ganchosDeRespaldo, String descripcionDeRespaldo, String descripcionFijaDeRespaldo,
        List<String> hashtagsBase,
        int maximoCaracteresPrecio, String avisoDeRespaldo) {

    /**
     * @param foto                 foto del producto que usa el plano (DETALLE, ENTERO o PORCION); vacía en el cierre.
     * @param conIa                el cierre no usa IA: lo arma el sistema.
     * @param movimientoDeRespaldo movimiento del guion de respaldo.
     * @param respaldoDesde        de dónde sale el texto del guion de respaldo, en orden de preferencia.
     * @param textoDeRespaldo      texto fijo si ningún texto del usuario sirve para el plano.
     */
    public record PlanoPlantilla(int orden, String nombre, String foto, int maxPalabras, int segundos,
            boolean conIa, String movimientoDeRespaldo, List<FuenteDeRespaldo> respaldoDesde,
            String textoDeRespaldo) {

        public PlanoPlantilla {
            respaldoDesde = respaldoDesde == null ? List.of() : List.copyOf(respaldoDesde);
        }
    }

    /** Datos del usuario que el guion de respaldo puede poner en un plano. */
    public enum FuenteDeRespaldo {
        /** El gancho fijo del tipo de video ({@code ganchosDeRespaldo}). */
        GANCHO_DEL_TIPO,
        PRODUCTO,
        ESPECIAL,
        DESCRIPCION_PRODUCTO
    }

    public PlantillaVideo {
        planos = List.copyOf(planos);
        hashtagsBase = List.copyOf(hashtagsBase);
    }

    public PlanoPlantilla plano(int orden) {
        return planos.stream().filter(plano -> plano.orden() == orden).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe el plano " + orden));
    }

    /** Planos que escribe la IA (todos menos el cierre). */
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

    public void validar(List<Opcion> canalesDelOnboarding, List<Opcion> tiposDeVideo) {
        exigir(planos.size() == 4, "la plantilla debe tener 4 planos");
        exigir(planos.stream().filter(p -> !p.conIa()).count() == 1, "debe haber un solo plano sin IA (el cierre)");
        for (PlanoPlantilla plano : planosConIa()) {
            exigir(plano.foto() != null && plano.maxPalabras() > 0, "plano " + plano.orden() + " sin foto o sin límite");
            exigir(plano.textoDeRespaldo() != null && !plano.textoDeRespaldo().isBlank(),
                    "plano " + plano.orden() + " sin texto de respaldo");
        }
        canalesDelOnboarding.forEach(canal -> exigir(llamados.containsKey(canal.valor()),
                "falta el llamado del canal " + canal.valor()));
        tiposDeVideo.forEach(tipo -> exigir(ganchosDeRespaldo.containsKey(tipo.valor()),
                "falta el gancho de respaldo de " + tipo.valor()));
        exigir(hashtagsBase.size() >= 3 && hashtagsBase.size() <= 6, "debe haber entre 3 y 6 hashtags base");
        exigir(descripcionDeRespaldo != null && descripcionFijaDeRespaldo != null
                && !descripcionFijaDeRespaldo.contains("{producto}") && !descripcionFijaDeRespaldo.contains("{especial}"),
                "faltan las descripciones de respaldo o la fija usa datos del usuario");
        exigir(maximoCaracteresPrecio > 0, "falta el máximo de caracteres del precio");
    }

    private static void exigir(boolean condicion, String problema) {
        if (!condicion) {
            throw new IllegalStateException("Plantilla de video inválida: " + problema);
        }
    }
}
