# Fase 4 — Guion y storyboard: diseño

Rama `fase-4-guion`. Alcance de la sección 19: **modelos de IA en base de datos, fábrica de proveedores, adaptador Gemini, validador y guion de respaldo**, más el storyboard (RF-21, RF-22).
Reglas: RN-21 a RN-25, RN-32 (costo por llamada), RN-54 (el video guarda plan y modelos), RN-55 (credenciales fuera del código y la BD).
Casos de la sección 17: **V18, V19, V20, V21**.

Decisiones del usuario (2026-11-04):
- Todavía no hay clave de Gemini: el adaptador real se construye y se prueba contra un servidor simulado; sin clave, el sistema usa el guion de respaldo y lo avisa.
- Cambiar la foto de un plano: elegir otra de las tres del producto **o** subir una nueva.
- Sin temporadas ni fechas especiales (decisión de la fase 3).

## 1. Paquete de nicho (sección 7)

Nuevos archivos en `resources/nichos/pasteleria/`, validados al arrancar y expuestos por `PaqueteNicho`:
- `plantilla.json` (`plantillaVideo()`): los 4 planos de 7.4 — `orden`, `nombre` (Gancho, Producto, Relleno, Cierre), `foto` (DETALLE, ENTERO, PORCION; el cierre no tiene), `maxPalabras` (6, 5, 7), `segundos` (3, 4, 4, 4), `conIa` (el cierre no); los llamados a la acción por canal del onboarding (WHATSAPP → "Pide por WhatsApp", TIENDA → "Te esperamos en {distrito}", DELIVERY → "Pídelo por delivery"); movimientos del guion de respaldo; hashtags base del nicho.
- `movimientos.json` (`movimientos()`): los 7 de 7.5 con `id`, `descripcion` en palabras simples (para el storyboard) e `instruccion` en inglés (la usa la fase 5), y las prohibiciones que siempre se agregan.
- `vocabulario.json` (`vocabulario()`): tono por estilo (7.6), palabras prohibidas (salud y nutrición —permitidas solo si la descripción del producto las declara—, superlativos absolutos, promesas de entrega o tiempos).
- `guion.md` (`instruccionesGuion()`): instrucciones base para el modelo de texto, con marcadores que se reemplazan (perfil, producto, respuestas, plantilla, movimientos, vocabulario).

## 2. IA (`ia/`)

- **Puerto `Guionista`** (`ia/dominio`): `String escribir(ModeloIA modelo, String credencial, String instrucciones, String esquemaJson)` → JSON del guion. Errores tipados: `ProveedorNoDisponible` (red, 5xx, tiempo de espera) y `CredencialInvalida` (401/403). `adaptador()` devuelve `"GEMINI"`.
- **`GeminiGuionista`** (`ia/infraestructura`): `RestClient` → `POST {url}/v1beta/models/{identificadorModelo}:generateContent`, cabecera `x-goog-api-key`, cuerpo `{contents:[{role:"user", parts:[{text}]}], generationConfig:{responseMimeType:"application/json", responseJsonSchema, temperature}}`; lee `candidates[0].content.parts[*].text`. URL configurable (`autoreel.ia.gemini.url`) para las pruebas. Tiempo de espera de 30 s.
- **Secretos** (RN-55): puerto `ProveedorDeSecretos.obtener(nombreSecreto)`; en desarrollo, `SecretosDeConfiguracion` lee `autoreel.secretos.[autoreel/gemini]=${GEMINI_API_KEY:}`; en producción, Secrets Manager (fase 8). El modelo solo guarda el nombre del secreto.
- **`FabricaDeProveedores`**: plan efectivo del negocio (en el MVP, el de su suscripción; los beneficios temporales llegan en la fase 7) → `modeloTexto` → el `Guionista` cuyo `adaptador()` coincide. Si el modelo está inactivo o el adaptador no existe, error claro (RN-56).

## 3. Video, planos y costos (migración `V5`)

