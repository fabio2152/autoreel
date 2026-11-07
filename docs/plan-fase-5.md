# Fase 5 — Generación: diseño

Rama `fase-5-generacion`. Alcance de la sección 19: **adaptador de video, procesador de tareas, composición con FFmpeg, plan de respaldo y revisión**, más la descarga (RF-25) y el historial (RF-26).
Requisitos: RF-22 (aprobar), RF-23, RF-24, RF-25, RF-26. Reglas: RN-26 a RN-32, RN-49, RN-54 a RN-57. Casos de la sección 17: **V22 a V27** (y V08 para el video final, V39).

Decisiones del usuario (2026-11-04):
- **Video con Veo 3.1 en Google Cloud** (Vertex AI, hoy "Gemini Enterprise Agent Platform"). Google cerró los Veo 3.1 *preview* de la API de Gemini el 22/10/2026; allí solo queda Gemini Omni Flash. Veo en Google Cloud usa una **cuenta de servicio** (credencial JSON), distinta de `GEMINI_API_KEY`. Todavía no hay credencial: el adaptador se prueba contra un servidor simulado y, sin credencial, cada plano usa el acercamiento de FFmpeg (8.5) y el video se entrega igual.
- **Música (P-02):** pistas de relleno generadas, una por estilo; la fuente real queda pendiente.
- **Tipografías (P-05):** Google Fonts con licencia OFL: Nunito (casero), Playfair Display (elegante), Fredoka (divertido).
- **FFmpeg:** instalado en la PC con winget; en el CI, con apt; en producción, en la imagen Docker (fase 8).
- Sin temporadas ni fechas especiales (fase 3).

## 1. Aprobar el storyboard y consumo diario (RF-22, RN-26, RN-27, V22, V27)

- `POST /api/videos/{id}/aprobar-storyboard` (solo `STORYBOARD`):
  - Bloquea la fila de consumo del negocio para el **día de Lima** (`consumo_diario`, única por `negocio_id, fecha`; se crea con `insert ... on conflict do nothing` y se lee con `PESSIMISTIC_WRITE`).
  - Si `videos_generados >= plan.videosPorDia` → 409 `VIDEOS_DEL_DIA_AGOTADOS`: "Ya usaste tu video de hoy. Mañana tienes uno nuevo." (con la fecha). El plan es el **aplicado al video** (RN-54); los beneficios temporales llegan en la fase 7.
  - Suma 1, guarda `video.consumo_fecha` y pasa a `GENERANDO` con los planos 1 a 3 en `PENDIENTE`.
- **RN-27:** si el video termina en `ERROR` por una falla del sistema, se resta 1 al consumo de `video.consumo_fecha` una sola vez (`video.consumo_devuelto`).
- La vista del storyboard muestra `consumo`: usados hoy, por día y si puede aprobar.

## 2. Modelo de video: puerto y adaptador (8.2, 11.1, RN-56)

- **Puerto `GeneradorDeClips`** (`ia/dominio`): `adaptador()`, `iniciar(modelo, credencial, SolicitudDeClip) → idTarea` y `consultar(modelo, credencial, idTarea) → EstadoDeClip` (`EnCurso`, `Listo(bytes, mime, segundos)`, `Fallido(motivo)`). Errores con `ErrorDeProveedor` (fase 4).
- **`SolicitudDeClip`:** foto (bytes y tipo), instrucción en inglés (de `movimientos.json`), prohibiciones (negativas), segundos y relación de aspecto.
- **`VeoGeneradorDeClips`** (adaptador `VEO`, Vertex AI):
  - `POST https://{region}-aiplatform.googleapis.com/v1/projects/{proyecto}/locations/{region}/publishers/google/models/{identificadorModelo}:predictLongRunning` con `instances[{prompt, image{bytesBase64Encoded, mimeType}}]` y `parameters{aspectRatio "9:16", durationSeconds, sampleCount 1, negativePrompt, personGeneration "dont_allow", generateAudio false, resolution}`.
  - Consulta: `POST ...:fetchPredictOperation` con `{operationName}`; listo cuando `done`; el clip en `response.videos[0].bytesBase64Encoded`; `raiMediaFilteredCount > 0` o `error` = fallido.
  - **Credencial:** el secreto del modelo es el JSON de la cuenta de servicio. `TokenDeGoogle` firma un JWT RS256 con su `private_key` (Nimbus, ya en el proyecto) y lo cambia por un token de acceso en `oauth2.googleapis.com/token`; lo guarda hasta 1 minuto antes de vencer. El proyecto sale del JSON (`project_id`); la región, de `modelo.parametros.region` (por defecto `us-central1`).
  - URLs y tiempos en `autoreel.ia.veo` (las pruebas apuntan a un servidor simulado).
