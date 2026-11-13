package com.autoreel.video.dominio;

import static com.autoreel.video.dominio.Guiones.CIERRE;
import static com.autoreel.video.dominio.Guiones.PAQUETE;
import static com.autoreel.video.dominio.Guiones.conCierre;
import static com.autoreel.video.dominio.Guiones.datos;
import static com.autoreel.video.dominio.Guiones.guion;
import static com.autoreel.video.dominio.Guiones.valido;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.autoreel.nicho.dominio.Texto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RN-21 a RN-24 (V18 y V19) con el paquete Pastelería real. */
class ValidadorDeGuionTest {

    private static final String DESCRIPCION = "Nuestra Torta Tres Leches lleva fresas frescas de Huaral.";
    private static final List<String> HASHTAGS = List.of("#pasteleria", "#tortas", "#tresleches", "#postres",
            "#miraflores");

    @Test
    void unGuionCorrectoNoTieneProblemas() {
        assertThat(ValidadorDeGuion.validar(valido(), datos(), PAQUETE)).isEmpty();
    }

    @Test
    void v18UnTextoConMasPalabrasQueSuMaximoSeRechaza() {
        GuionPropuesto largo = guion("Un antojo de fresas frescas para hoy mismo", "Torta Tres Leches",
                "Cada porción con fresas", DESCRIPCION, HASHTAGS);

        assertThat(ValidadorDeGuion.validar(largo, datos(), PAQUETE))
                .containsExactly("El plano 1 tiene 8 palabras; el máximo es 6.");
    }

    @Test
    void faltanOSobranPlanos() {
        GuionPropuesto incompleto = new GuionPropuesto(List.of(
                new GuionPropuesto.PlanoPropuesto(1, "ACERCAMIENTO_LENTO", "Un antojo de fresas"),
                new GuionPropuesto.PlanoPropuesto(1, "ACERCAMIENTO_LENTO", "Otro antojo de fresas"),
                new GuionPropuesto.PlanoPropuesto(4, "LUZ_CALIDA", "Pide por WhatsApp")), CIERRE, DESCRIPCION,
                HASHTAGS);

        assertThat(ValidadorDeGuion.validar(incompleto, datos(), PAQUETE)).contains(
                "El plano 1 viene repetido.", "Falta el plano 2.", "Falta el plano 3.",
                "Sobra el plano 4: solo se escriben los planos [1, 2, 3].");
    }

    @Test
    void elMovimientoDebeSerDeLaListaYVaporSoloEnRecienHorneado() {
        GuionPropuesto conVapor = new GuionPropuesto(List.of(
                new GuionPropuesto.PlanoPropuesto(1, "VAPOR_SUAVE", "Un antojo de fresas"),
                new GuionPropuesto.PlanoPropuesto(2, "ZOOM_LOCO", "Torta Tres Leches"),
                new GuionPropuesto.PlanoPropuesto(3, "LUZ_CALIDA", "Cada porción con fresas")), CIERRE, DESCRIPCION,
                HASHTAGS);

        assertThat(ValidadorDeGuion.validar(conVapor, datos(), PAQUETE)).containsExactly(
                "El movimiento \"VAPOR_SUAVE\" del plano 1 no está en la lista.",
                "El movimiento \"ZOOM_LOCO\" del plano 2 no está en la lista.");
        DatosDelGuion recienHorneado = datos("RECIEN_HORNEADO", "Recién salido del horno", "Torta Tres Leches",
                "Fresas frescas", null, List.of());
        assertThat(ValidadorDeGuion.validar(conVapor, recienHorneado, PAQUETE))
                .containsExactly("El movimiento \"ZOOM_LOCO\" del plano 2 no está en la lista.");
    }

