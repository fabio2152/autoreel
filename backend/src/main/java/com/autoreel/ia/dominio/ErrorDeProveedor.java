package com.autoreel.ia.dominio;

/**
 * Falla de un proveedor de IA que no se resuelve reintentando: el proceso usa su respaldo y se avisa al
 * administrador (RN-57). El mensaje nunca incluye la credencial.
 */
public class ErrorDeProveedor extends RuntimeException {

    public enum Tipo {
        /** 401 o 403: la credencial no existe o no sirve. */
        CREDENCIAL_INVALIDA,
        /** 400 o 404: el identificador del modelo o la petición están mal configurados. */
        CONFIGURACION_INVALIDA,
        /** 429, 5xx, red o tiempo de espera. */
        NO_DISPONIBLE
    }

    private final Tipo tipo;

    public ErrorDeProveedor(Tipo tipo, String mensaje) {
        super(mensaje);
        this.tipo = tipo;
    }

    public ErrorDeProveedor(Tipo tipo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }
}
