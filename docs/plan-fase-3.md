# Fase 3 — Chat: diseño

Rama `fase-3-chat`. Alcance de la sección 19: **conversación guiada con la agrupación de mensajes y la detección de preguntas**.
Requisitos: RF-19, RF-20; reglas RN-09, RN-14, RN-16 a RN-20; sección 7.3.
Casos de la sección 17 que se cierran aquí: **V10, V11 (resto), V13, V14, V15, V16, V17**.

## 1. Guion del chat en el paquete de nicho (RN-16)

- Archivo `resources/nichos/pasteleria/chat.json`, leído y validado al arrancar, expuesto por `PaqueteNicho.guionDelChat()`.
- Cada paso (`PasoChat`): `id`, `texto` (lo que dice el sistema), `tipo`, `obligatoria`, `opciones` (para botones) y `ayuda`.
- Tipos de paso:
  - `BOTONES`: el usuario toca una opción (respuesta inmediata, sin agrupar).
  - `FECHA_ESPECIAL`: botones con las fechas especiales vigentes o próximas (15 días), calculadas con `PaqueteNicho`.
  - `PRODUCTO`: dos caminos — "Elegir del catálogo" (carrusel) o "Subir uno nuevo hoy" (formulario con nombre, descripción y tres fotos, y la pregunta "¿Lo guardo en tu catálogo?").
  - `TEXTO`: respuesta libre, con agrupación de 4 s y detección de preguntas.
- Pasos de Pastelería (7.3):
  1. `tipoVideo` (BOTONES): Producto estrella, Promoción, Fecha especial, Tortas por encargo, Recién salido del horno.
  2. `fechaEspecial` (FECHA_ESPECIAL): solo si `tipoVideo = FECHA_ESPECIAL`: "¿Para qué fecha?".
  3. `producto` (PRODUCTO): "¿Qué producto mostramos?".
  4. `especial` (TEXTO, obligatoria): "¿Qué lo hace especial hoy?", con la descripción del producto como sugerencia.
  5. `precio` (TEXTO, opcional; obligatoria si `tipoVideo = PROMOCION`, RN-20): acepta "no".
  6. `extra` (TEXTO, opcional): "¿Algo más que quieras que se diga?".
  7. Cierre fijo: "¡Listo! Estoy preparando tu propuesta…". La conversación queda `COMPLETA`. En la fase 3 la pantalla muestra que la propuesta llegará con la fase 4 (guion y storyboard).
- Mensajes fijos del paquete: pregunta del usuario (RN-18), respuesta muy corta, respuesta muy larga, aviso lateral, saludo de temporada ("Es verano: estos suelen funcionar mejor").

## 2. Modelo (migración `V4__chat.sql`)

- `conversacion`: `id`, `negocio_id`, `estado` (`EN_CURSO`, `COMPLETA`, `ABANDONADA`), `paso_actual`, `respuestas jsonb`, `fecha_inicio`, `fecha_actualizacion`. Índice único parcial `(negocio_id) where estado = 'EN_CURSO'` (RN-19). La columna `video_id` llega en la fase 4.
- `mensaje_chat`: `id`, `conversacion_id`, `autor` (`SISTEMA`, `USUARIO`), `texto`, `fecha_hora`, `procesado` (los mensajes de texto del usuario se marcan al agruparse).
- `producto.en_catalogo boolean not null default true` (RN-14): un producto subido desde el chat sin aceptar guardarlo queda con `false`; no aparece en el catálogo ni en el carrusel, pero existe para el video.

## 3. Agrupación de mensajes (RN-17, V15) en el backend

- Cada mensaje de texto del usuario se guarda al instante (`procesado=false`) y se ve en el chat.
- Una respuesta agrupada se cierra cuando pasan **4 s** sin mensajes nuevos del usuario. El reloj lo lleva el backend (`Clock`), así que la regla se prueba sin depender del navegador.
- El frontend, 4 s después del último envío, llama `POST /api/chat/conversaciones/{id}/procesar`. Si llegó otro mensaje entretanto, el backend responde "todavía no" y el frontend reprograma. Al volver a abrir el chat (`GET actual`) también se procesa lo pendiente.
- Al procesar: une los textos pendientes con un espacio, en orden; marca los mensajes como `procesado`; aplica:
  - RN-18: si contiene "?" o "¿" → mensaje fijo + repite la pregunta (V16).
  - Respuesta obligatoria de menos de 3 caracteres → pide más detalle.
  - Más de 500 caracteres → pide resumirla.
  - `precio` con tipo Promoción y respuesta "no" (o equivalente) → la pide de nuevo (V17).
  - Si pasa, guarda la respuesta y avanza al siguiente paso (mensaje del sistema con la pregunta).
- Las respuestas por botón, fecha y producto se procesan al instante (no se agrupan) y descartan cualquier texto pendiente.

## 4. API (`chat/`)

