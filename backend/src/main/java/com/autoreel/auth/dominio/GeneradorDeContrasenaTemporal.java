package com.autoreel.auth.dominio;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * RN-02: 12 caracteres aleatorios. Se omiten los caracteres que se confunden al dictarlos (0/O, 1/l/I).
 */
@Component
public class GeneradorDeContrasenaTemporal {

    public static final int LONGITUD = 12;
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String ALFABETO = LETRAS + NUMEROS;

    private final SecureRandom aleatorio = new SecureRandom();

    public String generar() {
        String contrasena;
        do {
            StringBuilder texto = new StringBuilder(LONGITUD);
            for (int i = 0; i < LONGITUD; i++) {
                texto.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
            }
            contrasena = texto.toString();
        } while (contrasena.chars().noneMatch(Character::isDigit) || contrasena.chars().noneMatch(Character::isLetter));
        return contrasena;
    }
}