- `video`: `negocio_id`, `conversacion_id` (único), `producto_id`, `tipo_video`, `estado` (los de 9.2), `plan_aplicado`, `modelo_texto_usado`, `modelo_video_usado`, `opciones_produccion jsonb` (copiadas del plan, 8.6), `guion jsonb`, `descripcion_publicacion`, `hashtags jsonb`, `guion_de_respaldo`, `intentos_guion`, `costo_estimado`, `motivo_error`, `fecha_creacion`, `fecha_actualizacion`.
- `plano`: `video_id`, `orden`, `tipo_foto`, `clave_foto`, `movimiento`, `texto` (planos 1–3), `precio`, `llamado`, `ubicacion` (plano 4); único `(video_id, orden)`. Las columnas de clips (fase 5) llegan con su migración.
- `registro_costo` (RN-32): `negocio_id`, `video_id`, `proveedor`, `concepto` (`GUION`), `cantidad`, `costo_usd`, `fecha_hora`. El costo del guion = `costoReferencial` del modelo por solicitud.

## 4. Flujo

1. Al completarse la conversación (fase 3), en la misma transacción se crea el `Video` en `GUION_PENDIENTE` con el plan y los modelos vigentes (RN-54). La vista del chat devuelve `videoId`.
2. Después del commit, un evento lanza la generación en segundo plano (`@Async`), así el chat no espera a la IA.
3. `ServicioGuion.generar(videoId)`:
   - Arma las instrucciones con el paquete, el perfil, el producto y las respuestas.
   - Hasta **3 intentos** (1 + 2 reintentos, RN-25): cada respuesta pasa por `ValidadorDeGuion`; si falla, el siguiente intento incluye los errores. Cada llamada registra su costo (RN-32).
   - Si los 3 fallan, o el proveedor no responde, o falta la credencial: **guion de respaldo** (V20, RNF-09) y `guion_de_respaldo = true`.
   - Crea los 4 planos con la foto que les toca y pasa a `STORYBOARD`.
4. Una tarea programada reintenta los videos que quedaron en `GUION_PENDIENTE` más de 2 minutos (por ejemplo, si la aplicación se reinició).

## 5. Validador (RN-21 a RN-24; V18, V19)

`ValidadorDeGuion` (función pura, devuelve la lista de errores en español para el siguiente intento):
- 4 planos con orden 1–4; planos 1–3 con la foto de la plantilla y un movimiento de la lista; textos no vacíos y dentro de su máximo de palabras.
- Plano 4: `precio` igual, carácter por carácter, a lo que escribió el usuario, o vacío si no escribió (V19); `llamado` entre los permitidos por los canales del negocio; `ubicacion` = distrito.
- RN-22: ningún precio o porcentaje en los textos ni en la descripción que no esté en lo que escribió el usuario.
- RN-23: sin palabras prohibidas del paquete (salvo las de salud que la descripción declare) ni del negocio (`palabrasProhibidas`), sin distinguir mayúsculas ni tildes, por palabra completa.
- RN-24: descripción de hasta 600 caracteres; entre 5 y 8 hashtags con forma `#palabra`; al menos uno del distrito.

## 6. Guion de respaldo (RN-25, V20)

Sin IA, con los textos del usuario recortados a los límites: gancho = lo especial (6 palabras), producto = nombre (5), relleno = lo especial o la descripción (7); movimientos por defecto de la plantilla; cierre con precio, llamado del primer canal y distrito; descripción con nombre, especial, extra y precio (600); hashtags base + distrito + negocio (5 a 8). Siempre pasa el validador (se prueba).

## 7. Storyboard (RF-21, RF-22; V18, V21)

