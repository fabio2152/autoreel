package com.autoreel.almacenamiento.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.almacenamiento.dominio.ArchivoAlmacenado;
import com.autoreel.almacenamiento.dominio.PropositoArchivo;
import com.autoreel.compartido.error.ExcepcionDeNegocio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Subida directa de archivos desde el navegador (sección 11.4), en dos tiempos:
 * <ol>
 *   <li>El backend firma una URL para una clave temporal {@code subidas/negocios/{id}/{carpeta}/{uuid}.{ext}}.</li>
 *   <li>Cuando el perfil o un producto usan esa clave, el backend verifica el archivo y lo copia a su clave
 *       definitiva {@code negocios/{id}/{carpeta}/{uuid}.{ext}}, para la que nunca firma una URL de escritura.
 *       Así nadie puede reemplazar un archivo después de verificado.</li>
 * </ol>
 * Las claves se arman y se comprueban siempre con el negocio de la sesión (RNF-05).
 */
@Service
public class ServicioDeArchivos {

    private static final Logger log = LoggerFactory.getLogger(ServicioDeArchivos.class);

    static final Duration VIGENCIA_SUBIDA = Duration.ofMinutes(10);
    static final Duration VIGENCIA_LECTURA = Duration.ofHours(1);
    /** Una URL de lectura se reutiliza mientras le queden al menos estos minutos. */
    private static final Duration MARGEN_REUTILIZACION = Duration.ofMinutes(15);
    private static final int MAXIMO_URLS_EN_MEMORIA = 5_000;
    private static final String PREFIJO_SUBIDAS = "subidas/";
    private static final String PATRON_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** Lo que el navegador necesita para subir: la clave temporal, la URL firmada y las cabeceras que debe enviar. */
    public record SubidaFirmada(String clave, String url, String metodo, Map<String, String> cabeceras) {
    }

    /** Imagen ya verificada y guardada en su clave definitiva. */
    public record ImagenGuardada(String clave, ImagenVerificada imagen) {
    }

    private record UrlFirmada(String url, Instant vence) {
    }

    private final AlmacenDeArchivos almacen;
    private final VerificadorDeImagenes verificador;
    private final Clock reloj;
    private final Map<String, UrlFirmada> urlsDeLectura = new ConcurrentHashMap<>();

    public ServicioDeArchivos(AlmacenDeArchivos almacen, VerificadorDeImagenes verificador, Clock reloj) {
        this.almacen = almacen;
        this.verificador = verificador;
        this.reloj = reloj;
    }

    public SubidaFirmada firmarSubida(Long negocioId, PropositoArchivo proposito, String tipoContenido, long tamano) {
        String tipo = tipoContenido == null ? "" : tipoContenido.trim().toLowerCase(Locale.ROOT);
        if (!proposito.admite(tipo)) {
            throw proposito == PropositoArchivo.LOGO
                    ? new ExcepcionDeNegocio("LOGO_DEBE_SER_PNG", "El logo debe ser una imagen PNG.")
                    : new ExcepcionDeNegocio("TIPO_NO_PERMITIDO",
                            "Ese tipo de archivo no se admite. Sube una foto JPG, PNG o WebP.");
        }
        if (tamano <= 0) {
            throw new ExcepcionDeNegocio("ARCHIVO_VACIO", "El archivo está vacío.");
        }
        if (tamano > proposito.bytesMaximos()) {
            throw demasiadoGrande(proposito);
        }
        String clave = PREFIJO_SUBIDAS + carpeta(negocioId, proposito) + UUID.randomUUID()
                + "." + PropositoArchivo.extension(tipo);
        return new SubidaFirmada(clave, almacen.urlDeSubida(clave, tipo, tamano, VIGENCIA_SUBIDA), "PUT",
                Map.of("Content-Type", tipo));
    }