- **`FabricaDeProveedores.generadorDeClips(modelo)`:** tipo `VIDEO`, `ACTIVO`, `POR_SEGUNDO` y adaptador existente; si no, respaldo y aviso (RN-57).
- **Secretos:** `SecretosDeConfiguracion` admite secretos en archivo (`autoreel.secretos-en-archivo."[autoreel/veo]": ${VEO_CREDENCIAL_ARCHIVO:}`), porque un JSON con saltos de línea no cabe bien en el `.env`.
- **Confirmado en la documentación oficial** (docs.cloud.google.com, 2026-11-04):
  - `veo-3.1-generate-001` y `veo-3.1-fast-generate-001` son GA; `veo-3.1-lite-generate-001` está en **vista previa** (la del plan Lite, por costo).
  - Duraciones 4, 6 u 8 s; 9:16 admitido; `negativePrompt` y `generateAudio` (por defecto `true`) opcionales; sin `storageUri`, el clip vuelve en la respuesta.
  - Solo en `us-central1`. Vertex no acepta clave de API para Veo: hace falta la cuenta de servicio.
  - Precio por segundo sin confirmar en la página oficial (se carga desde la base de datos y se ajusta en la fase 7).
- **Migración V6:** el modelo Veo pasa a `nombre_secreto = 'autoreel/veo'` y `parametros` con `region`, `duracionClipSegundos: 4`, `relacionAspecto: "9:16"`, `resolucion: "720p"`, `generarAudio: false`. El identificador se confirma con la documentación oficial (es un dato editable).

## 3. Procesador de videos (12.2, 8.2, 8.5, RN-30, V24)

- **`ProcesadorDeVideos`** (`@Scheduled`, cada 10 s) avanza los videos en `GENERANDO` y `COMPONIENDO`. Cada video se reclama con un UPDATE condicional sobre `video.trabajo_reclamado_hasta` (2 minutos), como el guion. El pool de tareas programadas sube a 2 hilos, para que una composición no frene los guiones.
- **Por plano con IA** (`plano.estado`: `PENDIENTE`, `GENERANDO`, `LISTO`, `RESPALDO`):
  - `PENDIENTE`: lee la foto, envía la tarea (`id_tarea_proveedor`, `enviado_en`) y pasa a `GENERANDO`. Las tres se envían en la misma pasada (en paralelo para el proveedor).
  - `GENERANDO`: consulta. Listo → guarda el clip en el almacenamiento (`clave_clip`), registra su costo (`segundos × costoReferencial`, RN-32) y pasa a `LISTO`. Fallido → `intentos + 1`; con 2 intentos, `RESPALDO`; si no, vuelve a `PENDIENTE`. Si pasan **10 minutos** desde `enviado_en`, `RESPALDO`.
  - Sin credencial, modelo inactivo o error del proveedor (`ErrorDeProveedor`): `RESPALDO` directo y aviso (RN-57), sin cobrar.
- Con los tres planos en `LISTO` o `RESPALDO`, el video pasa a `COMPONIENDO`.
- **`COMPONIENDO`:** compone, guarda el MP4 final (`video.clave_final`) y pasa a `LISTO`. Si la composición falla 2 veces (`intentos_composicion`), `ERROR` con `motivo_error` y devolución del consumo (RN-27).

## 4. Composición con FFmpeg (7.4, 8.3, RN-31, V23)