- `POST /api/chat/conversaciones`: empieza una nueva. RN-09: si el onboarding no está completo, 409 `ONBOARDING_INCOMPLETO` (V10). Si había una `EN_CURSO`, queda `ABANDONADA` ("empezar de nuevo").
- `GET /api/chat/conversaciones/actual`: la conversación `EN_CURSO` (o la última `COMPLETA` del día), con sus mensajes, el paso actual y lo que necesita la pantalla del paso (botones, fechas o productos ordenados). 204 si no hay ninguna.
- `POST /api/chat/conversaciones/{id}/mensajes` con uno de:
  - `{ "texto": "..." }` (paso TEXTO),
  - `{ "opcion": "PROMOCION" }` (BOTONES o FECHA_ESPECIAL),
  - `{ "productoId": 12 }` (PRODUCTO, del catálogo; debe ser del negocio, activo, en catálogo y completo — V11),
  - `{ "productoNuevo": { nombre, descripcion, fotos: {ENTERO, DETALLE, PORCION} }, "guardarEnCatalogo": true|false }` (PRODUCTO nuevo; exige las tres fotos; RN-14 / V14).
- `POST /api/chat/conversaciones/{id}/procesar`: cierra la respuesta agrupada si ya pasaron 4 s.
- Todo filtrado por el negocio de la sesión; una conversación de otro negocio responde 404.

## 5. Carrusel de productos (RF-20, V11, V13)

- Productos `ACTIVO`, `en_catalogo = true` y con sus tres fotos.
- Orden: primero los que tienen alguna temporada vigente (según `temporadasVigentes(hoy)` en Lima, V13), luego el resto; dentro de cada grupo, los más recientes primero.
- Mensaje de temporada si hay vigentes: "Es {nombre}: estos suelen funcionar mejor".
- Sugerencia del paso `especial`: la descripción del producto elegido.

## 6. Frontend (`chat/`)

- La pantalla principal del negocio (`/`) pasa a ser el chat (RF-19). Si el onboarding no está completo, `RutaOnboarding` ya lleva al asistente.
- Burbujas (sistema a la izquierda, usuario a la derecha), botones de respuesta rápida, carrusel horizontal de productos con foto y temporada, tarjeta "Subir uno nuevo hoy" con los tres `SubidorDeImagen` del catálogo y la pregunta "¿Lo guardo en tu catálogo?".
- Caja de texto con contador de 500, envío con Enter o botón; tras cada envío, espera 4 s (reinicia con cada mensaje) y llama `procesar`.
- Aviso lateral fijo, en tono suave (7.3); en el celular, una franja discreta sobre la caja de texto.
- Al entrar con una conversación en curso: "¿Continuamos donde quedamos?" con "Continuar" y "Empezar de nuevo".
- Al terminar: "¡Listo! Estoy preparando tu propuesta…" y una tarjeta que avisa que el storyboard llega en la fase 4.

## 7. Pruebas (sección 18)

- Unitarias: agrupación (`AgrupadorDeMensajes`: tres mensajes en 3 s = una respuesta; uno a los 5 s abre otra), detección de preguntas, longitudes, regla de promoción, orden del carrusel por temporada, validación de `chat.json`.
- Integración: V10, V11, V13 (reloj en verano), V14, V15, V16, V17, RN-19 (empezar de nuevo abandona la anterior), aislamiento entre negocios.

## 8. Fuera de la fase 3

- Generar el guion y mostrar el storyboard (fase 4): la conversación `COMPLETA` es su entrada.
- RN-26 (video del día) se aplica al aprobar el storyboard (fase 5), no al empezar el chat.

---

## Diseño final (tras la revisión y las decisiones del usuario, 2026-11-04)

### Decisiones del usuario

- **Sin fechas especiales ni temporadas en el flujo del chat.** No es prioridad para el MVP: se quita el botón "Fecha especial" y el paso `fechaEspecial`, el carrusel no se ordena por temporada y no hay saludo de temporada. **V13 queda fuera del MVP** y se documenta como trabajo futuro (junto con el calendario de fechas especiales, RF-31). Las etiquetas de temporada del catálogo (fase 2) se conservan, pero el chat no las usa.
- **Producto subido desde el chat:** temporada "Todo el año", editable después en el catálogo.

### Guion (`chat.json`)

- Raíz: `minimoCaracteres: 3`, `maximoCaracteres: 500`, `respuestasNegativas` (se comparan en minúsculas, sin tildes, espacios ni puntuación) y `mensajes` fijos: `pregunta`, `muyCorta`, `muyLarga`, `precioObligatorio`, `productoNoDisponible`, `catalogoVacio`, `cierre`, `aviso`.
- `PasoChat`: `id`, `tipo` (`BOTONES`, `PRODUCTO`, `TEXTO`), `texto`, `opciones`, `obligatoria`, `obligatoriaSi: {paso, valor}`, `omitible`, `sugerencia` (`DESCRIPCION_PRODUCTO`). El motor evalúa las condiciones de forma genérica.
- Pasos: `tipoVideo` (Producto estrella, Promoción, Tortas por encargo, Recién salido del horno) → `producto` → `especial` (obligatoria, sugerencia: descripción) → `precio` (omitible; obligatoria si `tipoVideo = PROMOCION`) → `extra` (omitible) → cierre.
- Validación al arrancar (lectura con `FAIL_ON_UNKNOWN_PROPERTIES`): ids únicos, textos no vacíos, opciones en BOTONES, un solo paso PRODUCTO, condiciones que apuntan a pasos anteriores y valores existentes, `sugerencia` solo en TEXTO, todos los mensajes presentes.

