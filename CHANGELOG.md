# Changelog

Cambios de AutoReel por fase. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [Fase 1 — Base] - 2026-11-03

### Agregado

- **Repositorio y monorepo:** `backend/` (Spring Boot 4.1.2, Java 21, Maven wrapper, paquete `com.autoreel`) y `frontend/` (React 19, TypeScript, Vite 8, Tailwind CSS 4).
- **Entorno local:** `docker-compose.yml` con PostgreSQL 17 y `.env.example` con las variables de todas las fases.
- **Base de datos con Flyway:**
  - `V1`: negocios y usuarios.
  - `V2`: modelos de IA, planes y suscripciones.
  - Datos iniciales: Gemini (texto), Veo 3.1 Lite (video) y el plan Lite (sección 16).
- **Autenticación (RF-01, RF-13, RF-14):**
  - JWT en una cookie `HttpOnly`, con protección CSRF y sesión de 8 horas.
  - Roles ADMIN y NEGOCIO verificados en el backend.
  - Contraseña temporal de 12 caracteres que vence a las 48 horas y obliga a crear una propia.
  - Reglas de contraseña (RN-04).
  - Bloqueo de 15 minutos tras 5 intentos fallidos.
  - Cierre de sesiones al reiniciar la contraseña o suspender el negocio (RN-01 a RN-08).
- **Panel de administración:**
  - Lista de negocios con búsqueda, filtro y color por estado de pago (RF-02, parcial).
  - Crear negocio con nicho, plan, monto mensual y fecha del primer pago, mostrando la contraseña temporal una sola vez (RF-03, RN-41).
  - Detalle del negocio (RF-05, parcial).
  - Reinicio de contraseña (RF-04).
  - Suspender y reactivar (RF-06, parcial).
- **Administrador inicial** creado desde `ADMIN_EMAIL` y `ADMIN_PASSWORD`.
- **Errores de la API** con la forma `{"codigo", "mensaje"}` en español, incluidos los de seguridad.
- **Documentación OpenAPI** con springdoc, solo en el perfil `dev`.
- **Pruebas** (40):
  - Reglas de contraseña, generador de contraseñas temporales, usuario y cálculo del estado de pago.
  - Integración con PostgreSQL real (Testcontainers) para V01 a V07, V09 y V33, bloqueo, CSRF y datos iniciales.
- **CI con GitHub Actions** en cada push y pull request: compilación y pruebas del backend; lint y build del frontend.
- **Documentación:** `README.md`, `CLAUDE.md` y este changelog. `docs/` es local y no se versiona.

### Pendiente en fases siguientes

- **RF-02:** la columna "videos del mes" muestra "—" hasta la fase 5.
- **RF-05:** pagos, catálogo, videos y consumo aparecen como "Disponible en la fase N".
- **RF-06:** el cambio de plan llega en la fase 7.
