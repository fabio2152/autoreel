package com.autoreel.ia.dominio;

/**
 * Credenciales que el administrador pega en Ajustes → Integraciones (decisión del 2026-11-06, ADR 0018). Se guardan
 * fuera de la base de datos y del código: en desarrollo, un archivo ignorado por git; en AWS, Secrets Manager (fase 8).
 * El panel nunca vuelve a mostrar el valor.
 */
public interface AlmacenDeSecretos {

    /** De dónde sale el valor que se usa hoy. El del panel tiene prioridad sobre el de la configuración. */
    enum Origen {
        PANEL,
        CONFIGURACION,
        NINGUNO
    }

    /** Guarda o reemplaza el valor del secreto. */
    void guardar(String nombreSecreto, String valor);

    /** Borra el valor guardado desde el panel; vuelve a regir el de la configuración, si existe. */
    void borrar(String nombreSecreto);

    Origen origen(String nombreSecreto);
}