    /**
     * Verifica una imagen recién subida a su clave temporal y la deja en su clave definitiva.
     *
     * @throws ExcepcionDeNegocio si la clave no es de este negocio y propósito, si el archivo no se subió o no cumple
     *                            las reglas del propósito.
     */
    public ImagenGuardada confirmar(Long negocioId, PropositoArchivo proposito, String claveSubida) {
        Matcher coincidencia = patron(negocioId, proposito, true).matcher(claveSubida == null ? "" : claveSubida);
        if (!coincidencia.matches()) {
            throw claveInvalida();
        }
        ArchivoAlmacenado archivo = almacen.leer(claveSubida, proposito.bytesMaximos())
                .orElseThrow(() -> new ExcepcionDeNegocio("ARCHIVO_NO_SUBIDO",
                        "No encontramos el archivo subido. Vuelve a subirlo."));
        if (archivo.contenido() == null || archivo.tamano() > proposito.bytesMaximos()) {
            throw demasiadoGrande(proposito);
        }
        String tipoDeclarado = PropositoArchivo.tipoPorExtension(coincidencia.group(1));
        ImagenVerificada imagen = verificador.verificar(archivo.contenido(), proposito, tipoDeclarado);
        String definitiva = claveSubida.substring(PREFIJO_SUBIDAS.length());
        if (!almacen.copiarSiNoCambio(claveSubida, definitiva, archivo.etiqueta())) {
            throw new ExcepcionDeNegocio(HttpStatus.CONFLICT, "ARCHIVO_MODIFICADO",
                    "El archivo cambió mientras lo revisábamos. Vuelve a subirlo.");
        }
        eliminarAlConfirmar(claveSubida);
        return new ImagenGuardada(definitiva, imagen);
    }

    /**
     * La subida temporal se borra solo si la operación se confirma: si falla más adelante, el usuario puede
     * reintentar con la misma clave. Las que queden se limpian con la regla de expiración del bucket.
     */
    private void eliminarAlConfirmar(String claveSubida) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            almacen.eliminar(claveSubida);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    almacen.eliminar(claveSubida);
                } catch (RuntimeException e) {
                    log.warn("No se pudo borrar la subida temporal {}", claveSubida, e);
                }
            }
        });
    }

    /** Si la clave es una ya guardada de este negocio y propósito (no una subida nueva). */
    public boolean esDefinitivaDe(Long negocioId, PropositoArchivo proposito, String clave) {
        return clave != null && patron(negocioId, proposito, false).matcher(clave).matches();
    }

    public boolean esSubida(String clave) {
        return clave != null && clave.startsWith(PREFIJO_SUBIDAS);
    }

    /** URL temporal de lectura; se reutiliza mientras no esté por vencer, para que el navegador la tenga en caché. */
    public String urlDeLectura(String clave) {
        if (clave == null) {
            return null;
        }
        Instant ahora = reloj.instant();
        UrlFirmada guardada = urlsDeLectura.get(clave);
        if (guardada != null && guardada.vence().isAfter(ahora.plus(MARGEN_REUTILIZACION))) {
            return guardada.url();
        }
        if (urlsDeLectura.size() >= MAXIMO_URLS_EN_MEMORIA) {
            urlsDeLectura.values().removeIf(url -> !url.vence().isAfter(ahora.plus(MARGEN_REUTILIZACION)));
        }
        UrlFirmada nueva = new UrlFirmada(almacen.urlDeDescarga(clave, VIGENCIA_LECTURA), ahora.plus(VIGENCIA_LECTURA));
        if (urlsDeLectura.size() < MAXIMO_URLS_EN_MEMORIA) {
            urlsDeLectura.put(clave, nueva);
        }
        return nueva.url();
    }

    private static String carpeta(Long negocioId, PropositoArchivo proposito) {
        return "negocios/" + negocioId + "/" + proposito.carpeta() + "/";
    }

    private static Pattern patron(Long negocioId, PropositoArchivo proposito, boolean subida) {
        return Pattern.compile("^" + (subida ? Pattern.quote(PREFIJO_SUBIDAS) : "")
                + Pattern.quote(carpeta(negocioId, proposito)) + PATRON_UUID + "\\.(png|jpg|webp)$");
    }

    private static ExcepcionDeNegocio claveInvalida() {
        return new ExcepcionDeNegocio("ARCHIVO_INVALIDO", "El archivo no corresponde a tu negocio. Vuelve a subirlo.");
    }

    private static ExcepcionDeNegocio demasiadoGrande(PropositoArchivo proposito) {
        return new ExcepcionDeNegocio("ARCHIVO_DEMASIADO_GRANDE",
                "El archivo pesa más de " + proposito.megabytesMaximos() + " MB.");
    }
}