    @Test
    void v19UnPrecioQueElUsuarioNoEscribioSeRechaza() {
        GuionPropuesto conPrecio = guion("Un antojo de fresas", "Torta Tres Leches", "Cada porción con fresas",
                DESCRIPCION + " Hoy a S/ 50.", HASHTAGS);

        assertThat(ValidadorDeGuion.validar(conPrecio, datos(), PAQUETE)).contains(
                "La descripción menciona \"50\", que no está en los datos del negocio.",
                "La descripción menciona un precio o un porcentaje que el negocio no escribió.");

        DatosDelGuion conPrecioDelUsuario = datos("PROMOCION", "Promoción", "Torta Tres Leches", "Fresas frescas",
                "S/ 50 la torta entera", List.of());
        assertThat(ValidadorDeGuion.validar(conPrecioEnElCierre(conPrecio, "S/ 50 la torta entera"),
                conPrecioDelUsuario, PAQUETE)).isEmpty();
    }

    @Test
    void enLosTextosEnPantallaNuncaVaUnPrecioAunqueElUsuarioLoHayaEscrito() {
        DatosDelGuion conPrecio = datos("PROMOCION", "Promoción", "Torta Tres Leches", "Fresas frescas", "S/ 65",
                List.of());
        GuionPropuesto precioEnPantalla = conPrecioEnElCierre(guion("Hoy a S/ 65", "Torta Tres Leches",
                "Cada porción con fresas", DESCRIPCION, HASHTAGS), "S/ 65");

        assertThat(ValidadorDeGuion.validar(precioEnPantalla, conPrecio, PAQUETE))
                .containsExactly("El plano 1 menciona un precio; los precios solo van en el cierre.");
    }

    @Test
    void unPrecioEscritoEnPalabrasTambienSeDetecta() {
        DatosDelGuion conPrecio = datos("PROMOCION", "Promoción", "Torta Tres Leches", "Fresas frescas",
                "45 soles", List.of());
        GuionPropuesto enPantalla = conPrecioEnElCierre(guion("Hoy a solo 45 soles", "Torta Tres Leches",
                "Cada porción con fresas", DESCRIPCION, HASHTAGS), "45 soles la torta");

        assertThat(ValidadorDeGuion.validar(enPantalla, conPrecio, PAQUETE))
                .containsExactly("El plano 1 menciona un precio; los precios solo van en el cierre.");
        GuionPropuesto enDescripcion = guion("Un antojo de fresas", "Torta Tres Leches", "Cada porción con fresas",
                DESCRIPCION + " Llévala a 20 soles.", HASHTAGS);
        assertThat(ValidadorDeGuion.validar(enDescripcion, datos(), PAQUETE)).contains(
                "La descripción menciona un precio o un porcentaje que el negocio no escribió.");
    }

    @Test
    void hashtagsFueraDeLugarOConPromocionesInventadas() {
        assertThat(ValidadorDeGuion.validar(guionConDescripcion(DESCRIPCION + " #tortas"), datos(), PAQUETE))
                .containsExactly("La descripción no debe tener hashtags; van solo en la lista de hashtags.");
        assertThat(ValidadorDeGuion.validar(guionConHashtags(List.of("#pasteleria", "#promo", "#tresleches",
                "#postres", "#miraflores")), datos(), PAQUETE))
                .containsExactly("El hashtag #promo menciona \"promo\", una promoción que el negocio no escribió.");
    }

    @Test
    void unTextoDePocasPalabrasMuyLargasSeRechaza() {
        GuionPropuesto largo = guion("Torta " + "!".repeat(200), "Torta Tres Leches", "Cada porción con fresas",
                DESCRIPCION, HASHTAGS);

        assertThat(ValidadorDeGuion.validar(largo, datos(), PAQUETE))
                .containsExactly("El plano 1 pasa de 200 caracteres.");
    }

    @Test
    void unaPromocionInventadaSeRechaza() {
        GuionPropuesto conPromo = guion("Un antojo con descuento", "Torta Tres Leches", "Cada porción con fresas",
                DESCRIPCION + " Aprovecha el 2x1.", HASHTAGS);

        assertThat(ValidadorDeGuion.validar(conPromo, datos(), PAQUETE)).contains(
                "El plano 1 menciona \"descuento\", una promoción que el negocio no escribió.",
                "La descripción menciona \"2x1\", una promoción que el negocio no escribió.");
    }