- `GET /api/videos/{id}`: estado, planos (orden, nombre, foto con URL, movimiento en palabras simples, texto y su máximo de palabras; cierre con precio, llamado, ubicación, logo, colores y foto del local), descripción y hashtags, aviso de guion de respaldo, fotos del producto para cambiar.
- `PUT /api/videos/{id}/planos/{orden}`: `{texto}` y/o `{foto: {tipo: "ENTERO"}}` o `{foto: {clave: "subidas/..."}}` (verificada como FOTO_PRODUCTO). Solo en `STORYBOARD`. Texto por encima del límite → 400 `TEXTO_MUY_LARGO` (V21); palabras prohibidas → 400 `PALABRA_PROHIBIDA`. El plano 4 no se edita en esta fase.
- `POST /api/videos/{id}/descartar`: `STORYBOARD` → `DESCARTADO`.
- Aprobar el storyboard (consume el video del día, RN-26) y generar los clips llegan en la fase 5.
- Aislamiento: un video de otro negocio responde 404.

## 8. Frontend

- Al terminar el chat, la tarjeta final lleva a "Ver mi propuesta" (`/videos/{id}`).
- `PaginaStoryboard`: mientras `GUION_PENDIENTE`, "Estoy preparando tu propuesta…" (consulta cada 2 s); luego 4 tarjetas verticales con la foto, el movimiento en palabras simples y el texto editable con contador de palabras; el cierre muestra precio, llamado, distrito y logo sobre la foto del local o los colores. "Cambiar foto": hoja con las tres fotos del producto y "Subir otra". Aviso si se usó el guion de respaldo. Botón "Crear mi video" visible pero desactivado ("llega en la siguiente fase") y "Descartar".

## 9. Pruebas

- Unitarias: validador (V18, V19, prohibidas, hashtags, distrito, precios inventados), guion de respaldo (siempre válido), `GeminiGuionista` contra un servidor HTTP simulado (forma de la petición, lectura de la respuesta, 401, 500, tiempo de espera).
- Integración (con un `Guionista` simulado programable): guion válido → `STORYBOARD`; inválido tres veces → respaldo (V20); sin credencial → respaldo; costo registrado; V21; cambiar foto por tipo y por subida; descartar; aislamiento.


## Anexo: cambios tras la revisión del diseño (todos aceptados)
**1. Cambios al diseño (ordenados por severidad)**

**Alta**

1. **Generación del guion en segundo plano (§4.2 y §4.4).** Hoy dos procesos pueden tomar el mismo video a la vez: el `@Async` y la tarea de reintento. Eso duplica el costo y rompe el índice único `(video_id, orden)`. Además, en el código no existen `@EnableAsync` ni `@EnableScheduling`.
   - Arreglo: quitar el `@Async` y el evento posterior al commit. Dejar un solo `ProcesadorDeGuiones` con `@Scheduled(fixedDelay = 3 s)`, en una clase `ConfiguracionTareas` que lleve `@EnableScheduling`. Así se cumple §12.2 y la fase 5 solo le agrega los demás estados.
   - Agregar `video.guion_reclamado_hasta timestamptz`. Cada ejecución reclama el video con un UPDATE condicional y sigue solo si afectó 1 fila:
     ```sql
     update video set guion_reclamado_hasta = now() + interval '3 minutes'
     where id = ? and estado = 'GUION_PENDIENTE'
       and (guion_reclamado_hasta is null or guion_reclamado_hasta < now())
     ```
   - Si el reclamo anterior venció sin terminar (reinicio o error no previsto), se aplica directamente el guion de respaldo, sin llamar a la IA. Así un video nunca queda atascado.
   - `generar` no lleva `@Transactional`. Va en cuatro pasos:
     1. Una transacción corta que lee los datos que necesita (perfil, producto, respuestas y modelo).
     2. Las llamadas HTTP, fuera de toda transacción.
     3. El costo de cada respuesta, guardado en su propia transacción corta.
     4. Una transacción final que bloquea el video (`PESSIMISTIC_WRITE`), comprueba que sigue en `GUION_PENDIENTE`, crea los planos y lo pasa a `STORYBOARD`.
   - Agregar el índice parcial `on video (fecha_creacion) where estado = 'GUION_PENDIENTE'`.
   - Agregar la propiedad `autoreel.guion.procesador.activo`, en `false` en el perfil test. Las pruebas llaman al procesador directamente.

