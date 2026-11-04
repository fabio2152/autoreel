package com.autoreel.compartido.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj único de la aplicación, en hora de Lima: el "día" de las reglas de negocio es el calendario de Lima (RN-26).
 * Se inyecta en lugar de usar {@code now()} para poder probar vencimientos con un reloj fijo.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionReloj {

    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @Bean
    Clock reloj() {
        return Clock.system(ZONA_LIMA);
    }
}
