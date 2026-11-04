package com.autoreel.ia.dominio;

/**
 * Puerto del modelo de texto que escribe el guion (sección 8.1). Cada proveedor tiene su adaptador; el resto del
 * proceso no sabe cuál se usa (RNF-07).
 */
public interface Guionista {

    /** Identificador del adaptador, igual al campo {@code adaptador} de {@link ModeloIA} (por ejemplo, GEMINI). */
    String adaptador();

    /**
     * Pide el guion en JSON.
     *
     * @return la respuesta del modelo. Una respuesta sin texto o bloqueada vuelve con {@code texto} vacío y el
     *         {@code problema}, y cuenta como un intento (se cobra).
     * @throws ErrorDeProveedor si el proveedor no está disponible, la credencial no sirve o la configuración del
     *                          modelo es inválida. En esos casos no tiene sentido reintentar.
     */
    RespuestaDelModelo escribir(ModeloIA modelo, String credencial, String instrucciones, String esquemaJson);

    /** @param problema motivo por el que no hay texto utilizable; vacío si la respuesta es normal. */
    record RespuestaDelModelo(String texto, String problema) {

        public static RespuestaDelModelo conTexto(String texto) {
            return new RespuestaDelModelo(texto, null);
        }

        public static RespuestaDelModelo sinTexto(String problema) {
            return new RespuestaDelModelo(null, problema);
        }
    }
}
