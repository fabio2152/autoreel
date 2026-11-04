package com.autoreel.auth.dominio;

import java.nio.charset.StandardCharsets;

import com.autoreel.compartido.error.ExcepcionDeNegocio;

/**
 * RN-04: la contraseña propia tiene al menos 8 caracteres, con al menos una letra y un número, y se confirma
 * escribiéndola dos veces. El máximo de 64 evita el límite de 72 bytes de BCrypt.
 */
public final class ReglasDeContrasena {

    public static final int LONGITUD_MINIMA = 8;
    public static final int LONGITUD_MAXIMA = 64;
    private static final int BYTES_MAXIMOS_BCRYPT = 72;

    private ReglasDeContrasena() {
    }

    public static void validar(String nueva, String confirmacion) {
        if (nueva == null || nueva.length() < LONGITUD_MINIMA) {
            throw new ExcepcionDeNegocio("CONTRASENA_MUY_CORTA",
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        // Con letras como "ñ" o tildes, 64 caracteres pueden pasar de los 72 bytes que admite BCrypt.
        if (nueva.length() > LONGITUD_MAXIMA || nueva.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_BCRYPT) {
            throw new ExcepcionDeNegocio("CONTRASENA_MUY_LARGA",
                    "La contraseña puede tener como máximo " + LONGITUD_MAXIMA + " caracteres.");
        }
        if (nueva.chars().noneMatch(Character::isLetter)) {
            throw new ExcepcionDeNegocio("CONTRASENA_SIN_LETRA", "La contraseña debe tener al menos una letra.");
        }
        if (nueva.chars().noneMatch(Character::isDigit)) {
            throw new ExcepcionDeNegocio("CONTRASENA_SIN_NUMERO", "La contraseña debe tener al menos un número.");
        }
        if (!nueva.equals(confirmacion)) {
            throw new ExcepcionDeNegocio("CONTRASENAS_NO_COINCIDEN", "Las contraseñas no coinciden.");
        }
    }
}
