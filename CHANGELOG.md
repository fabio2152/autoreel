# Changelog

Cambios de AutoReel por fase. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [Fase 2 — Negocio] - 2026-11-04

### Agregado

- **Paquete de nicho Pastelería** (`PaqueteNicho` / `PaquetePasteleria`): estilos, preguntas del onboarding y temporadas en `resources/nichos/pasteleria/`, validados al arrancar. Cálculo de temporadas vigentes y fechas especiales próximas (15 días), listo para el chat de la fase 3.
- **Almacenamiento de archivos** (`AlmacenDeArchivos` → `S3Almacen`):
  - Subida directa desde el navegador con URL firmada (tipo y tamaño exactos).
  - Verificación del contenido real (formato, medidas, transparencia del logo, orientación de la cámara, límites contra imágenes enormes o animadas).
  - Copia a una clave definitiva que nadie puede sobrescribir.
  - SeaweedFS en docker compose para desarrollo; Amazon S3 en producción sin cambiar código.
- **Onboarding y perfil** (RF-16, RF-17, RN-09): asistente de tres pasos en pantalla completa (marca, negocio, toque), que se retoma donde quedó. Logo PNG, dos colores con "Tomar del logo", estilo, preguntas del paquete, foto del local opcional. Edición en Ajustes.
- **Catálogo de productos** (RF-18, RN-10 a RN-13): nombre, descripción, temporadas y tres fotos (entero, detalle, porción) con imagen de ejemplo e indicaciones. Productos incompletos marcados; archivar y reactivar.
- **Fotos preparadas en el navegador:** se aplica la orientación de la cámara, se reducen a 2560 px y se quitan los metadatos (GPS).
- **Ajustes** del negocio: perfil y cambio de contraseña (RF-15).
- **Panel de administración:** el detalle del negocio muestra su perfil y su catálogo (RF-05).
- **Negocio de ejemplo** en el perfil `dev` (sección 16), con imágenes de relleno generadas.
- **Migración `V3`:** `perfil_negocio`, `producto` y `foto_producto`.
- **Pruebas** (90 en total): paquete de nicho y temporadas, validación de respuestas, verificador de imágenes, adaptador S3 contra SeaweedFS real, e integración para V08, V11, V12, aislamiento de archivos entre negocios, onboarding y catálogo.

### Pendiente en fases siguientes

- **V10, V13 y V14** se verifican con el chat (fase 3); la regla RN-09 ya está guardada en `onboardingCompleto`.
- **RN-14** (producto subido desde el chat sin guardarlo) llega con el chat.
- **RN-15** (detección de rostros con Rekognition) en la fase 9.
- **Limpieza de subidas abandonadas:** regla de expiración del bucket en la fase 8.

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
- **Endurecimiento del inicio de sesión:**
  - Los correos inexistentes se bloquean igual que las cuentas reales y responden en el mismo tiempo, para no revelar qué correos están registrados.
  - Los intentos simultáneos se cuentan uno a uno (bloqueo de fila).
  - El límite de 72 bytes de BCrypt se valida como regla de contraseña.
  - Los errores estándar de Spring MVC conservan su estado HTTP.
- **Frontend:** si la sesión termina en el servidor, la aplicación vuelve al inicio de sesión; los fallos de red se muestran con un mensaje en español y la opción de reintentar.
- **Pruebas** (45):
  - Reglas de contraseña, generador de contraseñas temporales, usuario y cálculo del estado de pago.
  - Integración con PostgreSQL real (Testcontainers) para V01 a V07, V09 y V33, bloqueo y su duración, desbloqueo al reiniciar, correos inexistentes, CSRF y datos iniciales.
- **CI con GitHub Actions** en cada push y pull request: compilación y pruebas del backend; lint y build del frontend.
- **Documentación:** `README.md`, `CLAUDE.md` y este changelog. `docs/` es local y no se versiona.

### Pendiente en fases siguientes

- **RF-02:** la columna "videos del mes" muestra "—" hasta la fase 5.
- **RF-05:** pagos, catálogo, videos y consumo aparecen como "Disponible en la fase N".
- **RF-06:** el cambio de plan llega en la fase 7.
