# ADR 0027: Despliegue mínimo en AWS para la demostración

- **Estado:** Aceptada
- **Fecha:** 2026-11-25
- **Modifica:** la sección 11.4 de la especificación (sin CloudFront ni S3 para el frontend) y, en el despliegue, el ADR 0001 (el backend sirve el frontend). Concreta el ADR 0005 y el ADR 0018 en AWS (Secrets Manager).

## Contexto

La fase 8 debe dejar AutoReel en AWS para la **demostración en vivo del 2026-11-26**. La especificación (sección 11.4) preveía:

- el frontend en S3 servido por CloudFront, con `/api/*` dirigido al backend;
- el backend con FFmpeg en EC2 o Elastic Beanstalk, detrás de un balanceador con HTTPS;
- RDS, S3 privado, Secrets Manager o Parameter Store y CloudWatch.

El dueño del proyecto (Fabio) pidió el 2026-11-25 **"solo usar servicios de AWS que sean necesarios"**: AutoReel estará encendido un par de horas para la demostración y después se apaga. Hay que cuidar el costo y el tiempo de armado, sin perder lo que el código ya exige:

- **Un solo origen** para el frontend y la API: la cookie de sesión `Secure` y el CSRF no funcionan entre orígenes sin CORS (ADR 0007).
- **HTTPS**, por la cookie `Secure`.
- **Ninguna clave en el disco del servidor** (ADR 0018): las claves que se pegan en el panel deben ir a Secrets Manager.
- **FFmpeg** junto al backend (ADR 0016).
- Un dominio propio: `autoreel.sapps.lat`, con el DNS en Namecheap.

## Decisión

Decisiones de Fabio del 2026-11-25:

1. **Región `us-east-1`, VPC por defecto.**
2. **Una sola EC2 `t3.medium`** (Ubuntu 24.04, Docker) con **un solo contenedor**: el backend, que **también sirve el frontend compilado** (`ConfiguracionFrontend`, con `index.html` para las rutas de React Router). El puerto 8080 solo acepta tráfico del balanceador; el SSH (22), solo la IP del administrador.
3. **Un Application Load Balancer** público en dos zonas, con un **certificado de ACM** para `autoreel.sapps.lat` (validado con un CNAME que Fabio agrega en Namecheap). El 80 redirige al 443, y el grupo de destino revisa `GET /actuator/health` en el 8080. El dominio es un CNAME `autoreel` → nombre DNS del balanceador. El backend confía en los encabezados `X-Forwarded-*` (`server.forward-headers-strategy: native`).
4. **RDS PostgreSQL 17 `db.t4g.micro`**, privada, solo alcanzable desde el grupo de seguridad de la EC2.
5. **Un bucket S3 privado** (Block Public Access, *bucket owner enforced*, CORS `PUT` y `GET` desde `https://autoreel.sapps.lat`, `subidas/` expira a 1 día).
6. **Secrets Manager** con tres secretos: `autoreel/app` (JSON con `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL` y `ADMIN_PASSWORD`), `autoreel/gemini` y `autoreel/google-cloud` (el contenido del JSON de la cuenta de servicio).
   - `autoreel/app` lo carga **`SecretosDeArranque`** (un `EnvironmentPostProcessor`, variable `SECRETOS_DE_ARRANQUE`) antes de arrancar Spring.
   - Los de IA los lee y escribe **`SecretosDeAws`** (perfil `aws`), que reemplaza a `SecretosDeConfiguracion`. Borrar desde el panel elimina el secreto sin recuperación; `origen` solo distingue `PANEL` y `NINGUNO`.
7. **Un rol IAM de instancia** con permisos de objetos y `ListBucket` en el bucket, solo lectura (`GetSecretValue`) sobre `autoreel/app`, y `GetSecretValue`, `PutSecretValue`, `CreateSecret` y `DeleteSecret` solo sobre `autoreel/gemini*` y `autoreel/google-cloud*`, para que el panel no pueda reemplazar `autoreel/app`. Sin claves `AWS_*`.
8. **Perfil de Spring `aws`** (`application-aws.yml`): pool de 8 conexiones y encabezados del balanceador. Las variables no secretas van en `/opt/autoreel/autoreel.env`.
9. **Publicación sin registro de imágenes:** `deploy/publicar.sh` construye la imagen en la PC (`backend/Dockerfile`, tres etapas: Node, JDK y Debian trixie con OpenJDK 21 y FFmpeg 7.1), la copia con `scp` y la levanta con `docker compose`. La versión anterior queda como `autoreel-backend:anterior` para volver atrás.
10. **Los recursos se crean con la AWS CLI** desde la PC de Fabio. Los comandos se documentan en `docs/despliegue-aws.md`, sección 7.

