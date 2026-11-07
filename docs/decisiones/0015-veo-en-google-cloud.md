# ADR 0015 — Veo en Google Cloud (Vertex AI) como modelo de video

- **Estado:** Aceptada; reemplazada en parte por el ADR 0017 (el plan Lite usa Gemini Omni Flash; Veo queda listo e inactivo)
- **Fecha:** 2026-11-04

## Contexto

La especificación fija **Veo 3.1 Lite** como modelo de video del plan Lite (RF-11, RN-46, sección 11.1): imagen a video, 9:16 y clips de 4 segundos. También supone que Gemini y Veo son **una sola integración**, con la misma API de Google y la misma clave (sección 11.1), y por eso la semilla de la fase 1 dejó a los dos modelos con el secreto `autoreel/gemini` (sección 16).

Al empezar la fase 5 eso ya no se cumplía:

- **Google cerró los Veo 3.1 *preview* de la API de Gemini el 22/10/2026.** En esa API, para video, solo queda **Gemini Omni Flash**. El riesgo ya estaba anotado desde la fase 1 (`docs/guias/nuevo-proveedor-ia.md`, sección 2.2) y en el índice de ADR previstos.
- **En Google Cloud** (Vertex AI, hoy "Gemini Enterprise Agent Platform") Veo sigue disponible. Según la documentación oficial consultada el 2026-11-04 (`docs/plan-fase-5.md`, sección 2):
  - `veo-3.1-generate-001` y `veo-3.1-fast-generate-001` son **GA**; `veo-3.1-lite-generate-001` está en **vista previa**.
  - Duraciones de 4, 6 u 8 s, relación 9:16, `negativePrompt` y `generateAudio` opcionales; sin `storageUri`, el clip vuelve en la misma respuesta.
  - Solo está en **`us-central1`**.
  - **No acepta clave de API para Veo:** pide una **cuenta de servicio** (un JSON con clave privada), distinta de `GEMINI_API_KEY`.
  - El precio por segundo de Lite no se pudo confirmar en la página oficial.
- **Todavía no hay credencial de Google Cloud.** El adaptador debía poder construirse y probarse sin ella, y el video debía entregarse igual (RNF-07, plan de respaldo de la sección 8.5).

Había que decidir qué proveedor y qué API usa el adaptador `VEO` en el MVP.

## Decisión

**Decisión del usuario (2026-11-04): el modelo de video es Veo 3.1 en Google Cloud (Vertex AI), con una cuenta de servicio propia. El plan Lite sigue con Veo 3.1 Lite (`veo-3.1-lite-generate-001`, en vista previa), como dato editable.**

1. **Puerto y adaptador.** El módulo `ia` tiene el puerto `GeneradorDeClips` (`ia/dominio`), con `adaptador()`, `iniciar(modelo, credencial, SolicitudDeClip) → idTarea` y `consultar(modelo, credencial, idTarea) → EstadoDeClip` (`EnCurso`, `Listo`, `Fallido`). Lo implementa `VeoGeneradorDeClips` (`ia/infraestructura`, adaptador `VEO`), y `FabricaDeProveedores.generadorDeClips(modelo)` lo entrega solo si el modelo es `VIDEO`, `ACTIVO` y `POR_SEGUNDO`.
2. **API de Vertex AI.**
   - La tarea se crea con `POST https://{region}-aiplatform.googleapis.com/v1/projects/{proyecto}/locations/{region}/publishers/google/models/{identificador_modelo}:predictLongRunning`, con la foto en base64, la instrucción del movimiento en inglés y `parameters` con `aspectRatio`, `durationSeconds`, `sampleCount: 1`, `negativePrompt` (las prohibiciones de `movimientos.json`), `personGeneration: "dont_allow"`, `generateAudio`, `resolution` y `resizeMode`.
   - Se consulta con `:fetchPredictOperation` y `{operationName}`. Con `done`, el clip llega en `response.videos[0].bytesBase64Encoded`. Un `error`, un `raiMediaFilteredCount` mayor que 0 o una respuesta sin clip se devuelven como `Fallido`.
   - La URL y los tiempos están en `autoreel.ia.veo` (`PropiedadesVeo`: `url` con `{region}`, `url-token`, 5 s de conexión y 60 s de lectura, porque el clip viene en base64). Las pruebas apuntan a `ServidorVeoSimulado`.
3. **Todo sale de la fila de `modelo_ia`** (RN-56, regla 4 de la sección 0). La migración `V6__generacion.sql` deja el modelo Veo 3.1 Lite con `nombre_secreto = 'autoreel/veo'` y `parametros = {"region": "us-central1", "duracionClipSegundos": 4, "relacionAspecto": "9:16", "resolucion": "720p", "generarAudio": false, "ajuste": "crop"}`. El adaptador no tiene ningún identificador ni región escritos, salvo los valores por defecto cuando falta un parámetro.
4. **Credencial: una cuenta de servicio, nunca en el código ni en la base** (regla 6, RN-55).
   - El secreto `autoreel/veo` es el JSON completo de la cuenta. En desarrollo, `SecretosDeConfiguracion` lo lee de un **archivo**: `autoreel.secretos-en-archivo."[autoreel/veo]": ${VEO_CREDENCIAL_ARCHIVO:}`, porque un JSON con saltos de línea no cabe bien en el `.env`. Si la ruta está vacía o el archivo no existe, devuelve vacío y deja un log solo con la ruta.
   - `.gitignore` excluye `/credenciales/` y `*service-account*.json`.
   - `TokenDeGoogle` firma un JWT RS256 con la `private_key` del JSON (Nimbus, que ya estaba en el proyecto), con el alcance `cloud-platform`, y lo cambia por un token de acceso en `oauth2.googleapis.com/token`. Guarda un token por cuenta y `private_key_id` hasta 1 minuto antes de vencer, y lo descarta ante un 401 o 403. El proyecto sale del `project_id` del JSON.
   - Un JSON sin `private_key`, `client_email` o `project_id`, o cualquier error del endpoint de tokens, es `CREDENCIAL_INVALIDA`. Ningún mensaje incluye la clave.