- **Puerto `ComponedorDeVideo`** (`video/dominio`) → **`FfmpegComponedor`** (`video/infraestructura`), con `autoreel.ffmpeg.ruta` (`FFMPEG_PATH`, por defecto `ffmpeg`) y tiempo máximo de 2 minutos.
- **Entrada (`Composicion`):** por plano, el clip o la foto (respaldo), sus segundos y su texto; el cierre (precio, llamado, WhatsApp, logo, foto del local o colores); la pista; la tipografía del estilo.
- **Trabajo en una carpeta temporal** (se borra al terminar): baja los archivos del almacenamiento, escribe cada texto en su archivo (`drawtext textfile=`, sin problemas de comillas) y ejecuta FFmpeg con esa carpeta como directorio de trabajo (rutas relativas).
- **Gráfico de filtros:**
  - Cada clip: `scale` para cubrir 1080×1920, `crop`, `fps=30`, recorte a su duración (`trim` + `setpts`).
  - Respaldo: la foto con `zoompan` (acercamiento lento de 1,0 a 1,12) por la duración del plano.
  - Cierre: la foto del local con un zoom suave o un degradado con los dos colores de marca (`gradients`); logo arriba, precio, llamado y WhatsApp.
  - Textos: en la mitad inferior dentro de la zona segura (entre 55 % y 75 % del alto, márgenes laterales de 90 px), con caja semitransparente y borde; el sistema parte el texto en dos líneas si pasa de ~20 caracteres.
  - Unión: `concat` con corte directo entre 1, 2 y 3, y un fundido de 0,4 s hacia el cierre (fundido a negro al final del 3 y desde negro al inicio del cierre, sin cambiar la duración).
  - Música: la pista recortada a 15 s con `afade` de salida de 1,5 s; el audio de los clips se descarta.
  - Salida: H.264 (`libx264`, `yuv420p`, `-preset veryfast`, `-crf 23`), AAC 128 kb/s, `+faststart`, `-t 15`.
- **Música (`pista_musical`):** id, nicho, estilo, nombre, clave, duración, licencia, activa. El paquete Pastelería trae `musica.json` y una pista de relleno por estilo; al arrancar, `CargadorDePistas` las sube al almacenamiento y crea las filas que falten. El video guarda su pista (`video.pista_musical_id`), elegida por estilo, para que recomponer use la misma.
- **Tipografías:** en el paquete (`nichos/pasteleria/fuentes/`, con su licencia OFL) y en `plantilla.json` (`tipografias` por estilo).

## 5. Revisión (RF-24, RN-28, RN-29, V25, V26)

- `GET /api/videos/{id}` en `LISTO` y después: URL del video final, estado de cada plano (`usoRespaldo`), regeneraciones usadas y por video.
- `POST /api/videos/{id}/planos/{orden}/regenerar` (solo `LISTO`, planos 1 a 3): si `regeneraciones_usadas < plan.regeneracionesPorVideo`, el plano vuelve a `PENDIENTE` (intentos en 0) y el video a `GENERANDO`; si no, 409 `SIN_REGENERACIONES`. Reintentar con IA un plano de respaldo también usa este botón.
- `PUT /api/videos/{id}/planos/{orden}` en `LISTO`: cambiar el texto o el precio solo recompone (`COMPONIENDO`), sin consumir nada (RN-29, V25); cambiar la foto regenera ese clip y cuenta como regeneración (8.4).
- `POST /api/videos/{id}/aprobar`: `LISTO` → `APROBADO` (la publicación llega en la fase 6).
- `GET /api/videos/{id}/descarga`: `{url}` firmada con `Content-Disposition: attachment`, en `LISTO` y `APROBADO` (RF-25).
- `POST /api/videos/{id}/descartar`: también desde `LISTO`.

## 6. Datos (migración V6)

- `video`: `consumo_fecha date`, `consumo_devuelto boolean`, `regeneraciones_usadas int`, `intentos_composicion int`, `trabajo_reclamado_hasta timestamptz`, `clave_final varchar`, `pista_musical_id` (FK), índice parcial de `GENERANDO` y `COMPONIENDO`.
- `plano`: `estado varchar` (CHECK), `intentos int`, `uso_respaldo boolean`, `id_tarea_proveedor varchar`, `enviado_en timestamptz`, `clave_clip varchar`.
- `consumo_diario` (negocio_id, fecha, videos_generados ≥ 0; único por negocio y fecha).
- `pista_musical`.
- `modelo_ia`: datos del modelo Veo (sección 2).

## 7. Frontend

- **Storyboard:** "Crear mi video" activo, con "Te queda 1 video hoy"; agotado, desactivado con "Mañana tienes uno nuevo".
- **Generando** (`GENERANDO`, `COMPONIENDO`): mensajes amables que rotan, el avance por plano, consulta cada 5 s y "puedes salir; tu video te esperará en Mis videos".
- **Revisión** (`LISTO`): reproductor vertical; por plano, "Regenerar este plano" (con las restantes; desactivado al agotarse) y "Editar texto"; aviso en los planos de respaldo con "Reintentar con IA"; "Aprobar", "Descargar" y "Descartar".
- **Aprobado:** reproductor, "Descargar" y "Publicar en Instagram llega pronto" (fase 6).
- **Mis videos:** miniatura y estado (ya existe); consulta mientras hay videos en proceso.
- **Panel del administrador:** la columna "videos del mes" usa `consumo_diario` (RF-02).