    @Test
    void losNumerosDeLosDatosDelNegocioSePermiten() {
        GuionPropuesto conNumero = guion("Un antojo de fresas", "Torta Tres Leches", "Cada porción con fresas",
                DESCRIPCION + " Escríbenos al 987654321, de 9 a 8.", HASHTAGS);

        assertThat(ValidadorDeGuion.validar(conNumero, datos(), PAQUETE)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "'La mejor torta de casa', la mejor",
            "'Torta SIN AZÚCAR de casa', sin azúcar",
            "'Ideal para tu dieta', dieta"
    })
    void lasFrasesProhibidasSeDetectanSinMayusculasNiTildes(String texto, String frase) {
        GuionPropuesto prohibido = guion(texto, "Torta Tres Leches", "Cada porción con fresas", DESCRIPCION,
                HASHTAGS);

        assertThat(ValidadorDeGuion.validar(prohibido, datos(), PAQUETE))
                .containsExactly("El plano 1 usa \"" + frase + "\", que no está permitido.");
    }

    @Test
    void unaAfirmacionDeSaludValeSiLaDescripcionDelProductoLaDice() {
        DatosDelGuion sinGluten = new DatosDelGuion("PRODUCTO_ESTRELLA", "Producto estrella", "Torta Tres Leches",
                "Bizcocho sin gluten con fresas frescas.", "Fresas", null, null, "Dulce Encanto", "CASERO", "Casero",
                null, null, "Lima", "Miraflores", null, null, null, List.of("TIENDA"), List.of());
        GuionPropuesto guion = guion("Torta sin gluten de fresas", "Torta Tres Leches", "Cada porción con fresas",
                DESCRIPCION, HASHTAGS);

        assertThat(ValidadorDeGuion.validar(guion, sinGluten, PAQUETE)).isEmpty();
        assertThat(ValidadorDeGuion.validar(guion, datos(), PAQUETE))
                .containsExactly("El plano 1 usa \"sin gluten\", que no está permitido.");
    }

    @Test
    void reglasDeLosHashtags() {
        assertThat(ValidadorDeGuion.validar(guionConHashtags(List.of("#pasteleria", "#tortas", "#miraflores",
                "#postres")), datos(), PAQUETE))
                .containsExactly("Debe haber entre 5 y 8 hashtags distintos; hay 4.");
        assertThat(ValidadorDeGuion.validar(guionConHashtags(List.of("#pasteleria", "#tortas", "#tresleches",
                "#postres", "#lima")), datos(), PAQUETE))
                .containsExactly("Falta un hashtag del distrito, por ejemplo #miraflores.");
        assertThat(ValidadorDeGuion.validar(guionConHashtags(List.of("#pasteleria", "#tortas", "sin espacios",
                "#postres", "#miraflores")), datos(), PAQUETE))
                .containsExactly("El hashtag \"sin espacios\" debe empezar con # y no tener espacios ni signos.");
        assertThat(ValidadorDeGuion.validar(guionConHashtags(List.of("#pasteleria", "#tortasinazucar", "#tresleches",
                "#postres", "#miraflores")), datos(), PAQUETE))
                .containsExactly("El hashtag #tortasinazucar usa \"sin azúcar\", que no está permitido.");
        // Una palabra prohibida suelta solo cuenta si es el hashtag completo: "fit" no está en "#confites".
        assertThat(ValidadorDeGuion.validar(guionConHashtags(List.of("#pasteleria", "#confites", "#tresleches",
                "#postres", "#MirafloresLima")), datos(), PAQUETE)).isEmpty();
    }