5. **Sin credencial, el video se entrega igual.** Si falta el secreto, o el modelo no está activo, cada plano usa el **acercamiento de FFmpeg** sobre la foto (respaldo, sección 8.5), con motivo `SIN_CREDENCIAL` o `MODELO_INACTIVO`, sin llamar ni cobrar, y un aviso en el registro (RN-57). En la revisión, "Regenerar" y "Reintentar con IA" se ocultan (`iaDisponible`) y el backend responde 409 `IA_NO_DISPONIBLE`.
6. **Costo (RN-32).** Cada clip listo registra `parametros.duracionClipSegundos × costo_referencial` en `registro_costo` (concepto `CLIP`). También se registra un clip ya enviado que termina en respaldo por tiempo, porque la operación ya se cobra. El costo de referencia sigue siendo el de la semilla (0,05 USD por segundo, sección 11.1) hasta confirmarlo.

## Alternativas consideradas

- **Gemini Omni Flash en la API de Gemini.** Mantenía una sola integración y la misma `GEMINI_API_KEY` que el guion, como suponía la sección 11.1. Pero no es Veo: cambiaba el modelo que la especificación fija para el plan Lite (RN-46) y obligaba a rehacer las decisiones del video (instrucciones, duración, costo) sobre un modelo distinto. El usuario eligió Veo.
- **Veo 3.1 Fast o Veo 3.1 (GA) desde el inicio.** No dependen de una vista previa, pero cuestan más: la especificación calcula 1,44 USD por video con Fast a 1080p, contra 0,60 con Lite (sección 15), y los reserva para los planes Pro y Premium. Quedan a un cambio de datos (`docs/guias/nuevo-proveedor-ia.md`, caso A).
- **La biblioteca cliente de Google Cloud** para Vertex AI y sus tokens. Resuelve la autenticación sola, pero suma dependencias grandes para dos llamadas HTTP y un JWT que Nimbus ya firma. `RestClient` y `TokenDeGoogle` mantienen el adaptador igual de simple que `GeminiGuionista` y fácil de probar con un servidor simulado.
- **Otro proveedor de video** (por ejemplo, Runway). Exige programar otro adaptador y otro contrato, y la especificación lo deja para planes superiores (sección 15). El puerto `GeneradorDeClips` lo permite más adelante.
- **Solo el acercamiento de FFmpeg, sin modelo de video.** Ya funciona como respaldo y no cuesta nada, pero quita el valor principal del producto: las fotos animadas con IA.

## Consecuencias

- **Positivas:**
  - El MVP usa el modelo que pide la especificación (Veo 3.1 Lite) por una vía que Google mantiene.
  - Cambiar a Veo 3.1 Fast o a Veo 3.1, que son GA, es crear una fila en `modelo_ia` con el mismo adaptador y asignarla a un plan, sin programar (RN-56).
  - El adaptador se prueba sin credencial ni costo: `VeoGeneradorDeClipsTest` cubre contra `ServidorVeoSimulado` el token, `predictLongRunning`, `fetchPredictOperation`, el filtro de contenido, el 401 y los 5xx, y `GeneracionIntegracionTest` usa el mismo servidor para el flujo completo.
  - Sin credencial, o si Veo falla, el negocio igual recibe su video de 15 s.
- **Negativas y riesgos:**
  - **Ya no es una sola integración:** Gemini usa una clave de API (`autoreel/gemini`) y Veo una cuenta de servicio (`autoreel/veo`). Hay dos credenciales que crear, guardar y rotar. La sección 11.1 de la especificación quedó desactualizada en ese punto.
  - **Cuenta de servicio pendiente.** Falta crear el proyecto de Google Cloud con Vertex AI y facturación, y la cuenta con permiso para llamar a Veo. Hasta entonces, todos los planos usan el acercamiento de FFmpeg y el adaptador solo se probó contra el servidor simulado, no contra Google.
  - **Solo `us-central1`.** La latencia desde Lima y desde la región de AWS del despliegue es mayor, y el clip viaja en base64 dentro de la respuesta. Si Google habilita otras regiones, basta con cambiar `parametros.region`.
  - **Veo 3.1 Lite está en vista previa en Google Cloud.** Google puede cambiarlo o cerrarlo, como hizo con los *preview* de la API de Gemini. Si pasa, el identificador es un dato editable y Fast (GA) es el reemplazo inmediato, a mayor costo.
  - **Costo sin confirmar.** El precio de Lite en Vertex no se confirmó en la página oficial; `registro_costo` usa el `costo_referencial` de la semilla. Se revisa al llegar el panel de integraciones (futuro, fase 7).
  - **El JSON de la cuenta es una clave privada.** En desarrollo vive en un archivo fuera del repositorio; en producción irá a AWS Secrets Manager con el nombre `autoreel/veo` (futuro, fase 8; `docs/despliegue-aws.md`).
  - "Probar conexión" para Veo llega con la pantalla de integraciones (futuro, fase 7).
- **Cuándo revisar esta decisión:** si Google cierra o cambia Veo 3.1 Lite en Vertex, si el precio confirmado no cabe en el plan Lite, si Veo llega a regiones más cercanas o si se agrega otro proveedor de video.
