# ADR 0006 — Spring Boot 4 en lugar de Spring Boot 3

- **Estado:** Aceptada
- **Fecha:** 2026-11-03

## Contexto

La especificación (sección 12.2) indica "Spring Boot 3 (versión estable actual)". Pero cuando empezó la fase 1, en noviembre de 2026:

- La línea 3.x de Spring Boot había salido del soporte libre (OSS). Sus correcciones de seguridad ya solo llegan con el soporte comercial.
- Spring Initializr (`start.spring.io`) ya no ofrecía versiones 3.x para generar proyectos nuevos.
- La versión estable actual era la **4.1.2**.

Empezar un proyecto que aspira a ser un SaaS real con una versión sin parches de seguridad gratuitos sería deuda técnica desde el primer día. La propia especificación pide "la versión estable actual", así que esta decisión respeta su intención.

## Decisión

El backend usa **Spring Boot 4.1.2** con **Java 21** y el **Maven wrapper** (`mvnw` / `mvnw.cmd`), porque Maven no está instalado en el equipo de desarrollo. Se generó con Spring Initializr (`type=maven-project`, `bootVersion=4.1.2`, `javaVersion=21`, `groupId=com.autoreel`).

**Lo que trae `backend/pom.xml`:**

- **Padre:** `spring-boot-starter-parent` 4.1.2.
- **Starters modulares de Boot 4:** `spring-boot-starter-webmvc`, `-security`, `-data-jpa`, `-validation`, `-flyway` y `-actuator`, con sus equivalentes `*-test`.
- **Base de datos:** `flyway-database-postgresql` y `postgresql`.
- **JWT:** `spring-security-oauth2-jose`, gestionada por el BOM. Solo se usan los `NimbusJwtEncoder` y `NimbusJwtDecoder`; ver ADR 0007.
- **Documentación de la API:** `springdoc-openapi-starter-webmvc-ui` 3.2.0, con la versión fijada en la propiedad `springdoc.version`.
- **Pruebas:** `spring-boot-testcontainers`, `testcontainers-junit-jupiter` y `testcontainers-postgresql`, con la imagen `postgres:17-alpine`.

**Lo que cambia respecto de Boot 3 y se tuvo en cuenta en el código:**

- **Spring Security 7:** `csrf(c -> c.spa())` para SPA y la configuración con lambdas (ADR 0007).
- **Jackson 3:** el paquete es `tools.jackson.databind`; por ejemplo, `RespuestasDeSeguridad` usa `tools.jackson.databind.ObjectMapper`. La prueba `GestionNegociosIntegracionTest.lasSemillasDePlanesYModelosSeLeenConSuJson` lee las semillas jsonb para confirmar que Jackson 3 y jsonb funcionan con `@JdbcTypeCode(SqlTypes.JSON)`.
- **Paquetes de autoconfiguración reorganizados:** por ejemplo, `application.yml` excluye `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`.
- **Testcontainers 2:** la clase es `org.testcontainers.postgresql.PostgreSQLContainer` y la conexión se configura con `@ServiceConnection`.

**El resto del stack:** PostgreSQL 17 en `docker compose` (puerto configurable con `POSTGRES_PORT`), Flyway con `ddl-auto=validate` y CI en GitHub Actions con Temurin 21 (`./mvnw -B verify`).

## Alternativas consideradas

- **Spring Boot 3.5.x**, la última de la línea 3. Coincide al pie de la letra con la especificación y tiene más ejemplos en internet, pero ya no recibe parches libres y no se puede generar desde Initializr. Migrar a la 4 después costaría más que empezar en ella.
- **Gradle en lugar de Maven.** Es válido, pero no aporta ventajas claras para este proyecto, y con el wrapper de Maven basta tener Java instalado.
- **Java 25.** Spring Boot 4 lo soporta, pero Java 21 es la LTS instalada y verificada en el equipo y en el CI.

## Consecuencias

- **Positivas:**
  - Se parte de una versión con soporte y parches de seguridad.
  - No habrá una migración mayor pendiente durante el MVP.
  - Se accede a las mejoras de Spring Security 7, como `csrf().spa()`.
- **Negativas:**
  - Hay menos documentación y ejemplos que para Boot 3, y algunas guías o respuestas en internet usan nombres de paquete o de starters antiguos (Jackson 2, `spring-boot-starter-web`).
  - Las librerías de terceros deben ser compatibles con Boot 4 y Jackson 3. Por eso springdoc está en la versión 3.2.0, y en las fases siguientes habrá que verificar el AWS SDK v2 y otras dependencias.
- **Desviación documentada:** la sección 12.2 de la especificación dice "Spring Boot 3". Esta decisión la reemplaza, y `CLAUDE.md` ya indica "Spring Boot 4".