    @Test
    void losHashtagsRepetidosCuentanUnaVez() {
        GuionPropuesto repetidos = guionConHashtags(List.of("#pasteleria", "#Pasteleria", "#tortas", "#postres",
                "#miraflores")).limpio();

        assertThat(repetidos.hashtags()).hasSize(4);
        assertThat(ValidadorDeGuion.validar(repetidos, datos(), PAQUETE))
                .containsExactly("Debe haber entre 5 y 8 hashtags distintos; hay 4.");
    }

    @Test
    void laDescripcionConSusHashtagsTieneHasta600Caracteres() {
        String larga = "Torta Tres Leches con fresas frescas de Huaral. ".repeat(12);

        assertThat(ValidadorDeGuion.validar(guionConDescripcion(larga), datos(), PAQUETE))
                .containsExactly("La descripción con sus hashtags pasa de 600 caracteres; acórtala.");
    }

    @Test
    void sinEmojisNiSaltosDeLineaEnPantalla() {
        GuionPropuesto conEmoji = guion("Un antojo de fresas 🍓", "Torta Tres\nLeches", "Cada porción con fresas",
                DESCRIPCION, HASHTAGS);

        assertThat(ValidadorDeGuion.validar(conEmoji, datos(), PAQUETE)).containsExactly(
                "El plano 1 tiene emojis o saltos de línea, que no se pueden mostrar en el video.",
                "El plano 2 tiene emojis o saltos de línea, que no se pueden mostrar en el video.");
    }

    @Test
    void losTresPlanosNoRepitenElMismoMovimiento() {
        GuionPropuesto repetido = new GuionPropuesto(List.of(
                new GuionPropuesto.PlanoPropuesto(1, "ORBITA_FRONTAL", "Un antojo de fresas"),
                new GuionPropuesto.PlanoPropuesto(2, "ORBITA_FRONTAL", "Torta Tres Leches"),
                new GuionPropuesto.PlanoPropuesto(3, "ORBITA_FRONTAL", "Cada porción con fresas")), CIERRE,
                DESCRIPCION, HASHTAGS);

        assertThat(ValidadorDeGuion.validar(repetido, datos(), PAQUETE)).containsExactly(
                "Los planos repiten el movimiento \"ORBITA_FRONTAL\"; elige movimientos distintos para que el "
                        + "video tenga variedad.");
    }

    /** La queja del usuario: solo acercamientos se ven planos. Al menos dos planos rodean, suben o cruzan el producto. */
    @Test
    void alMenosDosPlanosUsanCamara3d() {
        GuionPropuesto soloAcercamientos = new GuionPropuesto(List.of(
                new GuionPropuesto.PlanoPropuesto(1, "ACERCAMIENTO_LENTO", "Un antojo de fresas"),
                new GuionPropuesto.PlanoPropuesto(2, "ALEJAMIENTO_LENTO", "Torta Tres Leches"),
                new GuionPropuesto.PlanoPropuesto(3, "LUZ_CALIDA", "Cada porción con fresas")), CIERRE,
                DESCRIPCION, HASHTAGS);
        GuionPropuesto conOrbitas = new GuionPropuesto(List.of(
                new GuionPropuesto.PlanoPropuesto(1, "MACRO_DETALLE", "Un antojo de fresas"),
                new GuionPropuesto.PlanoPropuesto(2, "ACERCAMIENTO_LENTO", "Torta Tres Leches"),
                new GuionPropuesto.PlanoPropuesto(3, "GRUA_REVELADO", "Cada porción con fresas")), CIERRE,
                DESCRIPCION, HASHTAGS);

        assertThat(ValidadorDeGuion.validar(soloAcercamientos, datos(), PAQUETE)).containsExactly(
                "Al menos 2 planos deben usar un movimiento marcado como cámara 3D (que rodee, suba o cruce el "
                        + "producto); hay 1.");
        assertThat(ValidadorDeGuion.validar(conOrbitas, datos(), PAQUETE)).isEmpty();
    }