## 7.1 Pruebas

- **Unitarias:** gráfico de filtros y partición de textos; reglas de consumo; transiciones del plano (intentos, 10 minutos, respaldo).
- **`VeoGeneradorDeClipsTest`** contra un servidor simulado (token, `predictLongRunning`, `fetchPredictOperation`, filtrado, 401, 5xx).
- **`FfmpegComponedorTest`** con FFmpeg real y entradas sintéticas: `ffprobe` confirma 15 s, 1080×1920, 30 fps, H.264 y AAC (V23), con clips y con respaldo.
- **Integración** (Veo simulado y un componedor de prueba): V22 (y al día siguiente sí, con el reloj), V24, V25, V26, V27, regenerar, aprobar, descargar, aislamiento (V08) y V39.

## Anexo: cambios tras la revisión del diseño (aceptados)

**Estados, consumo y reintentos**
1. **V6 compatible con filas existentes:** contadores `integer not null default 0` y banderas `boolean not null default false`. `plano.estado` es nulo en el storyboard y en el cierre. Para pasar a `COMPONIENDO` solo cuentan los planos con IA.
2. **Ningún video queda atascado:**
   - `intentos_composicion` se suma al reclamar, antes de componer.
   - Toda excepción al enviar, consultar o guardar un plano cuenta como intento: con 2, `RESPALDO`.
   - Si falta la foto, `ERROR` con devolución.
   - Red de seguridad: 20 minutos desde `generacion_iniciada_en`. Los planos sin terminar pasan a `RESPALDO` y se compone.
3. **Clasificación de errores de Veo:**
   - Sin credencial, modelo inactivo, `CREDENCIAL_INVALIDA` o `CONFIGURACION_INVALIDA`: `RESPALDO` directo y aviso.
   - `NO_DISPONIBLE` al iniciar: cuenta como intento.
   - `NO_DISPONIBLE` al consultar: se sigue consultando hasta los 10 minutos.
4. **Consultar siempre primero.** Un clip listo se usa y se cobra aunque hayan pasado los 10 minutos. Si un plano va a `RESPALDO` por tiempo después de enviarse, se registra igual su costo estimado (la operación ya se cobra).
5. **Sin doble envío:** antes de `iniciar` se suman `intentos` y se fija `enviado_en` en su propia transacción; después se guarda `id_tarea_proveedor` (`varchar(500)`). Cada plano se guarda por separado.
6. **Recomponer no destruye:** `intentos_composicion = 0` al entrar a `COMPONIENDO`. Cada composición usa una clave nueva (`final-{uuid}.mp4`) y la anterior se borra al confirmar. Si una recomposición falla, el video vuelve a `LISTO` con su MP4 anterior y un aviso. `ERROR` y RN-27 solo aplican a la primera composición.
7. **`Video.regenerarPlano`** es la única puerta: la usan `/regenerar` y el cambio de foto en `LISTO`. Verifica el límite y responde 409 `SIN_REGENERACIONES`. Con texto y foto en la misma solicitud, cuenta una sola regeneración.
8. **Reclamo de 5 minutos** (FFmpeg tiene un tope de 2 minutos), renovado antes de ejecutar FFmpeg. Las consultas a Veo y la composición van en métodos programados distintos.

**Veo**

9. Antes de enviar, la foto se **normaliza en Java**: orientación EXIF aplicada, lado mayor de hasta 2160 px, recorte central a 9:16 y JPEG sin metadatos. El respaldo usa la misma foto. Se envía `resizeMode` (por defecto `crop`, leído de `parametros`).
10. **Token:** caché concurrente por cuenta y `private_key_id`; se descarta ante un 401. Cualquier error del endpoint de token, o un JSON sin `private_key`, `client_email` o `project_id`, cuenta como `CREDENCIAL_INVALIDA`.
11. **Secreto en archivo:** si la ruta está vacía o el archivo no existe, devuelve vacío y deja un log solo con la ruta. Se agregan `/credenciales/` y `*service-account*.json` a `.gitignore`.
12. El **costo** se calcula con el `durationSeconds` enviado (`parametros.duracionClipSegundos`) × `costoReferencial`.

