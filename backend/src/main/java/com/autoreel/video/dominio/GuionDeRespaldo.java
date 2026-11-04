package com.autoreel.video.dominio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PlantillaVideo;
import com.autoreel.nicho.dominio.PlantillaVideo.FuenteDeRespaldo;
import com.autoreel.nicho.dominio.PlantillaVideo.PlanoPlantilla;
import com.autoreel.nicho.dominio.Texto;

/**
 * Guion sin IA (RN-25, RNF-09): cuando el modelo no responde o sus tres intentos fallan, el video sigue con los
 * textos del usuario recortados a los límites y, si no sirven, con los textos fijos del paquete de nicho.
 */
public final class GuionDeRespaldo {

    /** Palabras que no pueden cerrar una frase recortada ("Torta de" no sirve). */
    private static final Set<String> CONECTORES = Set.of("de", "del", "con", "y", "e", "o", "u", "en", "a", "al",
            "para", "por", "que", "el", "la", "lo", "los", "las", "un", "una", "unos", "unas", "sin", "su", "sus",
            "tu", "tus", "mi", "mis", "muy", "mas", "pero");
    private static final int MINIMO_PALABRAS = 2;
    private static final int MAXIMO_PALABRAS_ESPECIAL_EN_DESCRIPCION = 30;

    private GuionDeRespaldo() {
    }

    /** Siempre devuelve un guion; si el armado con los datos del usuario no pasa el validador, el fijo. */
    public static GuionPropuesto armar(DatosDelGuion datos, PaqueteNicho paquete) {
        PlantillaVideo plantilla = paquete.plantillaVideo();
        List<GuionPropuesto.PlanoPropuesto> planos = new ArrayList<>();
        for (PlanoPlantilla plano : plantilla.planosConIa()) {
            planos.add(new GuionPropuesto.PlanoPropuesto(plano.orden(), plano.movimientoDeRespaldo(),
                    textoDelPlano(plano, datos, paquete)));
        }
        GuionPropuesto guion = new GuionPropuesto(planos, descripcion(datos, paquete), hashtags(datos, paquete))
                .limpio();
        return ValidadorDeGuion.validar(guion, datos, paquete).isEmpty() ? guion : fijo(datos, paquete);
    }

    /** Solo textos del paquete, más el llamado y el distrito del negocio. */
    public static GuionPropuesto fijo(DatosDelGuion datos, PaqueteNicho paquete) {
        PlantillaVideo plantilla = paquete.plantillaVideo();
        List<GuionPropuesto.PlanoPropuesto> planos = plantilla.planosConIa().stream()
                .map(plano -> new GuionPropuesto.PlanoPropuesto(plano.orden(), plano.movimientoDeRespaldo(),
                        textoFijo(plano, datos, paquete)))
                .toList();
        String descripcion = oracion(plantilla.descripcionFijaDeRespaldo().replace("{llamado}", llamado(datos, paquete)));
        return new GuionPropuesto(planos, descripcion, hashtags(datos, paquete)).limpio();
    }

    /**
     * Recorta un texto del usuario para la pantalla: hasta el primer signo de puntuación, al máximo de palabras, sin
     * conectores al final y con mayúscula inicial.
     */
    public static String recortar(String texto, int maxPalabras) {
        if (texto == null) {
            return "";
        }
        String primeraFrase = Arrays.stream(texto.split("[,.;:!?¡¿()\"«»“”\\n]"))
                .map(String::strip)
                .filter(parte -> !parte.isEmpty())
                .findFirst()
                .orElse("");
        List<String> palabras = new ArrayList<>(Arrays.asList(primeraFrase.split("\\s+")));
        if (palabras.size() > maxPalabras) {
            palabras = new ArrayList<>(palabras.subList(0, maxPalabras));
        }
        return conMayuscula(sinConectoresAlFinal(String.join(" ", palabras)));
    }

    /** Quita las palabras del final que dejan la frase cortada ("de", "con", "para"...) y los signos sueltos. */
    public static String sinConectoresAlFinal(String texto) {
        List<String> palabras = new ArrayList<>(Arrays.asList(texto.strip().split("\\s+")));
        while (!palabras.isEmpty() && (CONECTORES.contains(Texto.normalizar(palabras.getLast()))
                || Texto.normalizar(palabras.getLast()).isEmpty())) {
            palabras.removeLast();
        }
        return String.join(" ", palabras);
    }