    @Test
    void elCierreSinPrecioDelNegocioLlevaSoloLaFrase() {
        GuionPropuesto inventado = conCierre(new GuionPropuesto.CierrePropuesto("S/ 50", "Tu antojo te espera",
                null));
        String sinPrecio = "El negocio no escribió un precio con números: en el cierre, \"precio\" debe ser null y el "
                + "cierre muestra la frase.";

        assertThat(ValidadorDeGuion.validar(inventado, datos(), PAQUETE)).containsExactly(sinPrecio);
        // Una instrucción del negocio no es un precio: no se muestra en el cierre.
        DatosDelGuion conInstruccion = datos("PRODUCTO_ESTRELLA", "Producto estrella", "Torta Tres Leches",
                "Fresas frescas", "no menciones precio solo que están", List.of());
        GuionPropuesto copiado = conCierre(new GuionPropuesto.CierrePropuesto("no menciones precio solo que están",
                "Ya están listas para ti", null));
        assertThat(ValidadorDeGuion.validar(copiado, conInstruccion, PAQUETE)).containsExactly(sinPrecio);
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto(null,
                "Ya están listas para ti", null)), conInstruccion, PAQUETE)).isEmpty();
    }

    @Test
    void elPrecioDelCierreUsaLosNumerosQueEscribioElNegocio() {
        DatosDelGuion conPrecio = datos("PROMOCION", "Promoción", "Torta Tres Leches", "Fresas frescas",
                "la torta entera a S/ 65 y la porción a S/ 8", List.of());

        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto("S/ 65 la torta entera",
                "Tu antojo te espera", null)), conPrecio, PAQUETE)).isEmpty();
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto("S/ 60 la torta entera",
                "Tu antojo te espera", null)), conPrecio, PAQUETE))
                .containsExactly("El precio del cierre menciona \"60\", que no está en el precio que escribió el "
                        + "negocio.");
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto("Precio especial",
                "Tu antojo te espera", null)), conPrecio, PAQUETE))
                .containsExactly("El precio del cierre debe llevar los números del precio que escribió el negocio.");
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto(
                "S/ 65 la torta entera y S/ 8 cada porción", "Tu antojo te espera", null)), conPrecio, PAQUETE))
                .containsExactly("El precio del cierre pasa de 40 caracteres; resúmelo.");
    }

    /** V19: los números del precio salen de la respuesta de precio, no de la descripción del producto. */
    @Test
    void elPrecioDelCierreNoTomaNumerosDeLaDescripcion() {
        DatosDelGuion conPrecio = new DatosDelGuion("PROMOCION", "Promoción", "Torta Tres Leches",
                "Bizcocho de tres leches para 12 porciones.", "Fresas frescas", "S/ 65 la torta", null,
                "Pastelería Dulce Encanto", "CASERO", "Casero", null, null, "Lima", "Miraflores", null,
                "Lunes a sábado de 9 a 8", null, List.of("TIENDA"), List.of());

        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto("S/ 12 la porción",
                "Tu antojo te espera", null)), conPrecio, PAQUETE))
                .containsExactly("El precio del cierre menciona \"12\", que no está en el precio que escribió el "
                        + "negocio.");
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto("S/ 65 la torta entera",
                "Tu antojo te espera", null)), conPrecio, PAQUETE)).isEmpty();
    }

    /** Si el negocio escribió un precio real con moneda, el cierre lo muestra; una instrucción con "no", no. */
    @Test
    void unPrecioRealDelNegocioNoSePuedeOmitirEnElCierre() {
        DatosDelGuion conPrecio = datos("PROMOCION", "Promoción", "Torta Tres Leches", "Fresas frescas",
                "la torta a S/ 65", List.of());
        assertThat(ValidadorDeGuion.validar(conCierre(CIERRE), conPrecio, PAQUETE)).containsExactly(
                "El negocio escribió un precio (\"la torta a S/ 65\"): resúmelo en \"precio\" con sus mismos números "
                        + "y moneda.");

        DatosDelGuion conInstruccion = datos("PROMOCION", "Promoción", "Torta Tres Leches", "Fresas frescas",
                "no pongas los S/ 65, solo que hay", List.of());
        assertThat(ValidadorDeGuion.validar(conCierre(CIERRE), conInstruccion, PAQUETE)).isEmpty();
    }

    @Test
    void reglasDeLaFraseDelCierre() {
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto(null, null, null)), datos(),
                PAQUETE)).containsExactly("Falta la frase del cierre.");
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto(null,
                "Una torta para ti y tu familia", null)), datos(), PAQUETE))
                .containsExactly("La frase del cierre tiene 7 palabras; el máximo es 6.");
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto(null, "Hoy a S/ 65", null)),
                datos(), PAQUETE))
                .contains("La frase del cierre menciona un precio; el precio va solo en \"precio\".");
        assertThat(ValidadorDeGuion.validar(conCierre(new GuionPropuesto.CierrePropuesto(null, "La mejor de Lima",
                null)), datos(), PAQUETE))
                .containsExactly("La frase del cierre usa \"la mejor\", que no está permitido.");
        assertThat(ValidadorDeGuion.validar(new GuionPropuesto(valido().planos(), null, DESCRIPCION, HASHTAGS),
                datos(), PAQUETE)).containsExactly("Falta el cierre.");
    }

    @Test
    void laUbicacionDelCierreSoloUsaLugaresQueMencionoElNegocio() {
        DatosDelGuion conLugar = new DatosDelGuion("PRODUCTO_ESTRELLA", "Producto estrella", "Torta Tres Leches",
                "Bizcocho de tres leches con fresas frescas.", "Fresas", null,
                "estamos en el patio de comidas de plaza lima sur", "Dulce Encanto", "CASERO", "Casero", null, null,
                "Lima", "Chorrillos", null, null, null, List.of("TIENDA"), List.of());
        List<String> hashtags = List.of("#pasteleria", "#tortas", "#tresleches", "#postres", "#chorrillos");
        GuionPropuesto conPlaza = new GuionPropuesto(valido().planos(), new GuionPropuesto.CierrePropuesto(null,
                "Tu antojo te espera", "Plaza Lima Sur, Chorrillos"), DESCRIPCION, hashtags);
        GuionPropuesto inventada = new GuionPropuesto(valido().planos(), new GuionPropuesto.CierrePropuesto(null,
                "Tu antojo te espera", "Jockey Plaza"), DESCRIPCION, hashtags);

        assertThat(ValidadorDeGuion.validar(conPlaza, conLugar, PAQUETE)).isEmpty();
        assertThat(ValidadorDeGuion.validar(inventada, conLugar, PAQUETE)).containsExactly(
                "La ubicación del cierre usa \"jockey\", que el negocio no mencionó; usa solo lugares que escribió "
                        + "el negocio o null.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "S/ 65|2",
            "¡Antojo!|1",
            "2x1|1",
            "—|0",
            "Torta  de   fresa|3",
            "' Recién horneado '|2"
    })
    void contarPalabrasIgualQueEnElNavegador(String texto, int palabras) {
        assertThat(Texto.contarPalabras(texto)).isEqualTo(palabras);
    }

    private static GuionPropuesto guionConHashtags(List<String> hashtags) {
        return guion("Un antojo de fresas", "Torta Tres Leches", "Cada porción con fresas", DESCRIPCION, hashtags);
    }

    private static GuionPropuesto guionConDescripcion(String descripcion) {
        return guion("Un antojo de fresas", "Torta Tres Leches", "Cada porción con fresas", descripcion, HASHTAGS);
    }

    private static GuionPropuesto conPrecioEnElCierre(GuionPropuesto guion, String precio) {
        return new GuionPropuesto(guion.planos(), new GuionPropuesto.CierrePropuesto(precio, "Tu antojo te espera",
                null), guion.descripcion(), guion.hashtags());
    }
}