2. **El guion debe usar el modelo guardado en el video (RN-49, RN-54).** Ahora la fábrica (§2) vuelve a calcular el plan efectivo al generar, lo que contradice RN-54. Además, `Plan.modeloTexto` es LAZY: leerlo fuera de una transacción lanza un error.
   - Arreglo: crear `facturacion.ResolutorDePlanEfectivo.de(negocioId)`, con `join fetch` de los modelos, y usarlo **solo al crear el Video**.
   - `FabricaDeProveedores.guionista(ModeloIA modelo)` recibe `video.modeloTextoUsado` y valida: tipo TEXTO, estado ACTIVO, que exista el adaptador (RN-56) y que la unidad de costo sea `POR_SOLICITUD`.
   - Si el modelo está inactivo o no hay adaptador: guion de respaldo y aviso.
   - Al armar el mapa de adaptadores, que la fábrica falle al arrancar si dos implementaciones dicen el mismo `adaptador()`.

3. **Crear el Video sin poner en riesgo el chat.** Si la creación del Video falla dentro de la transacción del chat, se revierte también la última respuesta del usuario.
   - Arreglo: la conversación se completa en un solo lugar (`ServicioChat.aceptar`, línea 228). Justo después de `conversacion.completar(ahora)` se publica `ConversacionCompletada(conversacionId, negocioId)`.
   - Un `@EventListener` síncrono en `video/aplicacion` crea el Video en la misma transacción, **sin validar el estado de los modelos** (eso lo resuelve el paso de generación).
   - `vista()` obtiene el `videoId` con `videos.findIdByConversacionId`. Así también lo devuelve `actual()` para la conversación COMPLETA del día.

4. **El guion de respaldo no siempre pasa el validador, y se ve poco profesional (§6).**
   - El problema: copia los textos del usuario. Si "especial", la descripción o el nombre traen "la mejor de Lima" o "sin gluten" (sin que la descripción lo declare), o una palabra prohibida por el negocio, el respaldo falla la RN-23. Además, el gancho y el relleno salen los dos de "especial" (se repiten) y el recorte deja frases que terminan en "de" o "con".
   - Arreglo, en `plantilla.json`:
     - Agregar `ganchosDeRespaldo` por `tipoVideo`, con textos fijos (por ejemplo, RECIEN_HORNEADO → "Recién salido del horno").
     - Agregar `textosDeRespaldo` por plano y una descripción fija.
   - Arreglo, en la lógica del respaldo:
     - Cada plano prueba sus candidatos en orden: producto = nombre y luego texto fijo; relleno = "especial", luego la descripción, luego texto fijo. Usa el primero que, recortado, queda limpio: sin frases prohibidas, sin emojis y con al menos 2 palabras.
     - Recorte: hasta la primera `, . ;`, luego al máximo de palabras; quitar los conectores finales (de, con, y, en, a, para, por, que, el, la, los, las, un, una) y poner mayúscula inicial.
     - Antes de guardar, el respaldo pasa por `ValidadorDeGuion`. Si aun así falla, se usa la versión 100 % fija.
   - Al arrancar, validar que los textos fijos pasen el vocabulario y que `hashtagsBase` tenga entre 3 y 6 hashtags.
   - Prueba: con "especial" = "el mejor de Lima" y = "sin gluten".

5. **Precio del cierre (V19).** El chat admite hasta 500 caracteres (`chat.json`), y el diseño no fija un largo máximo para el precio del cierre.
   - Mientras el usuario decide (pregunta 1): `plano.precio varchar(500)`.
   - Un precio `""` se normaliza a null.
   - Si `respuestas.precio` es null, el plano 4 va sin precio y no puede aparecer ninguna mención de precio en ninguna parte.

**Media**

