package com.autoreel.almacenamiento.infraestructura;

import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionAlmacenamiento {

    /** S3 es el almacén por defecto; las pruebas usan uno en memoria ({@code autoreel.almacenamiento.tipo=memoria}). */
    @Bean
    @ConditionalOnProperty(name = "autoreel.almacenamiento.tipo", havingValue = "s3", matchIfMissing = true)
    AlmacenDeArchivos almacenS3(PropiedadesAlmacenamiento propiedades) {
        return new S3Almacen(propiedades);
    }
}
