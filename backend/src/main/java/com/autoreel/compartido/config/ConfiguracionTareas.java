package com.autoreel.compartido.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas (sección 12.2). Las pruebas las apagan con {@code autoreel.tareas.activas=false} y
 * llaman a cada procesador directamente.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "autoreel.tareas.activas", matchIfMissing = true)
public class ConfiguracionTareas {
}
