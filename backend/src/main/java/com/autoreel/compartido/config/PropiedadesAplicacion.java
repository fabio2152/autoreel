package com.autoreel.compartido.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param urlAplicacion dirección pública del frontend, usada en el mensaje que el administrador entrega al cliente.
 */
@Validated
@ConfigurationProperties("autoreel")
public record PropiedadesAplicacion(@NotBlank String urlAplicacion) {

    public String enlaceInicioSesion() {
        String base = urlAplicacion.endsWith("/") ? urlAplicacion.substring(0, urlAplicacion.length() - 1) : urlAplicacion;
        return base + "/login";
    }
}
