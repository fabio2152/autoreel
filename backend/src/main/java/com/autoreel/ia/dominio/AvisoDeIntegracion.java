package com.autoreel.ia.dominio;

/**
 * RN-57: una falla del proveedor (credencial inválida, servicio caído, modelo mal configurado) se avisa al
 * administrador. En la fase 4 queda en el registro de la aplicación; la notificación del panel llega en la fase 7.
 */
public interface AvisoDeIntegracion {

    void avisar(ModeloIA modelo, ErrorDeProveedor.Tipo tipo, String detalle);
}
