package com.autoreel.compartido.web;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

/**
 * En AWS el backend sirve el frontend compilado en el mismo origen (fase 8), así la cookie de sesión y el CSRF
 * funcionan igual que con el proxy de Vite. La carpeta se indica con {@code SPRING_WEB_RESOURCES_STATIC_LOCATIONS}
 * (en la imagen, {@code file:/app/static/}).
 * <ul>
 * <li>{@code /assets/**}: archivos con hash en el nombre, en caché un año.</li>
 * <li>El resto: el archivo si existe; si no, {@code index.html} para las rutas de React Router ({@code /videos/5},
 * {@code /admin/costos}...). Sin caché, para tomar cada despliegue.</li>
 * <li>{@code /api/**}, {@code /actuator/**} y lo que parece un archivo (tiene extensión) nunca reciben
 * {@code index.html}: siguen respondiendo 404 con el error JSON.</li>
 * </ul>
 * En desarrollo no hay {@code index.html} en las ubicaciones: no cambia nada.
 * <p>
 * Va antes que la configuración de Spring Boot, que así no registra su propio {@code /**}.
 */
@Configuration(proxyBeanMethods = false)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConfiguracionFrontend implements WebMvcConfigurer {

    private static final String INDICE = "index.html";

    private final String[] ubicaciones;

    public ConfiguracionFrontend(WebProperties web) {
        this.ubicaciones = web.getResources().getStaticLocations();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/assets/**")
                .addResourceLocations(Arrays.stream(ubicaciones).map(ubicacion -> ubicacion + "assets/")
                        .toArray(String[]::new))
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        registro.addResourceHandler("/**")
                .addResourceLocations(ubicaciones)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new RutasDeLaAplicacion());
    }

    /** Si el archivo no existe y la ruta es de la aplicación de React, entrega {@code index.html}. */
    static final class RutasDeLaAplicacion extends PathResourceResolver {

        @Override
        protected Resource resolveResourceInternal(HttpServletRequest peticion, String ruta,
                List<? extends Resource> ubicaciones, ResourceResolverChain cadena) {
            Resource recurso = super.resolveResourceInternal(peticion, ruta, ubicaciones, cadena);
            if (recurso != null || !esRutaDeLaAplicacion(ruta)) {
                return recurso;
            }
            return super.resolveResourceInternal(peticion, INDICE, ubicaciones, cadena);
        }

        static boolean esRutaDeLaAplicacion(String ruta) {
            String ultimoTramo = ruta.substring(ruta.lastIndexOf('/') + 1);
            return !ruta.equals("api") && !ruta.startsWith("api/")
                    && !ruta.equals("actuator") && !ruta.startsWith("actuator/")
                    && !ultimoTramo.contains(".");
        }
    }
}
