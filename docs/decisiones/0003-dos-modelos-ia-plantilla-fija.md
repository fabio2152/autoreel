# ADR 0003 — Dos modelos de IA, con plantilla fija para el video

- **Estado:** Aceptada
- **Fecha:** 2026-11-03

## Contexto

La IA de video usada sin control produce manos deformes, letras ilegibles y productos que cambian de forma (`docs/contexto-vision.md`, sección 2). Un negocio no puede publicar eso. A la vez, las respuestas del chat son datos sueltos ("fresas frescas, crema batida, S/ 65") que hay que convertir en frases de antojo, descripción y hashtags con el tono del negocio (especificación, sección 8.0).

Ningún modelo hace las dos cosas:

- **El modelo de video solo anima una imagen.** Recibe una foto y una instrucción de movimiento, y devuelve un clip.
- **El modelo de texto redacta.** No genera video.

Además, los precios y la calidad de los modelos cambian cada pocos meses. Por eso los modelos no pueden quedar escritos en el código (regla 4 de la sección 0, RN-56).

## Decisión

1. **Dos modelos con trabajos distintos:**
   - **El modelo de texto** (Gemini en el plan Lite) arma el guion en JSON: los textos de cada plano, la elección de foto y de movimiento, la descripción y los hashtags (sección 8.1).
   - **El modelo de video** (Veo 3.1 Lite en el plan Lite) anima cada foto en modo imagen a video, en 9:16 y con clips de 4 segundos (sección 8.2).
2. **Plantilla fija.**
   - El modelo de texto **no escribe libremente** la instrucción para el modelo de video: solo elige un movimiento de la lista permitida del paquete de nicho (sección 7.5).
   - El sistema arma la instrucción con una plantilla fija por movimiento, en inglés, más las prohibiciones.
   - Los textos en pantalla, el logo, el precio y la música los pone la composición con FFmpeg, nunca la IA.
3. **El guion se valida antes de usarse:**
   - Cantidad de planos, listas permitidas, longitudes, palabras prohibidas y que el precio coincida con lo que escribió el usuario.
   - Si falla, se reintenta hasta 2 veces y luego se usa un guion de respaldo sin IA (RN-21 a RN-25).
4. **Los modelos son datos, no código.**
   - Cada modelo es una fila de la tabla `modelo_ia`, con su `adaptador` (la implementación en el código: `GEMINI`, `VEO`), su `identificador_modelo` (el nombre exacto que exige la API del proveedor), sus `parametros` (jsonb) y su `nombre_secreto`.
   - Cada plan apunta a un modelo de texto y a uno de video (`plan.modelo_texto_id`, `plan.modelo_video_id`).
   - Agregar un modelo de un proveedor ya integrado no requiere programar. Un proveedor nuevo requiere programar su adaptador (RN-56).

**Lo que existe en la fase 1:** solo los datos, en `V2__modelos_ia_planes_y_suscripciones.sql`.

| Fila de `modelo_ia` | `tipo` | `adaptador` | `identificador_modelo` | Costo | `nombre_secreto` |
|---|---|---|---|---|---|
| Gemini | TEXTO | `GEMINI` | `gemini-3.8-flash` | 0,01 USD POR_SOLICITUD | `autoreel/gemini` |
| Veo 3.1 Lite | VIDEO | `VEO` | `veo-3.1-lite-generate-001` | 0,05 USD POR_SEGUNDO | `autoreel/gemini` |

- **Plan Lite:** 1 video por día, 3 regeneraciones por video, los dos modelos anteriores, `permite_personas = false` y `opciones_produccion_permitidas` con música, texto completo, sin voz en off, solo producto y sin personas (sección 8.6).
- **Cómo se leen en el código:** las entidades `ModeloIA` y `Plan` y sus repositorios. `GET /api/admin/planes` expone los planes en modo de solo lectura.
- **Aviso sobre el proveedor de video:** Google cerró el identificador *preview* de Veo en la API de Gemini el 22/10/2026. El identificador sembrado es un dato editable. El proveedor real del video (API de Gemini o Vertex AI) se decidirá con su propio ADR en la fase 5.

**Lo que queda para más adelante:**

- La fábrica de proveedores, el puerto `Guionista` → `GeminiGuionista`, el validador y el guion de respaldo (futuro, fase 4).
- El puerto `GeneradorDeClips` y su adaptador de video, las plantillas de movimiento en inglés, `FfmpegComponedor` y el respaldo con acercamiento lento (futuro, fase 5).
- La pantalla de integraciones, con las acciones probar, activar y desactivar (futuro, fase 7).

## Alternativas consideradas

- **Un solo modelo de video que reciba los datos del chat tal cual.** No produce textos de antojo, ni descripción, ni hashtags. Además, el texto generado dentro del video suele salir deforme.
- **El modelo de texto escribe libremente la instrucción de video.** Aprovecha más la creatividad, pero abre la puerta a pedir personas, manos o texto, que es justo lo que sale mal. Se cambió por la elección dentro de una lista cerrada.
- **Modelos escritos en el código o en variables de entorno.** Es más simple, pero cambiar de modelo o crear un plan Pro exigiría programar y desplegar.
- **Video generado desde texto, sin foto.** La IA inventaría el producto, con riesgo de publicidad engañosa. Contradice el principio "la IA no inventa el producto".

## Consecuencias

- **Positivas:**
  - Resultados predecibles.
  - El video siempre se entrega, gracias a los respaldos.
  - Los proveedores se pueden intercambiar.
  - Se pueden crear planes superiores (por ejemplo, Veo 3.1 Fast) y beneficios temporales sin programar.
  - Cada video guardará el plan y los modelos con los que se generó (RN-54, futuro, fase 5).
- **Negativas:**
  - Hay que mantener dos integraciones lógicas, aunque en el plan Lite comparten la misma API y la misma clave.
  - La creatividad queda limitada a lo que permite el paquete de nicho.
  - La validación del guion y los respaldos agregan código.
- **Riesgo abierto:** la disponibilidad del identificador de Veo. Se mitiga porque el identificador es un dato editable y porque el proveedor se decide con un ADR en la fase 5.
