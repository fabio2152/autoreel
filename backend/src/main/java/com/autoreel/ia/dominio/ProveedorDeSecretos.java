package com.autoreel.ia.dominio;

import java.util.Optional;

/**
 * Credenciales de los proveedores (RN-55): el modelo de IA solo guarda el nombre del secreto. En desarrollo vienen de
 * variables de entorno; en producción, de AWS Secrets Manager (fase 8).
 */
public interface ProveedorDeSecretos {

    /** El valor del secreto, o vacío si no está configurado. */
    Optional<String> obtener(String nombreSecreto);
}
