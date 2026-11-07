# ADR 0001 — API REST en Spring Boot y frontend en React

- **Estado:** Aceptada
- **Fecha:** 2026-11-03

## Contexto

AutoReel es el proyecto del curso Diseño de Software, que parte de Java, pero se piensa como un SaaS real (`docs/contexto-vision.md`, secciones 1 y 5). La interfaz del negocio necesita mucha interactividad: el chat guiado, el carrusel de productos, el storyboard editable y el aviso de "tu video está listo" (especificación, secciones 12.1 y 14). Con plantillas del lado del servidor (Thymeleaf) esas pantallas son difíciles de construir y de mantener.

Además, la plataforma debe poder servir más adelante a una aplicación móvil (hoja de ruta, etapa 4) y desplegarse en AWS con un costo bajo (sección 11.4).

## Decisión

Se separa el sistema en dos aplicaciones dentro del mismo repositorio:

- **`backend/`:** una API REST en Spring Boot (Java 21). Todas las rutas cuelgan de `/api` y todos los errores tienen la forma `{"codigo": "...", "mensaje": "..."}`, con mensajes en español (record `ErrorApi`, `ManejadorGlobalDeErrores` y `RespuestasDeSeguridad`). La API se documenta con springdoc-openapi, activo solo en el perfil `dev`.
- **`frontend/`:** una SPA en React 19 con TypeScript, Vite 8, Tailwind CSS 4, TanStack Query para los datos del servidor y React Router. El lint se hace con oxlint (`npm run lint`).

Ambas aplicaciones comparten **un solo origen**:

- **En desarrollo:** el proxy de Vite (`vite.config.ts`) envía `/api` a `http://localhost:8080`.
- **En producción (futuro, fase 8):** CloudFront sirve el frontend desde S3 y dirige `/api/*` al backend.

Al compartir origen no se necesita CORS, y la cookie de sesión funciona (ver ADR 0007).

**Lo que existe en la fase 1:**

- Las rutas de autenticación (`AuthController`): `POST /api/auth/login`, `/logout` y `/cambiar-contrasena`, y `GET /api/auth/yo`.
- Las rutas de administración:
  - `AdminNegocioController`: `GET` y `POST /api/admin/negocios`, `GET /api/admin/negocios/{id}`, `POST .../reiniciar-contrasena`, `/suspender` y `/reactivar`, y `GET /api/admin/nichos`.
  - `PlanController`: `GET /api/admin/planes`, solo lectura.
- En el frontend, el cliente HTTP (`src/api/cliente.ts`), las pantallas de inicio de sesión y de creación de contraseña, y el panel del administrador (`src/admin/`).

## Alternativas consideradas

- **Spring MVC con Thymeleaf (una sola aplicación).** Es más simple de desplegar, pero no resuelve bien el chat, el storyboard editable ni los avisos asíncronos, y no sirve para una app móvil.
- **Thymeleaf con islas de JavaScript (htmx o similar).** Reduce el JavaScript, pero mezcla dos modelos de interfaz y no deja una API reutilizable.
- **Frontend en otro framework (Angular, Vue) o con SSR (Next.js).** No aporta ventajas claras para una SPA detrás de un login. El SSR exigiría un servidor Node adicional, cuando el objetivo es servir archivos estáticos desde S3.

## Consecuencias

- **Positivas:**
  - Interfaz interactiva y móvil primero.
  - La misma API servirá a una app móvil sin rehacer el backend.
  - El frontend se despliega como archivos estáticos, que son baratos y escalan solos, y el backend escala por separado.
  - Es la arquitectura habitual de un SaaS, lo que facilita incorporar desarrolladores.
- **Negativas:**
  - Hay dos proyectos que construir, probar y desplegar. El CI (`.github/workflows/ci.yml`) tiene un job para el backend y otro para el frontend.
  - La seguridad de una SPA con cookie exige protección CSRF explícita (ADR 0007).
  - Hay que mantener sincronizados los tipos del frontend con los DTO del backend. En la fase 1 se escriben a mano en `src/api/`.
- **Restricción que se mantiene:** el backend y el frontend deben compartir origen. Un despliegue en dominios distintos obligaría a revisar CORS, `SameSite` y CSRF.