6. **RN-22 sin un criterio que se pueda programar (§5).** El validador compara contra "lo que escribió el usuario", pero la RN-22 también permite datos del perfil y de la descripción del producto.
   - Fuentes permitidas: respuestas del chat (especial, precio, extra), nombre y descripción del producto, y del perfil: nombre del negocio, especialidad, distintivo, horario, whatsapp, ciudad y distrito.
   - Qué se detecta (en planos, descripción y hashtags):
     - números: `\d+([.,]\d+)?`, `S/`, `$`, `%`, `\d+\s*x\s*\d+`;
     - palabras de promoción: 2x1, gratis, descuento, dscto, oferta, promo, regalo (lista en `vocabulario.json`).
   - Cada coincidencia, normalizada, debe aparecer en esas fuentes.
   - En los planos 1 a 3 se rechaza cualquier mención de precio, aunque el usuario la haya escrito (regla de `guion.md`).
   - Dejar escrito que los ingredientes y sabores se controlan solo con las instrucciones de `guion.md`, no con el validador.

7. **RN-23: normalización y frases (§5).** Comparar "por palabra completa" no sirve para las frases prohibidas de `vocabulario.json` ("sin azúcar", "el mejor").
   - Crear un `NormalizadorDeTexto` único, compartido por el validador, el respaldo y el PUT:
     - NFD, quitar solo U+0301 y U+0308 y volver a NFC, para no convertir la "ñ" en "n";
     - minúsculas con `Locale.ROOT`;
     - todo lo que no sea `[\p{L}\p{N}]` pasa a espacio.
   - Una frase prohibida se detecta como secuencia contigua de tokens.
   - En los hashtags se compara el hashtag sin `#` contra la frase sin espacios.
   - Las `palabrasProhibidas` del negocio se separan por `[,;\n]`, se recortan, se descartan las vacías y cada una se trata como frase.
   - La excepción de salud aplica si la frase aparece en la descripción normalizada del producto.

8. **RN-24 y V21: reglas sin definir.**
   - **Conteo de palabras:** `strip().split("\\s+")`, ignorando los tokens sin letras ni dígitos. Es una sola función, replicada en el frontend con la misma tabla de casos ("S/ 65", "¡Antojo!", "2x1", "—").
   - **Hashtags:**
     - forma `^#[\p{L}\p{N}_]+$`;
     - quitar duplicados sin distinguir mayúsculas antes de contar los 5 a 8;
     - el hashtag del distrito es el que contiene el distrito normalizado y sin espacios.
   - **Límite de 600:** la RN-24 dice que la descripción "incluye" los hashtags, así que el límite se aplica a `descripcionPublicacion + "\n\n" + hashtags.join(" ")` ≤ 600.
   - Corregir `guion.md`, que hoy pide "hasta 600 caracteres" solo para la descripción: pedir hasta unos 450.

9. **Cierre incompleto frente a §7.4.**
   - El plano 4 de `GET /api/videos/{id}` debe incluir `whatsapp` si el canal WHATSAPP está activo.
   - Prioridad del llamado: el orden de `llamados` en `plantilla.json` (WHATSAPP, DELIVERY, TIENDA), no el orden de `canales` del perfil.
   - El validador compara los llamados con `{distrito}` ya reemplazado.
   - La UI no repite `ubicacion` si el llamado ya incluye el distrito.

10. **`GeminiGuionista`: errores y forma de la respuesta incompletos.**
    - Clasificación de errores:

      | Respuesta | Error tipado |
      |---|---|
      | 401, 403 | `CredencialInvalida` |
      | 400, 404 | `ConfiguracionInvalida` |
      | 429, 5xx, IOException, tiempo de espera | `ProveedorNoDisponible` |

    - Ninguno de los tres se reintenta: van directo al respaldo y a un puerto `AvisoDeIntegracion` (RN-57). En la fase 4 su implementación solo escribe un log ERROR; la notificación `INTEGRACION_CON_ERROR` llega en la fase 7, como dice el plan de desarrollo.
    - Cuentan como **intento inválido** (no como excepción), y su error pasa al siguiente intento: `promptFeedback.blockReason`, respuesta sin candidatos, `finishReason` distinto de STOP o JSON mal formado.
    - Concatenar solo las partes con `thought != true`.
    - Esquema de respuesta plano y uniforme: `planos` con minItems y maxItems = 4; cada ítem con `orden` obligatorio y `foto` (enum), `movimiento` (enum de `movimientos.json`), `texto`, `precio`, `llamado` y `ubicacion` como nullable. Las longitudes las controla solo el validador.
    - `temperature` sale de `modelo.parametros.temperatura` (ya está en V2), con un valor por defecto si falta.
    - El mensaje de error nunca incluye la clave.

