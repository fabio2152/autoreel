# AutoReel

Plataforma web para que negocios pequeños, sin conocimientos de marketing, creen en pocos minutos un Reel de 15 segundos de sus productos a partir de sus propias fotos, con IA, y lo publiquen directamente en Instagram.

El MVP atiende un solo nicho: **pastelerías y postres**. El administrador de la plataforma crea cada negocio, le asigna un plan y una fecha de pago, y le entrega su acceso.

> **Estado:** fase 5 de 9 (generación, revisión y descarga del video). Ver [`CHANGELOG.md`](CHANGELOG.md).

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator), Flyway, springdoc-openapi |
| Base de datos | PostgreSQL 17 |
| Archivos | Amazon S3 (en local, SeaweedFS con API compatible con S3), AWS SDK for Java v2 |
| IA | Google AI Studio: Gemini (guion) y Gemini Omni Flash (clips de video), activos. Google Cloud: Gemini y Veo 3.1, listos e inactivos. Todos por sus API REST |
| Video | FFmpeg (composición del video final) |
| Frontend | React 19, TypeScript, Vite 8, Tailwind CSS 4, TanStack Query, React Router |
| Pruebas | JUnit 5, Spring Security Test, Testcontainers (PostgreSQL y SeaweedFS), FFmpeg real para la composición |
| CI | GitHub Actions: backend (`mvnw verify`) y frontend (lint y build) |

Arquitectura: API REST en un **monolito modular** (`backend/`, paquete `com.autoreel`, un módulo por área de negocio con capas `api`, `aplicacion`, `dominio` e `infraestructura`) y una SPA en `frontend/`. La sesión es un JWT en una cookie `HttpOnly`, con protección CSRF.

## Requisitos

- Java 21 (no hace falta instalar Maven: se usa el wrapper `mvnw`).
- Node.js 24 y npm.
- Docker (para PostgreSQL, SeaweedFS y las pruebas de integración con Testcontainers).
- FFmpeg (con `ffprobe`), para componer el video y para sus pruebas. En Windows: `winget install Gyan.FFmpeg`; en Ubuntu: `sudo apt-get install ffmpeg`.

## Ejecutar en local

1. **Variables de entorno.** Copia `.env.example` como `.env` en la raíz y completa los valores. Genera `JWT_SECRET` con `openssl rand -base64 32`. Si el puerto 5432 está ocupado, cambia `POSTGRES_PORT` y el puerto de `DB_URL`. Las credenciales `AWS_*` del `.env` son solo para SeaweedFS local: inventa cualquier par. `GEMINI_API_KEY` es **opcional**: es una clave de Google AI Studio (https://aistudio.google.com/apikey) y sirve para el guion (Gemini) y para los clips de video (Gemini Omni Flash). Los clips tienen costo por segundo (unos US$0,07 a 720p, sin plan gratuito), así que el proyecto de la clave necesita facturación activa. Si la dejas vacía, la propuesta usa el guion de respaldo y cada plano, un acercamiento lento de la foto hecho con FFmpeg; el video se entrega igual. `GOOGLE_CLOUD_CREDENCIAL_ARCHIVO` también es **opcional**: es la ruta al JSON de una cuenta de servicio de Google Cloud (Gemini y Veo en Vertex AI). Solo se usa si se activan esos modelos en la base de datos; hoy están inactivos. Nunca lo subas a git: guárdalo fuera del repositorio o en la carpeta `credenciales/`, que git ignora. `FFMPEG_PATH` es la ruta a `ffmpeg`: déjala en `ffmpeg` si está en el PATH o pon la ruta completa (en Windows, la carpeta `bin` de la instalación de winget).

2. **Base de datos y almacenamiento** (PostgreSQL y SeaweedFS):

   ```bash
   docker compose up -d
   ```

3. **Backend** (desde `backend/`), con el perfil `dev`, que lee el `.env` de la raíz:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   En PowerShell: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`. Flyway crea las tablas y el perfil `dev` crea el bucket al arrancar. La API queda en `http://localhost:8080` y su documentación en `http://localhost:8080/swagger-ui.html` (solo en `dev`).

4. **Frontend** (desde `frontend/`):

   ```bash
   npm install
   npm run dev
   ```

   Abre `http://localhost:5173`. Vite envía `/api` al backend, así que ambos comparten origen y la cookie de sesión funciona.

## Cuentas de prueba

- **Administrador:** se crea al primer arranque con `ADMIN_EMAIL` y `ADMIN_PASSWORD` del `.env` (solo si todavía no existe uno).
- **Negocio de ejemplo:** con el perfil `dev` se crea "Pastelería Dulce Encanto" (Miraflores) con `DEMO_NEGOCIO_EMAIL` y `DEMO_NEGOCIO_PASSWORD`, su perfil completo y tres productos con imágenes de relleno.
- **Recorrido del negocio:**
  1. En Inicio, el chat del video del día; al terminarlo, "Ver mi propuesta" abre el storyboard (`/videos/{id}`), donde se editan los textos, se cambian las fotos y se ajusta el precio.
  2. "Crear mi video" usa el video del día y muestra "Creando tu video" con el avance de cada plano. Se puede salir: cuando termina, la aplicación avisa "Tu video está listo".
  3. En la revisión se ve el video, se regenera un plano (hasta 3 veces en el plan Lite), se edita un texto o el precio (no gasta regeneraciones) y se aprueba.
  4. "Descargar" baja el MP4 de 15 segundos. Todos los videos quedan en "Mis videos" (`/videos`). La publicación en Instagram llega en la fase 6.
- **Negocio nuevo:** créalo desde el panel (`/admin` → "Crear negocio"). El sistema muestra una contraseña temporal una sola vez; al entrar con ella, el negocio crea su propia contraseña y pasa por el onboarding. Para la demostración en vivo de la sección 20, usa un correo distinto del negocio de ejemplo.

### Probar desde el celular

En la misma red Wi-Fi, ejecuta el frontend con `npm run dev -- --host` y abre `http://<IP-de-tu-PC>:5173`. Para que el celular pueda subir fotos, define en `.env` `S3_ENDPOINT_PUBLICO=http://<IP-de-tu-PC>:8333` y publica el puerto de SeaweedFS en la red local (en `docker-compose.yml`, cambia `127.0.0.1:` por `0.0.0.0:` solo mientras pruebas).

## Pruebas y calidad

```bash
cd backend && ./mvnw verify        # unitarias + integración (requiere Docker)
cd frontend && npm run lint && npm run build
```

Las pruebas no necesitan `GEMINI_API_KEY` ni `GOOGLE_CLOUD_CREDENCIAL_ARCHIVO`: los adaptadores de Google AI Studio (Gemini y Gemini Omni Flash) y de Google Cloud (Gemini y Veo) se prueban contra servidores simulados. Sí necesitan FFmpeg: la prueba de composición arma un video real y lo revisa con `ffprobe` (usa `FFMPEG_PATH` y `FFPROBE_PATH` si están definidos; si no, los del PATH). En total son 233 pruebas en el backend.

## Documentación

La especificación, la arquitectura, el modelo de datos, los ADR y los manuales se mantienen en la carpeta `docs/` **local**, que no se versiona (ver `CLAUDE.md`). Las reglas de trabajo del proyecto están en [`CLAUDE.md`](CLAUDE.md).
