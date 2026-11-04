package com.autoreel.video.dominio;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.autoreel.nicho.dominio.Movimientos.Movimiento;
import com.autoreel.nicho.dominio.PaqueteNicho;
import com.autoreel.nicho.dominio.PlantillaVideo.PlanoPlantilla;
import com.autoreel.nicho.dominio.Texto;
import com.autoreel.nicho.dominio.Vocabulario;

/**
 * Revisa el guion antes de mostrarlo (RN-21 a RN-24). Devuelve los problemas en español: se le envían al modelo en
 * el siguiente intento. Los ingredientes y sabores inventados no se pueden detectar aquí; los controlan las
 * instrucciones del guion.
 */
public final class ValidadorDeGuion {

    /** RN-24: la descripción, con sus hashtags, tiene hasta 600 caracteres. */
    public static final int MAXIMO_PUBLICACION = 600;
    public static final int MINIMO_HASHTAGS = 5;
    public static final int MAXIMO_HASHTAGS = 8;

    private static final Pattern HASHTAG = Pattern.compile("^#[\\p{L}\\p{N}_]+$");
    private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)?");
    /** Precios escritos con número y moneda en palabras: "45 soles", "20 lucas". */
    private static final String PRECIO_EN_PALABRAS = "\\d+(?:[.,]\\d+)?\\s*(?:soles?|lucas?)(?!\\p{L})";
    private static final Pattern MONEDA = Pattern.compile("(?i)(?<!\\p{L})s/|us\\$|\\$|%|" + PRECIO_EN_PALABRAS);
    private static final Pattern PRECIO_EN_PANTALLA = Pattern.compile(
            "(?i)(?<!\\p{L})s/|\\$|%|\\d+\\s*x\\s*\\d+|" + PRECIO_EN_PALABRAS);
    private static final Pattern HASHTAG_EN_TEXTO = Pattern.compile("#[\\p{L}\\p{N}]");
    /** Igual que la columna {@code plano.texto}: un texto con pocas palabras muy largas no debe romper el guardado. */
    public static final int MAXIMO_CARACTERES_TEXTO = 200;

    private ValidadorDeGuion() {
    }

    public static List<String> validar(GuionPropuesto guion, DatosDelGuion datos, PaqueteNicho paquete) {
        List<String> errores = new ArrayList<>();
        List<PlanoPlantilla> esperados = paquete.plantillaVideo().planosConIa();
        Set<Integer> ordenesEsperados = new HashSet<>();
        esperados.forEach(plano -> ordenesEsperados.add(plano.orden()));
        for (GuionPropuesto.PlanoPropuesto plano : guion.planos()) {
            if (plano.orden() == null || !ordenesEsperados.contains(plano.orden())) {
                errores.add("Sobra el plano " + plano.orden() + ": solo se escriben los planos " + ordenesEsperados
                        + ".");
            }
        }
        for (PlanoPlantilla esperado : esperados) {
            List<GuionPropuesto.PlanoPropuesto> iguales = guion.planos().stream()
                    .filter(plano -> plano.orden() != null && plano.orden() == esperado.orden()).toList();
            if (iguales.isEmpty()) {
                errores.add("Falta el plano " + esperado.orden() + ".");
            } else if (iguales.size() > 1) {
                errores.add("El plano " + esperado.orden() + " viene repetido.");
            } else {
                GuionPropuesto.PlanoPropuesto plano = iguales.getFirst();
                errores.addAll(erroresDelMovimiento(esperado.orden(), plano.movimiento(), datos, paquete));
                errores.addAll(erroresDelTextoEnPantalla(esperado.orden(), plano.texto(), esperado.maxPalabras(),
                        datos, paquete));
            }
        }
        if (guion.descripcion() == null || guion.descripcion().isBlank()) {
            errores.add("Falta la descripción de la publicación.");
        } else {
            if (HASHTAG_EN_TEXTO.matcher(guion.descripcion()).find()) {
                errores.add("La descripción no debe tener hashtags; van solo en la lista de hashtags.");
            }
            errores.addAll(problemasDeContenido("La descripción", guion.descripcion(), datos, paquete));
        }
        errores.addAll(erroresDeHashtags(guion.hashtags(), datos, paquete));
        if (guion.descripcion() != null
                && publicacion(guion.descripcion(), guion.hashtags()).length() > MAXIMO_PUBLICACION) {
            errores.add("La descripción con sus hashtags pasa de " + MAXIMO_PUBLICACION
                    + " caracteres; acórtala.");
        }
        return errores;
    }

    /** Lo que se publica: la descripción y, en otro párrafo, los hashtags. */
    public static String publicacion(String descripcion, List<String> hashtags) {
        return hashtags.isEmpty() ? descripcion : descripcion + "\n\n" + String.join(" ", hashtags);
    }

    /** Reglas del texto en pantalla de un plano (RN-21 a RN-23). También las usa el guion de respaldo. */
    public static List<String> erroresDelTextoEnPantalla(int orden, String texto, int maxPalabras,
            DatosDelGuion datos, PaqueteNicho paquete) {
        String donde = "El plano " + orden;
        if (texto == null || texto.isBlank()) {
            return List.of(donde + " no tiene texto.");
        }
        List<String> errores = new ArrayList<>();
        if (texto.length() > MAXIMO_CARACTERES_TEXTO) {
            errores.add(donde + " pasa de " + MAXIMO_CARACTERES_TEXTO + " caracteres.");
        }
        int palabras = Texto.contarPalabras(texto);
        if (palabras > maxPalabras) {
            errores.add(donde + " tiene " + palabras + " palabras; el máximo es " + maxPalabras + ".");
        }
        if (Texto.tieneCaracteresNoDibujables(texto)) {
            errores.add(donde + " tiene emojis o saltos de línea, que no se pueden mostrar en el video.");
        }
        if (PRECIO_EN_PANTALLA.matcher(texto).find()) {
            errores.add(donde + " menciona un precio; los precios solo van en el cierre.");
        }
        errores.addAll(problemasDeContenido(donde, texto, datos, paquete));
        return errores;
    }

    /** La primera frase prohibida que aparece en el texto (RN-23), sin distinguir mayúsculas ni tildes. */
    public static Optional<String> fraseProhibida(String texto, DatosDelGuion datos, PaqueteNicho paquete) {
        return frasesProhibidas(datos, paquete.vocabulario()).stream()
                .filter(frase -> Texto.contieneFrase(texto, frase))
                .findFirst();
    }

    private static List<String> erroresDelMovimiento(int orden, String movimiento, DatosDelGuion datos,
            PaqueteNicho paquete) {
        boolean permitido = paquete.movimientos().para(datos.tipoVideo()).stream()
                .map(Movimiento::id)
                .anyMatch(id -> id.equals(movimiento));
        return permitido ? List.of()
                : List.of("El movimiento \"" + movimiento + "\" del plano " + orden + " no está en la lista.");
    }

    /** RN-22 (nada inventado) y RN-23 (nada prohibido) en un texto del guion. */
    static List<String> problemasDeContenido(String donde, String texto, DatosDelGuion datos, PaqueteNicho paquete) {
        List<String> errores = new ArrayList<>();
        List<String> fuentes = datos.fuentes();
        Set<String> numerosPermitidos = numeros(String.join(" ", fuentes));
        for (String numero : numeros(texto)) {
            if (!numerosPermitidos.contains(numero)) {
                errores.add(donde + " menciona \"" + numero + "\", que no está en los datos del negocio.");
            }
        }
        String fuentesEnMinusculas = String.join(" ", fuentes).toLowerCase(Locale.ROOT);
        Matcher moneda = MONEDA.matcher(texto);
        while (moneda.find()) {
            if (!fuentesEnMinusculas.contains(moneda.group().toLowerCase(Locale.ROOT))) {
                errores.add(donde + " menciona un precio o un porcentaje que el negocio no escribió.");
                break;
            }
        }
        for (String promocion : paquete.vocabulario().palabrasDePromocion()) {
            if (Texto.contieneFrase(texto, promocion)
                    && fuentes.stream().noneMatch(fuente -> Texto.contieneFrase(fuente, promocion))) {
                errores.add(donde + " menciona \"" + promocion + "\", una promoción que el negocio no escribió.");
            }
        }
        fraseProhibida(texto, datos, paquete).ifPresent(frase ->
                errores.add(donde + " usa \"" + frase + "\", que no está permitido."));
        return errores;
    }

    private static List<String> erroresDeHashtags(List<String> hashtags, DatosDelGuion datos, PaqueteNicho paquete) {
        List<String> errores = new ArrayList<>();
        if (hashtags.size() < MINIMO_HASHTAGS || hashtags.size() > MAXIMO_HASHTAGS) {
            errores.add("Debe haber entre " + MINIMO_HASHTAGS + " y " + MAXIMO_HASHTAGS + " hashtags distintos; hay "
                    + hashtags.size() + ".");
        }
        List<String> frases = frasesProhibidas(datos, paquete.vocabulario());
        Set<String> numerosPermitidos = numeros(String.join(" ", datos.fuentes()));
        for (String hashtag : hashtags) {
            if (!HASHTAG.matcher(hashtag).matches()) {
                errores.add("El hashtag \"" + hashtag + "\" debe empezar con # y no tener espacios ni signos.");
                continue;
            }
            String cuerpo = cuerpo(hashtag);
            frases.stream().filter(frase -> usaFrase(cuerpo, frase)).findFirst().ifPresent(frase ->
                    errores.add("El hashtag " + hashtag + " usa \"" + frase + "\", que no está permitido."));
            for (String promocion : paquete.vocabulario().palabrasDePromocion()) {
                if (usaFrase(cuerpo, promocion)
                        && datos.fuentes().stream().noneMatch(fuente -> Texto.contieneFrase(fuente, promocion))) {
                    errores.add("El hashtag " + hashtag + " menciona \"" + promocion
                            + "\", una promoción que el negocio no escribió.");
                }
            }
            for (String numero : numeros(hashtag)) {
                if (!numerosPermitidos.contains(numero)) {
                    errores.add("El hashtag " + hashtag + " menciona \"" + numero
                            + "\", que no está en los datos del negocio.");
                }
            }
        }
        String distrito = datos.distritoHashtag();
        if (!distrito.isEmpty() && hashtags.stream().noneMatch(hashtag -> cuerpo(hashtag).contains(distrito))) {
            errores.add("Falta un hashtag del distrito, por ejemplo #" + distrito + ".");
        }
        return errores;
    }

    /** Si un hashtag no usa ninguna frase prohibida para este negocio (lo usa el guion de respaldo). */
    public static boolean hashtagPermitido(String hashtag, DatosDelGuion datos, PaqueteNicho paquete) {
        String cuerpo = cuerpo(hashtag);
        return frasesProhibidas(datos, paquete.vocabulario()).stream().noneMatch(frase -> usaFrase(cuerpo, frase));
    }

    /** Frases prohibidas del nicho y del negocio; las de salud valen si la descripción del producto las dice. */
    private static List<String> frasesProhibidas(DatosDelGuion datos, Vocabulario vocabulario) {
        Set<String> frases = new LinkedHashSet<>(vocabulario.prohibidas());
        vocabulario.prohibidasSalvoQueLasDeclareElProducto().stream()
                .filter(frase -> !Texto.contieneFrase(datos.descripcionProducto(), frase))
                .forEach(frases::add);
        frases.addAll(datos.palabrasProhibidas());
        return List.copyOf(frases);
    }

    /**
     * En un hashtag no hay espacios: una frase de varias palabras se busca pegada ("#sinazucar"); una sola palabra
     * debe ser el hashtag completo, para no confundir "fit" con "#confites".
     */
    private static boolean usaFrase(String cuerpo, String frase) {
        List<String> palabras = Texto.tokens(frase);
        if (palabras.isEmpty()) {
            return false;
        }
        String pegada = String.join("", palabras);
        return palabras.size() > 1 ? cuerpo.contains(pegada) : cuerpo.equals(pegada);
    }

    private static String cuerpo(String hashtag) {
        return Texto.normalizar(hashtag).replace(" ", "");
    }

    private static Set<String> numeros(String texto) {
        Set<String> numeros = new LinkedHashSet<>();
        if (texto == null) {
            return numeros;
        }
        Matcher numero = NUMERO.matcher(texto);
        while (numero.find()) {
            numeros.add(numero.group().replace(',', '.'));
        }
        return numeros;
    }
}
