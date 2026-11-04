# AutoReel

Plataforma web para que negocios pequeños, sin conocimientos de marketing, creen en pocos minutos un Reel de 15 segundos de sus productos a partir de sus propias fotos, con IA, y lo publiquen directamente en Instagram.

El MVP atiende un solo nicho: **pastelerías y postres**. El administrador de la plataforma crea cada negocio, le asigna un plan y una fecha de pago, y le entrega su acceso.

> **Estado:** fase 1 de 9 (base: autenticación, roles y panel de administración). Ver [`CHANGELOG.md`](CHANGELOG.md).

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator), Flyway, springdoc-openapi |
| Base de datos | PostgreSQL 17 |
| Frontend | React 19, TypeScript, Vite 8, Tailwind CSS 4, TanStack Query, React Router |
| Pruebas | JUnit 5, Spring Security Test, Testcontainers |
| CI | GitHub Actions: backend (`mvnw verify`) y frontend (lint y build) |

Arquitectura: API REST en un **monolito modular** (`backend/`, paquete `com.autoreel`, un módulo por área de negocio con capas `api`, `aplicacion`, `dominio` e `infraestructura`) y una SPA en `frontend/`. La sesión es un JWT en una cookie `HttpOnly`, con protección CSRF.

## Requisitos

- Java 21 (no hace falta instalar Maven: se usa el wrapper `mvnw`).
- Node.js 24 y npm.
- Docker (para PostgreSQL y para las pruebas de integración con Testcontainers).

## Ejecutar en local

1. **Variables de entorno.** Copia `.env.example` como `.env` en la raíz y completa los valores. Genera `JWT_SECRET` con `openssl rand -base64 32`. Si el puerto 5432 está ocupado, cambia `POSTGRES_PORT` y el puerto de `DB_URL`.

2. **Base de datos:**

   ```bash
   docker compose up -d
   ```

3. **Backend** (desde `backend/`), con el perfil `dev`, que lee el `.env` de la raíz:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   En PowerShell: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`. Flyway crea las tablas al arrancar. La API queda en `http://localhost:8080` y su documentación en `http://localhost:8080/swagger-ui.html` (solo en `dev`).

4. **Frontend** (desde `frontend/`):

   ```bash
   npm install
   npm run dev
   ```

   Abre `http://localhost:5173`. Vite envía `/api` al backend, así que ambos comparten origen y la cookie de sesión funciona.

## Cuentas de prueba

- **Administrador:** se crea al primer arranque con `ADMIN_EMAIL` y `ADMIN_PASSWORD` del `.env` (solo si todavía no existe uno).
- **Negocio:** créalo desde el panel (`/admin` → "Crear negocio"). El sistema muestra una contraseña temporal una sola vez; al entrar con ella, el negocio crea su propia contraseña.

## Pruebas y calidad

```bash
cd backend && ./mvnw verify        # unitarias + integración (requiere Docker)
cd frontend && npm run lint && npm run build
```

## Documentación

La especificación, la arquitectura, el modelo de datos, los ADR y los manuales se mantienen en la carpeta `docs/` **local**, que no se versiona (ver `CLAUDE.md`). Las reglas de trabajo del proyecto están en [`CLAUDE.md`](CLAUDE.md).
