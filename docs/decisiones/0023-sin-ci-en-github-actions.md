# ADR 0023: Sin CI en GitHub Actions

- **Estado:** Aceptada
- **Fecha:** 2026-11-25

## Contexto

Desde la fase 1 el repositorio tenía un workflow de GitHub Actions (`.github/workflows/ci.yml`) que, en cada push y pull request, compilaba y probaba el backend (`./mvnw -B verify` con FFmpeg y Testcontainers) y hacía el lint y el build del frontend. No desplegaba nada (no había CD).

Desde el 2026-11-05 GitHub bloquea las Actions de la cuenta por un problema de facturación, y el usuario no quiere registrar una tarjeta. Los pull request se venían integrando sin esperar el CI, después de verificar en local, y cada push generaba una ejecución fallida.

## Decisión

Por pedido del usuario (2026-11-25), se borra `.github/workflows/ci.yml`: no hay CI ni CD en GitHub Actions.

Antes de integrar un pull request se corre en local:

- backend: `./mvnw verify` (necesita Docker y FFmpeg);
- frontend: `npm run lint` y `npm run build`.

Se mantiene el trabajo por ramas y pull requests.

## Consecuencias

- Sin ejecuciones fallidas ni avisos de GitHub en cada push.
- La verificación depende de correr las pruebas en local antes de integrar.
- Si se quiere CI o despliegue automático más adelante (por ejemplo, al desplegar en AWS o Google Cloud), se recupera el workflow del historial de git y se agrega un ADR nuevo.