**FFmpeg**

13. **`drawtext` con `expansion=none`;** los archivos de texto van en UTF-8, con `\n` y sin salto final. La prueba usa `%`, `\`, `:`, comillas, `¿`, `¡` y `ñ`.
14. **Proceso:**
    - Opciones: `-nostdin -hide_banner -nostats -loglevel error -y`.
    - La salida va a un archivo de log, y sus últimas líneas a `motivo_error`.
    - Al vencer el plazo: `destroyForcibly`, esperar a que termine y recién entonces borrar la carpeta (`Files.createTempDirectory`).
15. **Segmentos:**
    - Respaldo y fondo del local: `scale` a 2160×3840 para cubrir, `crop`, `zoompan` con `d` y `s=1080x1920:fps=30` explícitos, sin `-loop`.
    - Cada segmento termina en `fps=30,setsar=1,format=yuv420p` antes de `concat`.
16. **Degradado del cierre** generado en Java (PNG 1080×1920 con los dos colores), sin la fuente `gradients`.
17. **Tipografía:** sus bytes salen del paquete de nicho y se escriben en la carpeta temporal (`fontfile=fuente.ttf`), sin `getFile()`.
18. **Zona segura asimétrica** (de x = 90 a x = 900).
    - Saltos de línea y tamaño calculados en Java con `FontMetrics` del mismo TTF: hasta 3 líneas, con tamaño mínimo legible.
    - En el cierre, la franja del logo va separada de la de los textos.
19. **FFmpeg en el CI:**
    - El job del backend pasa a `ubuntu-24.04` con `apt-get install ffmpeg`.
    - El gráfico va como argumento de `-filter_complex`.
    - Se agrega `autoreel.ffprobe.ruta` (`FFPROBE_PATH`) y la versión de FFmpeg se registra al arrancar.
20. **Logo:** `scale=w=360:h=360:force_original_aspect_ratio=decrease,format=rgba`.
21. **Caracteres dibujables:** `Texto.tieneCaracteresNoDibujables` pasa a una lista blanca (U+0020–007E, U+00A0–00FF y ‘ ’ “ ” – — … €). Los datos del catálogo o del perfil que se dibujan en el cierre se limpian en lugar de fallar.

**Producto**

22. **Aviso de "listo":** `LayoutNegocio` consulta `GET /api/videos` cada 15 s mientras haya videos en proceso, y muestra "Tu video está listo" con su enlace. La vista expone el estado de cada plano también en `GENERANDO`.
23. **Reproductor:** si la URL firmada vence, vuelve a pedir la vista.
24. **Panel del administrador:**
    - El detalle del negocio muestra el consumo del mes y sus últimos videos (estado, planos en respaldo y `motivo_error`), RF-05.
    - La lista de negocios muestra los videos del mes, RF-02.

### Decisiones del usuario sobre la revisión (2026-11-04)

- **Una regeneración que falla no se cobra:** se descuenta al pedirla y se devuelve si ese plano termina en respaldo por falla del proveedor, conservando el clip anterior. "Reintentar con IA" en un plano que falló sigue la misma regla. Sin IA disponible (sin credencial o modelo inactivo), regenerar y reintentar se ocultan (`iaDisponible`) y el backend responde 409 `IA_NO_DISPONIBLE`.
- **RN-27 se devuelve en el día del error** (día de Lima), una sola vez.
- **El plan Lite sigue con Veo 3.1 Lite** (`veo-3.1-lite-generate-001`, en vista previa en Google Cloud); es un dato editable.

## Estado (2026-11-04)

**Implementado** en la rama `fase-5-generacion`, con el diseño, el anexo y las decisiones del usuario. Casos V22 a V27 cubiertos por pruebas automatizadas, más V08 (video final) y V39; 220 pruebas en total en el backend (10 de integración en `GeneracionIntegracionTest`, con el `VeoGeneradorDeClips` real contra `ServidorVeoSimulado` y un componedor de prueba; `FfmpegComponedorTest` con FFmpeg real para V23; `VeoGeneradorDeClipsTest`). Detalle de lo entregado en `docs/plan-de-desarrollo.md`, sección 7.

Lo que se construyó:
- `ia`: puerto `GeneradorDeClips`, `VeoGeneradorDeClips` (adaptador `VEO`, `predictLongRunning` y `fetchPredictOperation`), `TokenDeGoogle` (JWT RS256 firmado con Nimbus), `FabricaDeProveedores.generadorDeClips` y `videoDisponible`, `SecretosDeConfiguracion` con secretos en archivo (`VEO_CREDENCIAL_ARCHIVO`).
- `video`: `ServicioConsumo` y `consumo_diario` (RN-26, RN-27), `ServicioGeneracion`, `ServicioComposicion`, `ProcesadorDeVideos`, `BibliotecaMusical`, puerto `ComponedorDeVideo` con `FfmpegComponedor`, `PreparadorDeFotos` y `DisenoDeTextos`. Endpoints `aprobar-storyboard`, `planos/{orden}/regenerar`, `aprobar` y `descarga`. Migración `V6__generacion.sql`.
- Paquete de nicho: `musica.json` con 3 pistas de relleno (P-02 sigue pendiente), tipografías OFL por estilo en `fuentes/` y `tipografias` en `plantilla.json` (P-05).
- Frontend: pantalla Generando, revisión con reproductor, regenerar o reintentar con IA, aprobar, descargar; aviso "Tu video está listo" en `LayoutNegocio`; "Mis videos" consulta mientras hay videos en proceso; consumo del mes y últimos videos en el panel del administrador.
- FFmpeg en el CI con apt (`ubuntu-24.04`). Sin `VEO_CREDENCIAL_ARCHIVO` (todavía no hay credencial), todos los planos usan el acercamiento de FFmpeg y el video se entrega igual.

Diferencias con el diseño que se ven en el código:
- **Sin `CargadorDePistas`:** la carga de la música la hace `BibliotecaMusical` (un `ApplicationRunner`), que sube cada pista a `musica/{nicho}/{archivo}` y crea la fila si falta. La pista se elige al aprobar el storyboard, entre las activas del estilo, según el id del video. Sin pista, el video sale con audio en silencio.
- **Dos tareas programadas** en `ProcesadorDeVideos` (clips y composición, cada 10 s, lotes de 5) y un pool de **3** hilos (no 2), para que guiones, clips y composición no se frenen entre sí.
- **Fotos de 1080×1920:** `PreparadorDeFotos` entrega la foto ya recortada a 9:16 en 1080×1920 (no hasta 2160 px de lado mayor), y la misma va a Veo y al respaldo. El acercamiento la escala a 2160×3840 antes de `zoompan`.
- **Clip más corto que su plano:** se alarga repitiendo el último cuadro (`tpad`) antes de recortarlo a su duración.
- **Fondo del cierre con foto del local:** acercamiento suave (5 %) y la foto oscurecida para que se lean los textos; con el degradado, imagen fija.
- **Textos:** hasta 3 líneas, probando tamaños de 76 a 52 px; los textos de los planos empiezan en y = 1100.
- **`autoreel.ffmpeg.hilos`** (2) limita los hilos de codificación. **No hay `autoreel.ffprobe.ruta`:** `FFPROBE_PATH` solo lo lee `FfmpegComponedorTest`.
- **`resizeMode`** sale de `parametros.ajuste` (`crop` en la semilla).
- **Columnas extra en V6:** `video.aviso_revision` (aviso de una recomposición fallida), `plano.motivo_respaldo` (`SIN_CREDENCIAL`, `CREDENCIAL_INVALIDA`, `CONFIGURACION_INVALIDA`, `PROVEEDOR_NO_DISPONIBLE`, `CLIP_FALLIDO`, `TIEMPO_AGOTADO`, `MODELO_INACTIVO`) y `plano.regeneracion_en_curso` (para devolver una regeneración que falla).
- **Foto que falta:** no pasa a `ERROR` de inmediato; cuenta como intento fallido del plano y, como el respaldo también la necesita, la composición falla y el video termina en `ERROR` con la devolución del consumo (RN-27).
- **Cambiar la foto sin IA disponible:** se usa con el acercamiento y solo se recompone, sin gastar regeneraciones.
- **Descarga:** si el video no tiene MP4 final, 409 `VIDEO_NO_DISPONIBLE`. El archivo se llama `autoreel-{producto}-{id}.mp4` y el enlace dura 15 minutos. Se puede descargar en `LISTO`, `APROBADO`, `PROGRAMADO` y `PUBLICADO`.
- **Sin credencial o con el modelo inactivo**, el aviso al administrador (RN-57) se registra como `CONFIGURACION_INVALIDA`, una vez por pasada.
- **Panel del administrador:** el detalle muestra los últimos 20 videos.
