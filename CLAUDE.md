# AutoReel — Reglas de trabajo

AutoReel es una plataforma que convierte las fotos de los productos de un negocio en Reels de 15 segundos con IA y los publica en Instagram. El MVP atiende un solo nicho: pastelerías.

- Especificación completa: `docs/especificacion.md`.
- Contexto, visión y hoja de ruta: `docs/contexto-vision.md`.
- Plan de desarrollo por fases: `docs/plan-de-desarrollo.md`.

`docs/` y `.claude/` son **locales**: están en `.gitignore` y no se suben al repositorio, pero la documentación se mantiene al día igual.

## Reglas de la especificación (sección 0)

1. **Proyecto nuevo**, en un repositorio con dos carpetas: `backend/` (Java, Spring Boot) y `frontend/` (React).
2. **Sin TDD.** Desarrollo normal. Las pruebas automatizadas (sección 18) son pocas, se escriben después del código y solo cubren la lógica crítica.
3. **Respeta la arquitectura de la sección 12:** monolito modular en el backend, con los proveedores externos (IA, almacenamiento, Instagram, composición de video) detrás de interfaces, para poder cambiarlos sin tocar el resto.
4. **Los planes y los modelos de IA no se escriben en el código:** se leen de la base de datos. Agregar un plan o un modelo nuevo de un proveedor ya integrado no debe requerir programar.
5. **Todo lo propio del nicho** (preguntas, plantilla, movimientos, vocabulario, temporadas) vive en el **paquete de nicho Pastelería** (sección 7), nunca disperso por el código.
6. **Nunca escribas claves en el código ni en la base de datos.** Las credenciales se leen de variables de entorno en desarrollo y de AWS Secrets Manager en producción. Los modelos de IA solo guardan el nombre del secreto que usan (RN-55).
7. **La base de datos se versiona con Flyway.** No uses `ddl-auto=update` (se usa `validate`).
8. **Código claro y simple:** nombres de dominio en español, sin abstracciones que no se usen.
9. **Trabaja por fases**, en el orden de la sección 19, y al terminar cada fase verifica sus casos de la sección 17.
10. Commits por funcionalidad (por ejemplo, `feat: catalogo de productos`).
11. **Mantén la documentación de la sección 23** al día: cada fase termina con su documentación actualizada.
12. Si algo no está definido en la especificación, pregunta antes de decidir.

## Reglas del repositorio

- **Una rama por fase:** `fase-1-base`, `fase-2-negocio`, `fase-3-chat`, `fase-4-guion`, `fase-5-generacion`, `fase-6-publicacion`, `fase-7-facturacion`, `fase-8-despliegue`, `fase-9-opcional`. Cada una se integra a `main` mediante un pull request. Nunca hagas push directo a `main`.
- **Nunca agregues** "Co-Authored-By: Claude" ni "Generated with Claude Code" (ni ninguna otra atribución a Claude) en commits ni en pull requests.
- **Nunca hagas commit** de archivos `.env` ni de credenciales. Solo se versiona `.env.example`, con valores de ejemplo.
- Cada fase termina con: casos de verificación revisados, `CHANGELOG.md` y documentación de `docs/` actualizados, y el pull request abierto con el CI en verde.

## Estructura

```text
backend/    Spring Boot 4 (Java 21, Maven wrapper), paquete base com.autoreel
frontend/   React + TypeScript + Vite + Tailwind CSS
docs/       documentación local (no versionada)
```

Backend: monolito modular por módulos de negocio (`auth`, `admin`, `facturacion`, `ia`, `negocio`, `nicho`, ...), cada uno con las capas `api`, `aplicacion`, `dominio` e `infraestructura`. Las respuestas de error siempre tienen la forma `{"codigo": "...", "mensaje": "..."}` con mensajes en español.

## Comandos

```bash
# Base de datos local (usa .env de la raíz)
docker compose up -d

# Backend (desde backend/)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev      # Git Bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" # PowerShell
./mvnw verify                                              # compila y prueba (Testcontainers, requiere Docker)

# Frontend (desde frontend/)
npm run dev     # http://localhost:5173, con proxy /api -> http://localhost:8080
npm run lint
npm run build
```

El perfil `dev` carga `../.env`. El perfil `test` (pruebas) usa valores fijos de `src/test/resources/application-test.yml`.
