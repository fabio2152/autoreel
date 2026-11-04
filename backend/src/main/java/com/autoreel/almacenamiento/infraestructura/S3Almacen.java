package com.autoreel.almacenamiento.infraestructura;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import com.autoreel.almacenamiento.dominio.AlmacenDeArchivos;
import com.autoreel.almacenamiento.dominio.ArchivoAlmacenado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Adaptador de {@link AlmacenDeArchivos} para Amazon S3 o un servicio compatible (SeaweedFS en desarrollo).
 * El bucket es privado: los archivos se leen y se suben con URLs firmadas que vencen.
 */
public class S3Almacen implements AlmacenDeArchivos, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3Almacen.class);

    private final S3Client cliente;
    private final S3Presigner firmador;
    private final String bucket;

    public S3Almacen(PropiedadesAlmacenamiento propiedades) {
        Region region = Region.of(propiedades.region());
        AwsCredentialsProvider credenciales = propiedades.tieneEndpoint()
                && propiedades.claveAcceso() != null && !propiedades.claveAcceso().isBlank()
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(propiedades.claveAcceso(), propiedades.claveSecreta()))
                // En AWS: rol de la instancia, variables del entorno del proceso, etc.
                : DefaultCredentialsProvider.builder().build();
        var constructorCliente = S3Client.builder().region(region).credentialsProvider(credenciales);
        var constructorFirmador = S3Presigner.builder().region(region).credentialsProvider(credenciales);
        if (propiedades.tieneEndpoint()) {
            constructorCliente.endpointOverride(URI.create(propiedades.endpoint())).forcePathStyle(true);
            constructorFirmador.endpointOverride(URI.create(propiedades.endpointParaNavegador()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        this.cliente = constructorCliente.build();
        this.firmador = constructorFirmador.build();
        this.bucket = propiedades.bucket();
        if (propiedades.crearBucket()) {
            crearBucketSiFalta();
        }
    }

    @Override
    public String urlDeSubida(String clave, String tipoContenido, long tamano, Duration vigencia) {
        return firmador.presignPutObject(firma -> firma.signatureDuration(vigencia)
                        .putObjectRequest(objeto -> objeto.bucket(bucket).key(clave)
                                .contentType(tipoContenido).contentLength(tamano)))
                .url().toString();
    }

    @Override
    public String urlDeDescarga(String clave, Duration vigencia) {
        return firmador.presignGetObject(firma -> firma.signatureDuration(vigencia)
                        .getObjectRequest(objeto -> objeto.bucket(bucket).key(clave)))
                .url().toString();
    }

    @Override
    public Optional<ArchivoAlmacenado> leer(String clave, long bytesMaximos) {
        try {
            HeadObjectResponse cabecera = cliente.headObject(objeto -> objeto.bucket(bucket).key(clave));
            if (cabecera.contentLength() > bytesMaximos) {
                return Optional.of(new ArchivoAlmacenado(null, cabecera.contentType(), cabecera.contentLength(),
                        cabecera.eTag()));
            }
            ResponseBytes<GetObjectResponse> archivo = cliente.getObjectAsBytes(objeto -> objeto.bucket(bucket)
                    .key(clave).ifMatch(cabecera.eTag()));
            return Optional.of(new ArchivoAlmacenado(archivo.asByteArray(), archivo.response().contentType(),
                    archivo.response().contentLength(), archivo.response().eTag()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 412) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public boolean copiarSiNoCambio(String origen, String destino, String etiqueta) {
        try {
            cliente.copyObject(copia -> copia.sourceBucket(bucket).sourceKey(origen)
                    .destinationBucket(bucket).destinationKey(destino).copySourceIfMatch(etiqueta));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 412 || e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void guardar(String clave, byte[] contenido, String tipoContenido) {
        cliente.putObject(objeto -> objeto.bucket(bucket).key(clave).contentType(tipoContenido),
                RequestBody.fromBytes(contenido));
    }

    @Override
    public void eliminar(String clave) {
        cliente.deleteObject(objeto -> objeto.bucket(bucket).key(clave));
    }

    private void crearBucketSiFalta() {
        try {
            cliente.headBucket(consulta -> consulta.bucket(bucket));
        } catch (NoSuchBucketException e) {
            cliente.createBucket(creacion -> creacion.bucket(bucket));
            log.info("Bucket {} creado", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            cliente.createBucket(creacion -> creacion.bucket(bucket));
            log.info("Bucket {} creado", bucket);
        }
    }

    @Override
    public void close() {
        firmador.close();
        cliente.close();
    }
}
