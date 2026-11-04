package com.autoreel.compartido.error;

import org.springframework.http.HttpStatus;

/**
 * Error esperado de una regla de negocio. Se traduce a {@link ErrorApi} con el estado HTTP indicado.
 */
public class ExcepcionDeNegocio extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;

    public ExcepcionDeNegocio(HttpStatus estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    public ExcepcionDeNegocio(String codigo, String mensaje) {
        this(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }

    public static ExcepcionDeNegocio noEncontrado(String mensaje) {
        return new ExcepcionDeNegocio(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje);
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getCodigo() {
        return codigo;
    }
}