11. **Secretos en la configuración.**
    - En `application.yml`, la clave va entre comillas: `autoreel.secretos."[autoreel/gemini]": ${GEMINI_API_KEY:}`. Sin comillas YAML falla, y sin corchetes Spring borra la `/`.
    - Se mapea con `@ConfigurationProperties` como `Map<String,String>`.
    - `ProveedorDeSecretos.obtener` devuelve `Optional<String>` y filtra los valores vacíos con `isBlank()`.
    - Sin clave: respaldo directo, sin llamar al proveedor y sin registrar costo.

12. **RestClient y tiempos de espera.** El `RestClient.Builder` autoconfigurado no está: falta el starter de restclient en el pom. La clase `RestClient` sí está, a través de spring-web.
    - Arreglo: construir el cliente en `ia/infraestructura/ConfiguracionGemini` con `JdkClientHttpRequestFactory` (conexión 5 s, lectura 30 s, `setReadTimeout`).
    - Usar `PropiedadesGemini(url, tiempoConexion, tiempoLectura)` bajo `autoreel.ia.gemini`; en las pruebas, lectura de 500 ms.
    - Plazo total: no empezar un nuevo intento si ya pasaron 60 s; en ese caso, respaldo.

13. **Migración V5 lista para `ddl-auto=validate`.**
    - **`video`:**
      - `plan_aplicado_id`, `modelo_texto_usado_id` y `modelo_video_usado_id` como `bigint not null` con FK;
      - `tipo_video varchar(40)` sin CHECK y mapeado como String (regla 5: sus valores vienen del paquete);
      - `estado` con CHECK de todos los estados de 9.2;
      - `guion_reclamado_hasta`;
      - `version` (`@Version`);
      - `costo_estimado numeric(12,6) default 0`;
      - `hashtags jsonb default '[]'`;
      - `motivo_respaldo varchar(30)` nulo (SIN_CREDENCIAL, PROVEEDOR_NO_DISPONIBLE, GUION_INVALIDO, MODELO_INACTIVO).
    - **`plano`:** `tipo_foto` nulo (orden 4, o foto subida en el storyboard); CHECK de que `clave_foto` no sea nula en los planos 1 a 3.

14. **`PUT /api/videos/{id}/planos/{orden}` sin detalle.**
    - Bloqueo: `videos.bloquearDeNegocio(id, negocioId)` (PESSIMISTIC_WRITE); 404 si el video es de otro negocio.
    - Errores:
      - 409 `VIDEO_NO_EDITABLE` fuera de STORYBOARD;
      - 400 `TEXTO_VACIO`;
      - 400 `SOLICITUD_INVALIDA` si llegan `tipo` y `clave` juntos, o ninguno;
      - el mensaje de `PALABRA_PROHIBIDA` nombra el término.
    - `{foto:{tipo}}`: toma la clave actual de esa foto del producto (`catalogo.elegidoParaVideo`) y guarda `tipo_foto`.
    - `{foto:{clave}}`: llama a `ServicioDeArchivos.confirmar(negocioId, FOTO_PRODUCTO, clave)`, guarda la clave definitiva y deja `tipo_foto = null`. No modifica el producto ni el catálogo.
    - **No** aplicar la RN-22 a los textos que edita el usuario: él mismo los escribe.

15. **Dos fuentes de verdad.** Documentar que `video.guion jsonb` guarda la última respuesta del modelo, solo para auditoría, y que las filas de `plano` son la fuente de verdad que lee la fase 5.

**Baja**

