package com.autoreel.video.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoreel.nicho.pasteleria.PaquetePasteleria;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Los textos del video se reparten en líneas que caben en la zona segura (drawtext no ajusta ni recorta). */
class DisenoDeTextosTest {

    private static final int ANCHO = 770;
    private final DisenoDeTextos diseno = new DisenoDeTextos(
            new PaquetePasteleria(JsonMapper.builder().build()).tipografia("CASERO"));

    @Test
    void unTextoCortoVaEnUnaLineaConLaLetraMasGrande() {
        DisenoDeTextos.Bloque bloque = diseno.repartir("Torta Tres Leches", ANCHO, 3, 76, 68, 60, 52);

        assertThat(bloque.lineas()).containsExactly("Torta Tres Leches");
        assertThat(bloque.tamano()).isEqualTo(76);
    }

    @Test
    void unTextoLargoSeParteEnVariasLineas() {
        DisenoDeTextos.Bloque bloque = diseno.repartir("Cada porción con fresas de Huaral y crema batida",
                ANCHO, 3, 76, 68, 60, 52);

        assertThat(bloque.lineas()).hasSizeBetween(2, 3);
        assertThat(String.join(" ", bloque.lineas())).isEqualTo("Cada porción con fresas de Huaral y crema batida");
    }

    @Test
    void palabrasLarguisimasAchicanLaLetraHastaCaber() {
        DisenoDeTextos.Bloque bloque = diseno.repartir("Extraordinariamente deliciosas celebraciones "
                + "personalizadísimas inolvidables chocolatosas maravillosas", ANCHO, 3, 76, 68, 60, 52);

        assertThat(bloque.lineas()).hasSize(3);
        assertThat(bloque.tamano()).isLessThan(52);
        DisenoDeTextos.Bloque mismaLetra = diseno.repartir(bloque.lineas().get(2), Integer.MAX_VALUE, 1,
                bloque.tamano());
        assertThat(mismaLetra.lineas()).hasSize(1);
    }
}
