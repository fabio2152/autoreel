package com.autoreel.ia.dominio;

/**
 * RN-57: una falla del proveedor (credencial inválida, servicio caído, modelo mal configurado) se avisa al
 * administrador con una notificación del panel (fase 7).
 */
public interface AvisoDeIntegracion {

    void avisar(ModeloIA modelo, ErrorDeProveedor.Tipo tipo, String detalle);
}