16. **Costo de cada llamada (RN-32, V32).**
    - Se registra un `registro_costo` por cada respuesta 2xx, aunque el guion sea inválido, con `cantidad = 1`, y se suma a `video.costo_estimado` en la misma transacción corta.
    - No se registra costo en errores 4xx, 5xx, tiempo de espera ni cuando falta la credencial.

17. **Emojis y saltos de línea.** FFmpeg (fase 5) no los dibuja. El validador y el PUT los rechazan en los planos 1 a 3 con 400 `CARACTER_NO_PERMITIDO`. En la descripción sí se permiten.

18. **`VAPOR_SUAVE`.** Agregar `soloTiposVideo: ["RECIEN_HORNEADO"]` en `movimientos.json`. El enum del esquema y el validador lo excluyen en los demás tipos de video.

19. **Pruebas de integración.** Usar el `GeminiGuionista` real apuntado al servidor HTTP simulado de las pruebas unitarias (`autoreel.ia.gemini.url`), en vez de un segundo bean `Guionista` con el mismo adaptador.

20. **Frontend.**
    - A los 60 s en GUION_PENDIENTE, mostrar "Está tardando más de lo normal; puedes salir, tu propuesta te esperará" y consultar cada 5 s.
    - Si la consulta falla, ofrecer "Reintentar".
    - Junto a "Crear mi video", mostrar los videos por día del plan aplicado. El consumo real llega en la fase 5 (§14).
    - El texto del aviso de respaldo vive en el paquete, sin "error" ni "IA".

21. **Documentación (regla 11, §23).** Agregar una sección con:
    - `.env.example` con `GEMINI_API_KEY=` vacío;
    - `application-test.yml` con la clave vacía y la URL del servidor simulado;
    - `docs/modelo-de-datos.md` con la V5 (la relación es `video.conversacion_id` único, distinta de 9.1);
    - `docs/arquitectura.md` (fábrica, puertos y procesador);
    - un ADR de credenciales y procesador;
    - las guías `nuevo-proveedor-ia` y `nuevo-nicho` (plantilla, movimientos, vocabulario y `guion.md`);
    - README y CHANGELOG.

Descarté estos hallazgos de los revisores:
- **Columna `modelo_ia_id` en `registro_costo`:** no está en la 9.1 y no hace falta para V32.
- **`registro_costo.video_id` nulo:** es para la prueba de conexión de la fase 7.
- **Crear ya la tabla de notificaciones (RN-57):** el plan de desarrollo la pone en la fase 7.
- **Aplicar la RN-22 a los textos que edita el usuario:** él mismo los escribe.
- **"La aplicación no tiene RestClient":** la clase está en spring-web; solo falta el builder autoconfigurado.


### Decisiones del usuario sobre la revisión (2026-11-04)

- **Cierre armado por el sistema:** el plano 4 (precio literal del usuario, llamado y distrito) no lo escribe la IA. El esquema de respuesta del modelo solo incluye los planos 1 a 3, la descripción y los hashtags. En el storyboard el precio se puede editar, con un máximo de 40 caracteres; si lo escrito en el chat pasa de 40, se propone recortado en el límite de una palabra.
- **Pantalla "Mis videos" desde ya:** `GET /api/videos` (producto, estado, fecha; recientes primero) y una pantalla simple, base del historial RF-26 de la fase 5.

## Estado (2026-11-04)

**Implementado** en la rama `fase-4-guion`, con el diseño, el anexo y las decisiones del usuario. Casos V18 a V21 cubiertos por pruebas automatizadas; 193 pruebas en total en el backend (9 de integración en `VideoIntegracionTest`, con el `GeminiGuionista` real contra `ServidorGeminiSimulado`). Detalle de lo entregado en `docs/plan-de-desarrollo.md`, sección 6.