**Sin** CloudFront, ECR, NAT, Caddy, Elastic Beanstalk, Route 53 ni CloudWatch Logs. Los registros quedan en Docker, en la instancia.

## Alternativas consideradas

- **CloudFront con el frontend en S3, como en la sección 11.4.** Escala el frontend sin servidor y tiene caché en el borde. Suma un bucket, una distribución, un certificado en `us-east-1` para CloudFront, la regla de `index.html` para la SPA, el reenvío de cookies y de `X-XSRF-TOKEN` a `/api/*`, e invalidaciones en cada despliegue. Para una demostración de un par de horas no aporta nada que el backend no resuelva con una clase. Descartada por ahora; sigue siendo el camino si el frontend necesita escalar aparte.
- **Elastic Beanstalk con Docker.** Arma el balanceador y la instancia solo, pero agrega su propia capa (entornos, versiones de la aplicación en S3, plataforma) y oculta lo que se quiere mostrar. Descartada.
- **Caddy (u otro proxy con HTTPS) en la EC2, sin balanceador.** Más barato: Caddy saca el certificado de Let's Encrypt y se ahorra el ALB y dos IPv4 públicas. Descartada porque Fabio quiso usar **servicios de AWS** para el HTTPS y el tráfico de entrada, y para que el 8080 no quede expuesto a internet.
- **ECR para la imagen.** Es la forma habitual de llevar la imagen a la instancia. Suma un repositorio, permisos de lectura en el rol y el inicio de sesión de Docker en cada lado. Con una instancia, `docker save` y `scp` bastan. Descartada.
- **NAT y subredes privadas para la EC2.** Más aislamiento, pero el NAT cuesta más por hora que la propia instancia. El grupo de seguridad ya cierra la entrada. Descartada.
- **Parameter Store en lugar de Secrets Manager** (lo admite la sección 11.4). Gratis en el nivel estándar, pero `SecretosDeAws` tendría que crear y borrar parámetros cifrados igual. El ADR 0018 ya preveía Secrets Manager. Descartada.
- **Inyectar los secretos como variables de entorno** desde la instancia (por ejemplo, un script que los lea con la CLI y escriba el `autoreel.env`). Dejaría las contraseñas en el disco. Descartada: `SecretosDeArranque` los lee en memoria al arrancar.

## Consecuencias

- **Costo:** unos US$ 0,10 por hora (ALB, EC2, RDS e IPv4 públicas); un par de horas cuesta menos de US$ 1. Detener la EC2 y la RDS no deja el costo en cero: siguen sus discos, y el balanceador y sus IPv4 cobran hasta borrarlos. RDS detenida vuelve a iniciar sola a los 7 días.
- **Un solo origen sin CloudFront:** el frontend y la API comparten `https://autoreel.sapps.lat`. El backend sirve el frontend con caché larga para `/assets/**` y sin caché para lo demás.
- **Un despliegue corta el servicio un momento:** con una instancia, el balanceador responde 502 o 503 mientras arranca el contenedor nuevo.
- **Una sola instancia:** las tareas programadas no necesitan coordinarse. Escalar a varias exige revisar la tarea diaria de pagos y el reclamo de los clips de Omni (`docs/despliegue-aws.md`, sección 3.5).
- **Sin CloudWatch Logs:** los registros viven en la instancia y se pierden al terminarla.
- **El contenedor necesita IMDS con límite de saltos 2** para obtener las credenciales del rol.
- **El rol solo escribe `autoreel/gemini*` y `autoreel/google-cloud*`:** el panel no puede reemplazar `autoreel/app`, y un modelo de IA con otro nombre de secreto exige ampliar la política.
- **Las claves pegadas en el panel** quedan en Secrets Manager y sobreviven a cada despliegue; en AWS no hay un `.env` al que volver.
- El código no depende de esta topología: S3, los secretos y el frontend se configuran con variables de entorno y perfiles. Pasar después a CloudFront o a varias instancias no cambia los módulos de negocio.
