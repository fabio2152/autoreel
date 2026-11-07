# ADR 0017 — Dos caminos de Google para texto y video: AI Studio (activo) y Google Cloud (listo)

- **Estado:** Aceptada
- **Fecha:** 2026-11-05
- **Reemplaza en parte a:** ADR 0015 (Veo deja de ser el modelo de video activo del plan Lite).

## Contexto

Con la fase 5, el guion usaba Google AI Studio (clave de API) y el video usaba Veo en Google Cloud (cuenta de servicio): hacían falta **dos credenciales** distintas.

El usuario quiso probar rápido con **una sola**. Al crear una clave de API en la consola de Google Cloud, quedó restringida a la "API de Agent Platform" (el nombre actual de Vertex AI). No pudo agregarle la API de Gemini (AI Studio) porque su proyecto no tiene organización. Google Cloud solo acepta claves de API para `generateContent`, no para Veo, que necesita la cuenta de servicio.

Opciones con una sola credencial:

- **Solo Google AI Studio (clave de API):** Gemini para el texto y **Gemini Omni Flash** para el video (la única opción de video que queda en esa API).
  - Omni no deja elegir la duración del clip: sí la deja (`duration` de 3 a 15 s).
  - No admite prompt negativo ni temperatura.
  - Siempre genera audio.
  - No tiene plan gratuito: unos US$0,07 por segundo a 720p con `gemini-omni-1.2-flash`.
  - Su modo en segundo plano no está documentado para video.
- **Solo Google Cloud (cuenta de servicio):** Gemini para el texto (Vertex AI, endpoint `global`) y Veo para el video. Tiene más control y Veo 3.1 / Fast son estables, pero la configuración inicial es más trabajosa (proyecto, facturación, Vertex AI y cuenta de servicio).

## Decisión

El sistema queda preparado para **los dos caminos**, y la elección es un dato de la base de datos (RN-56), sin programar:

| Camino | Credencial | Texto | Video | Estado |
|---|---|---|---|---|
| Google AI Studio | `GEMINI_API_KEY` (secreto `autoreel/gemini`) | `GEMINI` (Gemini) | `GEMINI_OMNI` (Gemini Omni Flash, `gemini-omni-1.2-flash`) | **Activo** (plan Lite) |
| Google Cloud | `GOOGLE_CLOUD_CREDENCIAL_ARCHIVO` (secreto `autoreel/google-cloud`) | `GEMINI_CLOUD` (Gemini, `gemini-3.8-flash`, `global`) | `VEO` (Veo 3.1 Lite) | Listo e inactivo |

- El adaptador **`GEMINI_OMNI`** usa la API de interacciones (`POST /v1beta/interactions`) en modo **síncrono**, el que Google documenta para Omni. Guarda el resultado hasta la consulta siguiente; con `segundoPlano: true` en los parámetros usa `background` y consulta con `GET /v1beta/interactions/{id}`. Las prohibiciones van en el texto de la instrucción. El costo usa los tokens de video de la respuesta si vienen.
- El adaptador **`GEMINI_CLOUD`** reutiliza la cuenta de servicio y el token de Veo (`GoogleCloud`) y la lectura de respuestas de Gemini (`RespuestaGemini`).
- La migración **V7** crea los dos modelos nuevos, deja Veo y Gemini Cloud inactivos con el secreto `autoreel/google-cloud`, y cambia el modelo de video del plan Lite a Omni. Los videos ya creados conservan su modelo (RN-54).
- El plazo total de la generación cuenta desde el último envío de cada plano, no desde la aprobación: con un proveedor síncrono, un video puede esperar a que terminen otros.

## Consecuencias

- **Una sola clave** (AI Studio) alcanza hoy para el guion y los clips. El proyecto de la clave necesita **facturación activa**, porque Omni no tiene plan gratuito.
- Pasar a Google Cloud es un cambio de datos: activar `VEO` y `GEMINI_CLOUD`, cambiar los modelos del plan e inactivar los de AI Studio. Sin programar.
- Omni genera audio que se paga y se descarta, y la calidad sin prompt negativo debe verificarse con fotos reales. Por eso Veo queda listo como alternativa.
- La especificación (secciones 8.2 y 11.1) habla de Veo; queda actualizada por este ADR.
