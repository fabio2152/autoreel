# AutoReel

Plataforma web para que negocios pequeños, sin conocimientos de marketing, creen en pocos minutos un Reel de 15 segundos de sus productos a partir de sus propias fotos, con IA, y lo publiquen directamente en Instagram.

El MVP atiende un solo nicho: **pastelerías y postres**. El administrador de la plataforma crea cada negocio, le asigna un plan y una fecha de pago, y le entrega su acceso.

> **Estado:** fase 4 de 9 (guion con IA y propuesta del video). Ver [`CHANGELOG.md`](CHANGELOG.md).

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator), Flyway, springdoc-openapi |
| Base de datos | PostgreSQL 17 |
| Archivos | Amazon S3 (en local, SeaweedFS con API compatible con S3), AWS SDK for Java v2 |
| IA | Google Gemini (guion), llamado por su API REST |
| Frontend | React 19, TypeScript, Vite 8, Tailwind CSS 4, TanStack Query, React Router |
| Pruebas | JUnit 5, Spring Security Test, Testcontainers (PostgreSQL y SeaweedFS) |
| CI | GitHub Actions: backend (`mvnw verify`) y frontend (lint y build) |

Arquitectura: API REST en un **monolito modular** (`backend/`, paquete `com.autoreel`, un módulo por área de negocio con capas `api`, `aplicacion`, `dominio` e `infraestructura`) y una SPA en `frontend/`. La sesión es un JWT en una cookie `HttpOnly`, con protección CSRF.

## Requisitos

- Java 21 (no hace falta instalar Maven: se usa el wrapper `mvnw`).
- Node.js 24 y npm.
- Docker (para PostgreSQL, SeaweedFS y las pruebas de integración con Testcontainers).

## Ejecutar en local

1. **Variables de entorno.** Copia `.env.example` como `.env` en la raíz y completa los valores. Genera `JWT_SECRET` con `openssl rand -base64 32`. Si el puerto 5432 está ocupado, cambia `POSTGRES_PORT` y el puerto de `DB_URL`. Las credenciales `AWS_*` del `.env` son solo para SeaweedFS local: inventa cualquier par. `GEMINI_API_KEY` es **opcional**: si la dejas vacía, la propuesta del video usa el guion de respaldo (sin IA); con una clave de Google AI Studio, el guion lo escribe Gemini.

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
- **Recorrido del negocio:** en Inicio, el chat del video del día; al terminarlo, "Ver mi propuesta" abre el storyboard (`/videos/{id}`), donde se editan los textos, se cambian las fotos y se ajusta el precio. Todas las propuestas quedan en "Mis videos" (`/videos`). "Crear mi video" se activa en la fase 5.
- **Negocio nuevo:** créalo desde el panel (`/admin` → "Crear negocio"). El sistema muestra una contraseña temporal una sola vez; al entrar con ella, el negocio crea su propia contraseña y pasa por el onboarding. Para la demostración en vivo de la sección 20, usa un correo distinto del negocio de ejemplo.

### Probar desde el celular

En la misma red Wi-Fi, ejecuta el frontend con `npm run dev -- --host` y abre `http://<IP-de-tu-PC>:5173`. Para que el celular pueda subir fotos, define en `.env` `S3_ENDPOINT_PUBLICO=http://<IP-de-tu-PC>:8333` y publica el puerto de SeaweedFS en la red local (en `docker-compose.yml`, cambia `127.0.0.1:` por `0.0.0.0:` solo mientras pruebas).

## Pruebas y calidad

```bash
cd backend && ./mvnw verify        # unitarias + integración (requiere Docker)
cd frontend && npm run lint && npm run build
```

Las pruebas no necesitan `GEMINI_API_KEY`: el adaptador de Gemini se prueba contra un servidor simulado.

## Documentación

La especificación, la arquitectura, el modelo de datos, los ADR y los manuales se mantienen en la carpeta `docs/` **local**, que no se versiona (ver `CLAUDE.md`). Las reglas de trabajo del proyecto están en [`CLAUDE.md`](CLAUDE.md).
