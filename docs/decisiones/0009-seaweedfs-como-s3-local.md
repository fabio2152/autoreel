# ADR 0009 — SeaweedFS como S3 local

- **Estado:** Aceptada
- **Fecha:** 2026-11-04

## Contexto

En la fase 2 el navegador sube los logos y las fotos directamente al almacenamiento con URLs firmadas (sección 11.4), y el backend los lee para verificarlos y los copia a su clave definitiva (ADR 0010). En producción el almacenamiento es **Amazon S3** (sección 12). En desarrollo y en las pruebas automatizadas hace falta un servicio **compatible con la API de S3** que:

- corra en `docker compose` junto a PostgreSQL, sin cuenta de AWS ni internet;
- **verifique la firma SigV4** de las URLs prefirmadas, incluidas las cabeceras firmadas (`Content-Type` y `Content-Length`). Si no la verifica, las pruebas no detectan errores de firma que en S3 real darían 403;
- soporte `HeadObject`, `GetObject` con `If-Match`, `CopyObject` con `x-amz-copy-source-if-match` y `DeleteObject`;
- tenga una imagen Docker oficial, gratuita y con versión fija, para usarla también con Testcontainers.

Las dos opciones habituales dejaron de servir en 2026:

- **LocalStack** exige un token de autenticación (cuenta) para su imagen desde marzo de 2026, también en el plan gratuito.
- **MinIO Community Edition** dejó de publicar imágenes Docker y binarios oficiales: solo se distribuye como código fuente.

## Decisión

1. **SeaweedFS 4.48** (`chrislusf/seaweedfs:4.48`) es el S3 local. En `docker-compose.yml` corre como el servicio `seaweedfs`, con `server -s3 -dir=/data`, el puerto `127.0.0.1:${S3_PORT:-8333}:8333` y el volumen `seaweedfs-datos`.
2. **Credenciales por variables de entorno, con firma obligatoria.** El contenedor recibe `AWS_ACCESS_KEY_ID` y `AWS_SECRET_ACCESS_KEY` del `.env` (compose falla si faltan), así que SeaweedFS rechaza cualquier petición sin firma SigV4 válida. Son credenciales solo locales: `.env.example` trae valores de ejemplo (`autoreel-local` / `cambia-este-secreto-local`).
3. **El código no sabe qué servicio hay detrás.** El puerto `AlmacenDeArchivos` (`almacenamiento/dominio`) lo implementa un único adaptador, `S3Almacen` (`almacenamiento/infraestructura`), con el AWS SDK for Java v2 (`S3Client` y `S3Presigner`). Lo único que cambia entre local y AWS es la configuración de `PropiedadesAlmacenamiento` (`autoreel.almacenamiento.*`):

   | Propiedad | Variable | Local (SeaweedFS) | AWS |
   |---|---|---|---|
   | `endpoint` | `S3_ENDPOINT` | `http://localhost:8333` | vacía |
   | `endpoint-publico` | `S3_ENDPOINT_PUBLICO` | vacía (o `http://<IP-de-tu-PC>:8333` para probar desde el celular) | vacía |
   | `clave-acceso` / `clave-secreta` | `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | las del `.env` | vacías |
   | `crear-bucket` | — | `true` (`application-dev.yml`) | `false` (`application.yml`) |

   - Con `endpoint`, `S3Almacen` usa `endpointOverride` y estilo de ruta (`forcePathStyle(true)` en el cliente y `S3Configuration.pathStyleAccessEnabled(true)` en el firmador, cuyo constructor no tiene `forcePathStyle`), y `StaticCredentialsProvider` con las claves de las propiedades.
   - Sin `endpoint` (AWS), usa `DefaultCredentialsProvider`, para tomar el rol de la instancia y no perder `AWS_SESSION_TOKEN`.
   - Las claves se pasan por propiedades (`${AWS_ACCESS_KEY_ID:}`) porque el SDK lee las variables del entorno del proceso, no las del `.env` que importa el perfil `dev`.
4. **El perfil `dev` crea el bucket** (`AWS_S3_BUCKET`, por defecto `autoreel-dev`) al arrancar si no existe (`crear-bucket: true`).
5. **Pruebas.**
   - `S3AlmacenTest` levanta la misma imagen con Testcontainers (espera `Wait.forHttp("/").forPort(8333).forStatusCode(403)`, hasta 2 minutos) y prueba: subida con la URL firmada (200), otro tipo u otro tamaño (403), lectura sin descargar un archivo demasiado grande, copia con una etiqueta vieja (rechazada) y lectura con URL firmada.
   - Las pruebas de integración usan `AlmacenEnMemoria` (`autoreel.almacenamiento.tipo: memoria` en `application-test.yml`); `ConfiguracionAlmacenamiento` solo crea `S3Almacen` cuando el tipo es `s3` (el valor por defecto).

## Alternativas consideradas

- **LocalStack.** Emula S3 y el resto de AWS, pero desde marzo de 2026 exige un token de cuenta incluso para el plan gratuito: obliga a registrar una cuenta y a guardar un secreto más en el `.env` y en el CI.
- **MinIO Community Edition.** Fue la opción clásica, pero ya no publica imágenes ni binarios: habría que compilarla o depender de imágenes de terceros sin mantenimiento.
- **Un bucket real de S3 en AWS para desarrollo.** Es fiel a producción, pero exige cuenta y credenciales reales en el equipo y en el CI, cuesta dinero y no funciona sin internet.
- **Un almacén en disco o en memoria también en `dev`.** Más simple, pero el navegador no podría subir con URLs firmadas y no se probaría la parte más delicada del flujo (firma, copia condicional y CORS del navegador).
- **Simuladores solo para pruebas, como Adobe S3Mock.** Son livianos, pero no comprueban la firma de las URLs prefirmadas, que es justo lo que se quiere probar.

## Consecuencias

- **Positivas:**
  - Desarrollo y CI sin cuenta de AWS, sin tokens y sin internet: `docker compose up -d` basta.
  - La firma se verifica de verdad: una URL firmada con otro `Content-Type` u otro tamaño da 403, igual que en S3.
  - El mismo adaptador `S3Almacen` sirve en local y en AWS; cambiar de servicio es configuración, no código (regla 3).
- **Negativas y riesgos:**
  - SeaweedFS **no es S3**: puede diferir en detalles (mensajes de error, CORS, reglas de ciclo de vida, cifrado). Las reglas de CORS y de expiración del bucket solo se configuran y prueban en AWS (`docs/despliegue-aws.md`, fase 8).
  - Hay que mantener la versión fija de la imagen y revisarla al actualizar dependencias.
  - Para probar desde el celular (RNF-03) hay que definir `S3_ENDPOINT_PUBLICO` con la IP del equipo y publicar el puerto 8333 en la red local mientras se prueba (ver `README.md`).
- **Cuándo revisar esta decisión:** si SeaweedFS deja de publicar imágenes, si aparece una diferencia con S3 que afecte al flujo de subida o si el proyecto necesita emular otros servicios de AWS en local.
