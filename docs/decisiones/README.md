# Registro de decisiones de arquitectura (ADR)

Esta carpeta guarda una decisión de arquitectura por archivo, como pide la sección 23 de la especificación. Cada ADR tiene las mismas secciones:

- **Título.**
- **Estado.**
- **Fecha.**
- **Contexto.**
- **Decisión.**
- **Alternativas consideradas.**
- **Consecuencias.**

Las partes que todavía no existen en el código se marcan como "(futuro, fase N)".

## Índice

| N.º | Decisión | Estado | Fecha |
|---|---|---|---|
| [0001](0001-api-rest-con-react.md) | API REST en Spring Boot y frontend en React | Aceptada | 2026-11-03 |
| [0002](0002-monolito-modular.md) | Monolito modular en el backend | Aceptada | 2026-11-03 |
| [0003](0003-dos-modelos-ia-plantilla-fija.md) | Dos modelos de IA, con plantilla fija para el video | Aceptada | 2026-11-03 |
| [0004](0004-tareas-en-base-de-datos.md) | Tareas en segundo plano en la base de datos, en lugar de colas | Aceptada | 2026-11-03 |
| [0005](0005-credenciales-en-secrets-manager.md) | Credenciales en variables de entorno y en AWS Secrets Manager | Aceptada, modificada por 0018 | 2026-11-03 |
| [0006](0006-spring-boot-4.md) | Spring Boot 4 en lugar de Spring Boot 3 | Aceptada | 2026-11-03 |
| [0007](0007-sesion-jwt-en-cookie-y-csrf.md) | Sesión con JWT en cookie HttpOnly, CSRF para SPA y filtro propio | Aceptada | 2026-11-03 |
| [0008](0008-documentacion-local.md) | Documentación local, fuera del control de versiones | Aceptada | 2026-11-03 |
| [0009](0009-seaweedfs-como-s3-local.md) | SeaweedFS como S3 local | Aceptada | 2026-11-04 |
| [0010](0010-subida-directa-con-verificacion.md) | Subida directa al almacenamiento, con verificación y copia a la clave definitiva | Aceptada | 2026-11-04 |
| [0011](0011-contenido-del-nicho-en-json.md) | Contenido del paquete de nicho en archivos JSON | Aceptada | 2026-11-04 |
| [0012](0012-agrupacion-de-mensajes-en-el-backend.md) | Agrupación de mensajes en el backend, con el reloj del servidor | Aceptada | 2026-11-04 |
| [0013](0013-sin-temporadas-en-el-mvp.md) | Sin fechas especiales ni temporadas en el flujo del video del MVP | Aceptada | 2026-11-04 |
| [0014](0014-procesador-de-guiones-con-reclamo.md) | Un solo procesador de guiones, con reclamo en la base de datos | Aceptada, modificada por 0021 | 2026-11-04 |
| [0015](0015-veo-en-google-cloud.md) | Veo en Google Cloud (Vertex AI) como modelo de video | Aceptada, reemplazada en parte por 0017 | 2026-11-04 |
| [0016](0016-composicion-con-ffmpeg.md) | Composición del video con FFmpeg como proceso externo | Aceptada; sin acercamiento de respaldo (0020); fundidos y cierre nuevo (0021) | 2026-11-04 |
| [0017](0017-dos-caminos-de-google.md) | Dos caminos de Google para texto y video: AI Studio (activo) y Google Cloud (listo) | Aceptada | 2026-11-05 |
| [0018](0018-claves-desde-el-panel.md) | Las claves de IA se pegan en Ajustes, pero viven fuera de la base de datos | Aceptada | 2026-11-06 |
| [0019](0019-entrar-como-el-negocio.md) | El administrador puede entrar como un negocio | Aceptada | 2026-11-06 |
| [0020](0020-sin-respaldo-sin-ia-para-los-clips.md) | Sin respaldo sin IA para los clips | Aceptada, modificada por 0021 | 2026-11-06 |
| [0021](0021-calidad-del-video.md) | Calidad del video: guion publicitario, cierre de la IA, cámara 3D y fundidos (sin guion de respaldo) | Aceptada | 2026-11-12 |

Los ADR 0001 a 0005 son los mínimos que exige la sección 23. Los ADR 0006 a 0008 registran decisiones tomadas al iniciar la fase 1. Los ADR 0009 a 0011 registran las de la fase 2 (almacenamiento local, subida de archivos y paquete de nicho). Los ADR 0012 y 0013 registran las de la fase 3 (agrupación de mensajes del chat y la decisión del usuario de dejar fuera del MVP las fechas especiales y las temporadas). El ADR 0014 registra la de la fase 4 (cómo se escribe el guion en segundo plano sin procesar dos veces un video ni dejarlo atascado). Los ADR 0015 y 0016 registran las de la fase 5 (la decisión del usuario de usar Veo en Google Cloud, tras el cierre de los Veo 3.1 *preview* en la API de Gemini, y cómo se compone el video con FFmpeg). El ADR 0017 registra la decisión del usuario de probar todo con Google AI Studio (Gemini y Gemini Omni Flash), con Google Cloud listo como alternativa. Los ADR 0018 y 0019 registran las de la fase 7, pedidas al revisar el panel: pegar las claves en Ajustes sin guardarlas en la base de datos, y entrar como un negocio para darle soporte. El ADR 0020 registra la decisión del usuario, al revisar el flujo del negocio, de quitar el respaldo sin IA de los clips. El ADR 0021 registra las decisiones del usuario al revisar un video real (rama `mejora-calidad-video`): guion publicitario sin copiar al negocio, cierre escrito por la IA, sin guion de respaldo (reintentos automáticos y "Reintentar" sin gastar el video del día), movimientos de cámara 3D con el prompt en el paquete de nicho, y fundidos entre planos.

## ADR previstos

- **Fechas especiales y temporadas en el chat** (V13 y RF-31): si se reactivan después del MVP, un ADR nuevo reemplazará al 0013 (futuro, después del MVP).

## Cómo agregar un ADR

1. Copia el formato de un ADR existente y usa el siguiente número libre, con cuatro dígitos y un nombre corto en minúsculas con guiones. Por ejemplo, `0022-nombre-de-la-decision.md`.
2. Escribe el contexto y la decisión en español. Describe lo que existe en el código, con sus nombres reales, y marca como futuro lo que aún no existe.
3. Si una decisión reemplaza a otra, cambia el estado de la anterior a "Reemplazada por ADR NNNN" en lugar de borrarla.
4. Agrega la fila en el índice de este archivo.
5. Inclúyelo en el commit del cambio: desde el PR #11, `docs/` está en el repositorio (antes era local, ADR 0008).