### Contrato de `respuestas` (entrada de la fase 4)

`{"tipoVideo": "PROMOCION", "producto": 12, "especial": "...", "precio": "S/ 65" | null, "extra": "..." | null}` (cada clave es el id del paso; `producto` guarda el id del producto). Una respuesta negativa u omitida se guarda como `null`, nunca como "no".

### Concurrencia y API

- POST `/conversaciones` es idempotente: si hay una `EN_CURSO`, la devuelve; con `{"empezarDeNuevo": true}` la abandona con un `UPDATE` masivo **antes** de insertar la nueva (el índice parcial no admite el orden INSERT→UPDATE de Hibernate). RN-09: sin onboarding, 409 `ONBOARDING_INCOMPLETO`.
- Mensajes, procesar y empezar de nuevo cargan la conversación con `PESSIMISTIC_WRITE`. Fuera de `EN_CURSO`: 409 `CONVERSACION_CERRADA`.
- Cada POST `/mensajes` incluye `paso`; si no coincide con el actual: 409 `PASO_CAMBIADO` (el frontend recarga sin mostrar error).
- Exactamente uno de: `texto`, `opcion`, `productoId`, `productoNuevo` (+ `guardarEnCatalogo`), `omitir`. Errores: 400 `SOLICITUD_INVALIDA`, 409 `PASO_NO_ADMITE_RESPUESTA`, 400 `OPCION_INVALIDA`, 400 `PRODUCTO_NO_DISPONIBLE`, 400 `PRODUCTO_INCOMPLETO`.
- Agrupación: `AgrupadorDeMensajes` corta por separaciones de 4 s o más entre mensajes consecutivos; el último grupo se cierra cuando pasan 4 s sin mensajes. Al llegar un texto, si el último pendiente ya tiene 4 s, primero se procesa ese grupo.
- Mensajes, procesar y GET `actual` devuelven la misma `ConversacionVista` con `procesarEnMs` (calculado con el reloj del servidor; null si no hay pendientes). GET `actual` es de solo lectura: devuelve la `EN_CURSO` o la última `COMPLETA` del día (Lima); 204 si no hay.
- Respuestas por botón o producto se guardan con su etiqueta visible y `procesado=true`.
- Al cerrar, se revalida el producto (ACTIVO y completo); si no, vuelve al paso `producto` con `productoNoDisponible`.

### Catálogo

- `producto.en_catalogo` (RN-14, V14): `listarDeNegocio` y `buscarDeNegocio` filtran `en_catalogo = true` (catálogo y admin). El chat usa `listarParaCarrusel` (ACTIVO, en catálogo, con fotos; se filtran los completos) y `buscarParaVideo` (sin filtro de catálogo).
- `ServicioCatalogo.crearDesdeChat`: reutiliza las validaciones y exige las tres fotos (`PRODUCTO_INCOMPLETO`).
- Carrusel ordenado por fecha de creación (más recientes primero). Si no hay elegibles: solo "Subir uno nuevo hoy" y el mensaje `catalogoVacio`. Se informa cuántos productos no aparecen por faltarles fotos.

### Frontend

- Chat a pantalla completa (`100dvh` menos cabecera y barra), lista con `role="log"`/`aria-live`, caja de texto fija abajo con contador del grupo (pendientes + caja), `enterKeyHint="send"`; desactivada en pasos que no son de texto ("Elige una opción arriba").
- Solo los botones del último mensaje del sistema son tocables, y se desactivan mientras se envía.
- "No, gracias" en pasos omitibles; chip "Usar la descripción de mi producto" en `especial`.
- Hoja "Subir uno nuevo hoy" en un modal con los tres subidores y dos botones explícitos: "Usar y guardar en mi catálogo" / "Usar solo para hoy".
- Temporizador con `procesarEnMs`; al volver a la pestaña, `procesar` si hay pendientes; indicador "escribiendo…" mientras se procesa.
- Al continuar un borrador: "¿Continuamos donde quedamos?" con "Continuar" / "Empezar de nuevo".
- Cierre: tarjeta "Tus respuestas" — "Guardamos todo. Muy pronto verás aquí la propuesta de tu video." — y "Crear otro video".

### Casos de la fase 3

V10, V11, V14, V15, V16, V17 (V13 fuera del MVP por decisión del usuario).

### Documentar como agregado a la especificación

`POST /api/chat/conversaciones/{id}/procesar`, el campo `paso` en los mensajes, y que los productos `en_catalogo=false` de conversaciones abandonadas se conservan en el MVP.