Lo que se construyó:
- `ia`: `Guionista`, `GeminiGuionista`, `ErrorDeProveedor`, `ProveedorDeSecretos` + `SecretosDeConfiguracion`, `FabricaDeProveedores`, `AvisoDeIntegracion` (solo log por ahora).
- `facturacion.aplicacion.ResolutorDePlanEfectivo` y el módulo `costos` (`registro_costo`, `ServicioCostos`).
- `video`: `Video`, `Plano`, `CreadorDeVideos`, `ServicioGuion`, `ProcesadorDeGuiones`, `ValidadorDeGuion`, `GuionDeRespaldo`, `ServicioVideos` y `VideoController` (`GET /api/videos`, `GET /api/videos/{id}`, `PUT /api/videos/{id}/planos/{orden}`, `POST /api/videos/{id}/descartar`). Migración `V5__videos_y_costos.sql`.
- Paquete de nicho: `plantilla.json`, `movimientos.json`, `vocabulario.json` y `guion.md`, validados al arrancar (textos fijos contra el vocabulario, hashtags base entre 3 y 6, un gancho de respaldo por tipo de video y un llamado por canal).
- Frontend: `PaginaStoryboard`, `PaginaMisVideos` y "Ver mi propuesta" en la tarjeta final del chat.
- Sin `GEMINI_API_KEY` (todavía no hay clave) el storyboard usa el guion de respaldo.

Diferencias con el diseño que se ven en el código:
- **Errores del proveedor:** una sola excepción, `ErrorDeProveedor`, con tipo `CREDENCIAL_INVALIDA`, `CONFIGURACION_INVALIDA` o `NO_DISPONIBLE`, en lugar de una clase por error.
- **`Guionista.escribir`** devuelve `RespuestaDelModelo` (texto, o el problema si vino bloqueada, vacía o cortada) en lugar de un `String`.
- **Sin `ConfiguracionGemini`:** el `RestClient` se arma en el constructor de `GeminiGuionista` con `JdkClientHttpRequestFactory`, y `PropiedadesGemini` es un record dentro de esa clase.
- **`NormalizadorDeTexto` se llama `nicho.dominio.Texto`** (normalizar, frases, contar palabras, caracteres que no se pueden dibujar). El frontend replica `contarPalabras` en `api/videos.ts`.
- **Propiedad de las tareas:** `autoreel.tareas.activas` (en `ConfiguracionTareas`, para todas las tareas programadas) y `autoreel.tareas.guiones-cada` (3 s), en lugar de `autoreel.guion.procesador.activo`. El procesador toma lotes de 5 videos.
- **`plano.precio varchar(40)`**, no 500, por la decisión del usuario del precio de hasta 40 caracteres (el chat admite más; se propone recortado en el límite de una palabra). `plano.texto` es `varchar(200)`.
- **`motivo_respaldo`** suma `CREDENCIAL_INVALIDA`, `CONFIGURACION_INVALIDA` y `PROCESO_INTERRUMPIDO` a los cuatro del anexo.
- **Sin credencial también se avisa** por `AvisoDeIntegracion` (como `CREDENCIAL_INVALIDA`), además de usar el respaldo sin llamar ni cobrar.
- **Respaldo en la plantilla:** `textoDeRespaldo`, `movimientoDeRespaldo` y `respaldoDesde` van dentro de cada plano; hay `descripcionDeRespaldo` y `descripcionFijaDeRespaldo` (sin datos del usuario) y el `avisoDeRespaldo` para el usuario.
- **`GET /api/videos/{id}`:** los planos 1 a 3 van en `planos` y el cierre aparte en `cierre` (con `whatsapp` y `maximoCaracteresPrecio`). Incluye `plan.videosPorDia`.
- **`PUT` del plano 4:** solo el precio (decisión del usuario); cualquier otro campo responde 400 `SOLICITUD_INVALIDA`. Un orden fuera de 1 a 4 responde 404. Una foto del producto que ya no existe responde 400 `FOTO_NO_DISPONIBLE`.
- **Sin suscripción** al crear el video: 409 `SIN_SUSCRIPCION` (desde `ResolutorDePlanEfectivo`).
- **Si el respaldo de un proceso interrumpido falla** por algo no previsto, el video pasa a `ERROR` con `motivo_error`.
- **La descripción y los hashtags** se muestran en el storyboard, pero no se editan en esta fase.
