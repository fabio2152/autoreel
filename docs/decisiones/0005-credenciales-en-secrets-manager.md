# ADR 0005 — Credenciales en variables de entorno y en AWS Secrets Manager

- **Estado:** Aceptada; modificada por el ADR 0018 (las claves de IA también se pegan en el panel, siempre fuera de la base de datos)
- **Fecha:** 2026-11-03

## Contexto

AutoReel maneja credenciales de varios tipos:

- **Propias de la aplicación:** la base de datos y la clave para firmar los JWT.
- **De proveedores:** la clave de Google para Gemini y Veo, el secreto de la app de Meta y las credenciales de AWS.
- **Para cifrar datos:** la clave que cifra los tokens de Instagram.

Una credencial filtrada en el repositorio o en la base de datos compromete a todos los clientes y puede generar costos de IA a nombre de AutoReel. La especificación lo prohíbe expresamente (regla 6 de la sección 0, RN-55), y la Ley 29733 de protección de datos personales obliga a proteger la información (`docs/contexto-vision.md`, sección 8).

## Decisión

1. **Las claves nunca se escriben en el código ni en la base de datos.**
2. **En desarrollo**, se leen de variables de entorno definidas en un archivo `.env`, en la raíz del repositorio.
   - El `.env` está en `.gitignore`. Solo se versiona `.env.example`, con valores de ejemplo y agrupado por fase.
   - Lo leen `docker compose` (Postgres) y el backend con el perfil `dev`, que lo importa en `application-dev.yml` con `spring.config.import: optional:file:../.env[.properties]`.
3. **En producción** (futuro, fase 8), las credenciales vienen de **AWS Secrets Manager**, o de Parameter Store, que la sección 11.4 admite como alternativa.
4. **`application.yml` referencia cada variable de forma explícita** (`${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`, `${JWT_SECRET}`, `${APP_URL}`), sin valores por defecto para los secretos. Si falta un secreto, el backend no arranca.
   - `JWT_SECRET` se valida al iniciar en `ServicioJwt`: debe estar en base64 y tener al menos 32 bytes.
   - `PropiedadesSeguridad` es `@Validated`.
5. **Los modelos de IA solo guardan el nombre del secreto.** La columna `modelo_ia.nombre_secreto` guarda, por ejemplo, `autoreel/gemini`, nunca la clave (RN-55).
   - El panel mostrará si la credencial está configurada y permitirá probar la conexión, pero nunca la mostrará ni permitirá escribirla (futuro, fase 7).
6. **Otros datos sensibles:**
   - Las contraseñas de los usuarios se guardan solo como hash BCrypt (`usuario.contrasena_hash`). La contraseña temporal se muestra una sola vez, con `Cache-Control: no-store`.
   - Los tokens de Instagram se guardarán cifrados con `TOKEN_CIFRADO_CLAVE` (futuro, fase 6).
   - Ningún DTO expone hashes ni tokens (RN-39).

**Variables actuales** (`.env.example`):

- **Postgres:** `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_PORT`.
- **Conexión del backend:** `DB_URL`, `DB_USER` y `DB_PASSWORD`.
- **Sesión:** `JWT_SECRET` y `SESION_HORAS`.
- **Administrador inicial:** `ADMIN_EMAIL` y `ADMIN_PASSWORD`. Solo se usan si no existe ningún administrador; ver `InicializadorAdministrador`.
- **Frontend:** `APP_URL`.

- **Almacenamiento (desde la fase 2):** `AWS_REGION`, `AWS_S3_BUCKET`, `S3_ENDPOINT`, `S3_PORT`, `AWS_ACCESS_KEY_ID` y `AWS_SECRET_ACCESS_KEY`. Las claves `AWS_*` son solo para SeaweedFS local, con valores de ejemplo; en AWS no se definen y el backend usa el rol de la instancia (ADR 0009, `docs/despliegue-aws.md`). `S3_ENDPOINT_PUBLICO` es opcional, para probar desde el celular.
- **Negocio de ejemplo (desde la fase 2, solo perfil `dev`):** `DEMO_NEGOCIO_EMAIL` y `DEMO_NEGOCIO_PASSWORD`.

**Variables futuras, comentadas en el archivo:**

- `GEMINI_API_KEY` (fases 4 y 5).
- `FFMPEG_PATH` (fase 5).
- `META_APP_ID`, `META_APP_SECRET`, `META_REDIRECT_URI` y `TOKEN_CIFRADO_CLAVE` (fase 6).

**Para evitar filtraciones:**

- `.gitignore` excluye `.env`, `.env.*` (salvo `.env.example`), `*.pem`, `*.key`, `*.p12` y `*.jks`.
- Las pruebas usan valores fijos de `src/test/resources/application-test.yml`.
- Actuator solo expone `health`, sin detalles, y los errores nunca incluyen la traza ni el mensaje interno (`server.error.include-*`).

## Alternativas consideradas

- **Guardar las claves de los proveedores en la tabla `modelo_ia` y editarlas desde el panel.** Es más cómodo para el administrador, pero pone las claves en la base de datos y en sus respaldos, y exige cifrado y gestión de claves propios. Queda fuera del alcance del MVP (sección 21).
- **Archivos de configuración versionados con valores reales**, aunque el repositorio sea privado. El riesgo de filtración es inaceptable.
- **HashiCorp Vault.** Es potente, pero es otra pieza que operar. Secrets Manager ya está integrado con AWS, donde se desplegará el sistema.

## Consecuencias

- **Positivas:**
  - Ninguna clave en git ni en la base de datos.
  - Se pueden rotar las credenciales sin cambiar el código.
  - El administrador nunca ve las claves.
  - Una configuración incompleta falla al arrancar y no en mitad de una petición.
- **Negativas:**
  - Hay que mantener `.env.example` al día en cada fase.
  - En la fase 8 habrá que configurar Secrets Manager, los permisos de IAM y la carga de los secretos al arrancar, y documentarlo en `docs/despliegue-aws.md`.
  - Agregar una credencial nueva obliga a desplegar la configuración: no se puede hacer desde el panel.
