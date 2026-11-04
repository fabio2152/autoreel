package com.autoreel.auth.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GeneradorDeContrasenaTemporalTest {

    private final GeneradorDeContrasenaTemporal generador = new GeneradorDeContrasenaTemporal();

    @Test
    void generaDoceCaracteresSinAmbiguosConLetraYNumero() {
        for (int i = 0; i < 500; i++) {
            String contrasena = generador.generar();
            assertThat(contrasena).hasSize(12).doesNotContainPattern("[0O1lI]");
            assertThat(contrasena.chars().anyMatch(Character::isDigit)).isTrue();
            assertThat(contrasena.chars().anyMatch(Character::isLetter)).isTrue();
        }
    }

    @Test
    void cadaContrasenaEsDistinta() {
        Set<String> generadas = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generadas.add(generador.generar());
        }
        assertThat(generadas).hasSize(1000);
    }
}
