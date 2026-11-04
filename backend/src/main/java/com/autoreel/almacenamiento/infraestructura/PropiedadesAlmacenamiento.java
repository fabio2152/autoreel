package com.autoreel.almacenamiento.infraestructura;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param tipo           {@code s3} (por defecto) o {@code memoria} (solo pruebas).
 * @param endpoint       servicio compatible con S3 que usa el backend (SeaweedFS en desarrollo); vacío en AWS.
 * @param endpointPublico el que ve el navegador en las URLs firmadas; por defecto, el mismo {@code endpoint}.
 * @param claveAcceso    credenciales del servicio local; en AWS se dejan vacías y se usa el rol de la instancia.
 * @param crearBucket    crea el bucket al arrancar si no existe (solo desarrollo).
 */
@ConfigurationProperties("autoreel.almacenamiento")
public record PropiedadesAlmacenamiento(String tipo, String bucket, String region, String endpoint,
        String endpointPublico, String claveAcceso, String claveSecreta, boolean crearBucket) {

    public boolean tieneEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }

    public String endpointParaNavegador() {
        return endpointPublico != null && !endpointPublico.isBlank() ? endpointPublico : endpoint;
    }
}
