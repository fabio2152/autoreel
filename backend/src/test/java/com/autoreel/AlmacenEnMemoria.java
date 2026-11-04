package com.autoreel;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.almacenamiento.dominio.ArchivoAlmacenado;

/**
 * Almacén de pruebas: guarda los archivos en memoria. Las pruebas "suben" un archivo con {@link #guardar}, igual
 * que lo haría el navegador con la URL firmada.
 */
public class AlmacenEnMemoria implements AlmacenDeArchivos {

    private record Archivo(byte[] contenido, String tipo, String etiqueta) {
    }

    private final Map<String, Archivo> archivos = new ConcurrentHashMap<>();

    @Override
    public String urlDeSubida(String clave, String tipoContenido, long tamano, Duration vigencia) {
        return "memoria://subir/" + clave;
    }

    @Override
    public String urlDeDescarga(String clave, Duration vigencia) {
        return "memoria://leer/" + clave;
    }

    @Override
    public Optional<ArchivoAlmacenado> leer(String clave, long bytesMaximos) {
        return Optional.ofNullable(archivos.get(clave)).map(archivo -> new ArchivoAlmacenado(
                archivo.contenido().length > bytesMaximos ? null : archivo.contenido(), archivo.tipo(),
                archivo.contenido().length, archivo.etiqueta()));
    }

    @Override
    public boolean copiarSiNoCambio(String origen, String destino, String etiqueta) {
        Archivo archivo = archivos.get(origen);
        if (archivo == null || !archivo.etiqueta().equals(etiqueta)) {
            return false;
        }
        archivos.put(destino, archivo);
        return true;
    }

    @Override
    public void guardar(String clave, byte[] contenido, String tipoContenido) {
        archivos.put(clave, new Archivo(contenido, tipoContenido, java.util.UUID.randomUUID().toString()));
    }

    @Override
    public void eliminar(String clave) {
        archivos.remove(clave);
    }

    public boolean existe(String clave) {
        return archivos.containsKey(clave);
    }
}