    private static String textoDelPlano(PlanoPlantilla plano, DatosDelGuion datos, PaqueteNicho paquete) {
        for (FuenteDeRespaldo fuente : plano.respaldoDesde()) {
            String candidato = switch (fuente) {
                case GANCHO_DEL_TIPO -> paquete.plantillaVideo().ganchosDeRespaldo().get(datos.tipoVideo());
                case PRODUCTO -> recortar(datos.producto(), plano.maxPalabras());
                case ESPECIAL -> recortar(datos.especial(), plano.maxPalabras());
                case DESCRIPCION_PRODUCTO -> recortar(datos.descripcionProducto(), plano.maxPalabras());
            };
            if (candidato != null && Texto.contarPalabras(candidato) >= MINIMO_PALABRAS
                    && ValidadorDeGuion.erroresDelTextoEnPantalla(plano.orden(), candidato, plano.maxPalabras(),
                            datos, paquete).isEmpty()) {
                return candidato;
            }
        }
        return plano.textoDeRespaldo();
    }

    /**
     * Texto del paquete para el plano: el gancho del tipo de video si el plano lo usa y el texto fijo del plano, el
     * primero que cumpla las reglas para este negocio. Si ninguno las cumple (el negocio prohibió esas palabras), el
     * texto fijo: el usuario lo puede cambiar en el storyboard.
     */
    private static String textoFijo(PlanoPlantilla plano, DatosDelGuion datos, PaqueteNicho paquete) {
        List<String> candidatos = new ArrayList<>();
        if (plano.respaldoDesde().contains(FuenteDeRespaldo.GANCHO_DEL_TIPO)) {
            String gancho = paquete.plantillaVideo().ganchosDeRespaldo().get(datos.tipoVideo());
            if (gancho != null) {
                candidatos.add(gancho);
            }
        }
        candidatos.add(plano.textoDeRespaldo());
        return candidatos.stream()
                .filter(texto -> ValidadorDeGuion.erroresDelTextoEnPantalla(plano.orden(), texto, plano.maxPalabras(),
                        datos, paquete).isEmpty())
                .findFirst()
                .orElse(plano.textoDeRespaldo());
    }

    /** Con el producto y lo especial; sin lo especial si no sirve; la fija si tampoco. */
    private static String descripcion(DatosDelGuion datos, PaqueteNicho paquete) {
        PlantillaVideo plantilla = paquete.plantillaVideo();
        String llamado = llamado(datos, paquete);
        List<String> hashtags = hashtags(datos, paquete);
        String especial = recortarOracion(datos.especial());
        for (String conEspecial : List.of(especial, "")) {
            String candidata = oracion(plantilla.descripcionDeRespaldo()
                    .replace("{producto}", datos.producto() == null ? "" : datos.producto().strip())
                    .replace("{especial}", conEspecial)
                    .replace("{llamado}", llamado));
            if (ValidadorDeGuion.problemasDeContenido("La descripción", candidata, datos, paquete).isEmpty()
                    && ValidadorDeGuion.publicacion(candidata, hashtags).length()
                            <= ValidadorDeGuion.MAXIMO_PUBLICACION) {
                return candidata;
            }
        }
        return oracion(plantilla.descripcionFijaDeRespaldo().replace("{llamado}", llamado));
    }

    private static List<String> hashtags(DatosDelGuion datos, PaqueteNicho paquete) {
        // Un hashtag base que el negocio prohibió se omite: no debe descartar el resto del guion.
        List<String> hashtags = new ArrayList<>(paquete.plantillaVideo().hashtagsBase().stream()
                .filter(hashtag -> ValidadorDeGuion.hashtagPermitido(hashtag, datos, paquete))
                .toList());
        String distrito = datos.distritoHashtag();
        if (!distrito.isEmpty()) {
            hashtags.add("#" + distrito);
        }
        return new GuionPropuesto(List.of(), "", hashtags).limpio().hashtags();
    }

    private static String llamado(DatosDelGuion datos, PaqueteNicho paquete) {
        return paquete.plantillaVideo().llamadoPrincipal(datos.canales(), datos.distrito()).orElse("");
    }

    /** La primera oración de lo especial, con hasta 30 palabras y punto final. */
    private static String recortarOracion(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String primera = texto.strip().split("[.!?\\n]")[0].strip();
        String[] palabras = primera.split("\\s+");
        if (palabras.length > MAXIMO_PALABRAS_ESPECIAL_EN_DESCRIPCION) {
            primera = String.join(" ", Arrays.copyOf(palabras, MAXIMO_PALABRAS_ESPECIAL_EN_DESCRIPCION));
        }
        return primera.isEmpty() ? "" : conMayuscula(primera) + ".";
    }

    /** Une las piezas sin espacios ni puntos repetidos. */
    private static String oracion(String texto) {
        return texto.replaceAll("\\s+", " ")
                .replaceAll(" \\.", ".")
                .replaceAll("\\.{2,}", ".")
                .strip();
    }

    private static String conMayuscula(String texto) {
        if (texto.isEmpty()) {
            return texto;
        }
        int primera = texto.codePointAt(0);
        return new StringBuilder().appendCodePoint(Character.toUpperCase(primera))
                .append(texto.substring(Character.charCount(primera))).toString();
    }
}
