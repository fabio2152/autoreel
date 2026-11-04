package com.autoreel.video.dominio;

import static com.autoreel.video.dominio.Guiones.PAQUETE;
import static com.autoreel.video.dominio.Guiones.datos;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RN-25 y V20: el guion de respaldo siempre pasa el validador. */
class GuionDeRespaldoTest {

    @Test
    void usaLosTextosDelUsuarioRecortados() {
        GuionPropuesto guion = GuionDeRespaldo.armar(datos(), PAQUETE);

        assertThat(ValidadorDeGuion.validar(guion, datos(), PAQUETE)).isEmpty();
        assertThat(guion.plano(1).texto()).isEqualTo("Nuestro favorito de siempre");
        assertThat(guion.plano(2).texto()).isEqualTo("Torta Tres Leches");
        assertThat(guion.plano(3).texto()).isEqualTo("Fresas frescas de Huaral");
        assertThat(guion.plano(1).movimiento()).isEqualTo("ACERCAMIENTO_LENTO");
        assertThat(guion.descripcion())
                .isEqualTo("Hoy te presentamos Torta Tres Leches. Fresas frescas de Huaral. Pide por WhatsApp.");
        assertThat(guion.hashtags()).containsExactly("#pasteleria", "#postres", "#reposteria", "#dulces", "#antojo",
                "#miraflores");
    }

    @ParameterizedTest
    @CsvSource({
            "PRODUCTO_ESTRELLA, Producto estrella,",
            "PROMOCION, Promoción, S/ 65 la torta entera",
            "TORTAS_POR_ENCARGO, Tortas por encargo,",
            "RECIEN_HORNEADO, Recién salido del horno,"
    })
    void esValidoEnCadaTipoDeVideo(String tipo, String texto, String precio) {
        DatosDelGuion datos = Guiones.datos(tipo, texto, "Torta Tres Leches", "Fresas frescas de Huaral", precio,
                List.of());

        assertThat(ValidadorDeGuion.validar(GuionDeRespaldo.armar(datos, PAQUETE), datos, PAQUETE)).isEmpty();
    }

    @Test
    void siLoEspecialTieneUnSuperlativoUsaLaDescripcionDelProducto() {
        DatosDelGuion datos = Guiones.datos("PRODUCTO_ESTRELLA", "Producto estrella", "Torta Tres Leches",
                "El mejor de Lima", null, List.of());

        GuionPropuesto guion = GuionDeRespaldo.armar(datos, PAQUETE);

        assertThat(ValidadorDeGuion.validar(guion, datos, PAQUETE)).isEmpty();
        assertThat(guion.plano(3).texto()).isEqualTo("Bizcocho de tres leches con fresas frescas");
        assertThat(guion.descripcion()).isEqualTo("Hoy te presentamos Torta Tres Leches. Pide por WhatsApp.");
    }

    @Test
    void unaAfirmacionDeSaludQueElProductoNoDiceNoSeUsa() {
        DatosDelGuion datos = Guiones.datos("PRODUCTO_ESTRELLA", "Producto estrella", "Torta sin gluten",
                "Sin gluten y sin azúcar", null, List.of());

        GuionPropuesto guion = GuionDeRespaldo.armar(datos, PAQUETE);

        assertThat(ValidadorDeGuion.validar(guion, datos, PAQUETE)).isEmpty();
        assertThat(guion.plano(2).texto()).isEqualTo("Hecho para compartir");
        assertThat(guion.descripcion()).doesNotContainIgnoringCase("gluten");
    }

    @Test
    void unPrecioEnLoEspecialNoVaEnPantalla() {
        DatosDelGuion datos = Guiones.datos("PROMOCION", "Promoción", "Torta Tres Leches", "Hoy a solo 45 soles",
                "45 soles", List.of());

        GuionPropuesto guion = GuionDeRespaldo.armar(datos, PAQUETE);

        assertThat(ValidadorDeGuion.validar(guion, datos, PAQUETE)).isEmpty();
        assertThat(guion.plano(3).texto()).isEqualTo("Bizcocho de tres leches con fresas frescas");
    }

    @Test
    void lasPalabrasQueElNegocioProhibioNoTumbanElRestoDelGuion() {
        DatosDelGuion datos = Guiones.datos("PRODUCTO_ESTRELLA", "Producto estrella", "Torta Tres Leches",
                "Fresas frescas de Huaral", null, List.of("antojo", "cariño"));

        GuionPropuesto guion = GuionDeRespaldo.armar(datos, PAQUETE);

        assertThat(ValidadorDeGuion.validar(guion, datos, PAQUETE)).isEmpty();
        assertThat(guion.hashtags()).doesNotContain("#antojo").contains("#miraflores");
        assertThat(guion.plano(2).texto()).isEqualTo("Torta Tres Leches");
    }

    @Test
    void elGuionFijoUsaElGanchoDelTipoDeVideo() {
        DatosDelGuion datos = Guiones.datos("RECIEN_HORNEADO", "Recién salido del horno", "Torta Tres Leches",
                "Fresas", null, List.of());

        assertThat(GuionDeRespaldo.fijo(datos, PAQUETE).plano(1).texto()).isEqualTo("Recién salido del horno");
    }

    @Test
    void siNingunTextoSirveDevuelveElFijoSinFallar() {
        DatosDelGuion datos = Guiones.datos("PRODUCTO_ESTRELLA", "Producto estrella", "Torta Tres Leches",
                "Fresas", null, List.of("compartir", "antojo", "cariño", "favorito", "torta", "fresas", "bizcocho"));

        GuionPropuesto guion = GuionDeRespaldo.armar(datos, PAQUETE);

        assertThat(guion.planos()).hasSize(3);
        assertThat(guion.plano(2).texto()).isEqualTo("Hecho para compartir");
        assertThat(guion.descripcion()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Fresas frescas, crema y amor|6|Fresas frescas",
            "una torta hecha con mucho amor de la abuela|6|Una torta hecha con mucho amor",
            "Torta de chocolate con|5|Torta de chocolate",
            "¡Antojo total!|6|Antojo total",
            "Bizcocho húmedo de vainilla y manjar casero|5|Bizcocho húmedo de vainilla"
    })
    void recortaEnLaPrimeraFraseSinConectoresAlFinal(String texto, int maximo, String esperado) {
        assertThat(GuionDeRespaldo.recortar(texto, maximo)).isEqualTo(esperado);
    }
}
