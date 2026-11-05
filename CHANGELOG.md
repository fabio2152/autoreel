# Changelog

Cambios de AutoReel por fase. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [Proveedores de Google] - 2026-11-05

### Agregado

- **Dos caminos de Google para texto y video**, elegibles desde la base de datos sin programar (RN-56):
  - **Google AI Studio (activo):** una sola clave (`GEMINI_API_KEY`) para el guion con Gemini y para los clips con **Gemini Omni Flash** (`gemini-omni-1.2-flash`, adaptador `GEMINI_OMNI`), por la API de interacciones en modo síncrono (con segundo plano como opción).
  - **Google Cloud (listo, inactivo):** una cuenta de servicio (`GOOGLE_CLOUD_CREDENCIAL_ARCHIVO`) para los clips con Veo y para el guion con **Gemini en Vertex AI** (adaptador `GEMINI_CLOUD`, endpoint `global`).
- El costo de un clip usa los segundos que informa el proveedor (tokens de video de Omni) cuando vienen.
- **Migración `V7`:** los dos modelos nuevos, Veo y Gemini Cloud inactivos con el secreto `autoreel/google-cloud`, y el plan Lite con Gemini Omni Flash para el video. Los videos ya creados conservan su modelo (RN-54).
- **Pruebas** (234 en total): adaptadores de Omni y de Gemini en Google Cloud contra servidores simulados, y la generación de punta a punta con Omni.

### Cambiado

- La credencial de Google Cloud se llama `GOOGLE_CLOUD_CREDENCIAL_ARCHIVO` (la variable anterior, `VEO_CREDENCIAL_ARCHIVO`, sigue funcionando como respaldo) y sus propiedades pasan a `autoreel.ia.google-cloud`.
- El plazo total de la generación cuenta desde el último envío de cada plano, no desde la aprobación: con un proveedor síncrono, un video que espera su turno no pierde sus clips.

### Decisiones

- **Por ahora, todo con Google AI Studio** (decisión del usuario): la clave se crea en https://aistudio.google.com/apikey, en un proyecto con facturación activa porque Omni no tiene plan gratuito.

## [Fase 5 — Generación] - 2026-11-04

### Agregado

- **"Crear mi video"** (RF-22, RN-26, V22): aprobar el storyboard usa el video del día (día de Lima). Con el video del día ya usado, el botón se desactiva con "Ya usaste tu video de hoy. Mañana tienes uno nuevo."; al día siguiente se puede otra vez. Los videos no usados no se acumulan.
- **Generación de los clips** (RF-23) con Veo 3.1 Lite en Google Cloud (Vertex AI), a partir de la foto de cada plano y su movimiento de cámara. La credencial es el JSON de una cuenta de servicio, leído del archivo que indica `VEO_CREDENCIAL_ARCHIVO`, nunca del código ni de la base de datos (RN-55).
- **Plan de respaldo** (RN-30, V24): si un clip falla dos veces, no llega en 10 minutos o no hay credencial, ese plano usa un acercamiento lento de la foto hecho con FFmpeg y el video se entrega igual. Ninguna generación dura más de 20 minutos.
- **Composición del video final** (RN-31, V23): vertical 1080 × 1920, 30 cuadros por segundo, H.264 con AAC y 15 segundos exactos.
  1. Los textos de cada plano van dentro de la zona segura de Instagram, con la tipografía del estilo del negocio y un tamaño que se ajusta para que quepan.
  2. Fundido hacia el cierre: foto del local (o un degradado con los colores de la marca), logo, precio, llamado, distrito y WhatsApp.
  3. Música del estilo del negocio, con fundido al final.
  4. Las fotos se enderezan según la cámara y se recortan en vertical antes de usarse.
- **Pantalla "Creando tu video"** con mensajes que cambian y el avance de cada plano. Se puede salir: el video espera en "Mis videos", y la aplicación avisa "Tu video está listo".
- **Revisión del video** (RF-24): reproductor vertical y, por plano:
  1. "Regenerar este plano" (hasta 3 por video en el plan Lite, RN-28, V26). Una regeneración que falla no se cuenta y se conserva el clip anterior.
  2. "Reintentar con IA" en los planos que usaron el respaldo.
  3. Editar el texto o el precio vuelve a armar el video sin gastar regeneraciones (RN-29, V25).
  4. Cambiar la foto crea un clip nuevo y usa una regeneración.
- **Aprobar y descargar** (RF-25): "Aprobar mi video" y "Descargar" el MP4 con un nombre de archivo propio.
- **Devolución del video del día** (RN-27, V27): si el video no se puede armar por una falla del sistema, pasa a error y se devuelve el video del día. Si falla un cambio posterior, se conserva el video anterior con un aviso.
- **Costo de cada clip** por segundo (RN-32); el video registra Gemini y Veo 3.1 Lite como modelos usados en el plan Lite (V39).
- **Biblioteca de música** por nicho y estilo, cargada desde el paquete de nicho al arrancar (`musica.json`).
- **Tipografías por estilo** con licencia OFL en el paquete de nicho: Nunito (casero), Playfair Display (elegante) y Fredoka (divertido).
- **Panel de administración:** la lista muestra los videos del mes de cada negocio (RF-02) y el detalle, el consumo del mes y sus últimos videos con su estado, los planos con respaldo y el motivo de error (RF-05).
- **Migración `V6`:** `pista_musical`, `consumo_diario`, columnas nuevas de `video` y `plano`, y los datos del modelo Veo.
- **FFmpeg** como requisito del backend (`FFMPEG_PATH`); el CI lo instala con apt.
- **Pruebas** (220 en total): composición con FFmpeg real (V23), adaptador de Veo contra un servidor simulado y 10 pruebas de integración de la generación (V22, V24, V25, V26, V27, V39, regeneración fallida, recomposición fallida, sin credencial, 10 minutos sin clip y aislamiento entre negocios, V08).

### Pendiente en fases siguientes

- **Publicar en Instagram** el video aprobado: fase 6. Hoy el video aprobado muestra "Muy pronto podrás publicarlo en Instagram desde aquí".
- **Editar la descripción y los hashtags:** todavía solo se muestran.
- **Aviso al administrador** cuando falla una integración de IA (RN-57) y beneficios temporales en el consumo diario: fase 7.
- **Precio por segundo de Veo:** sin confirmar en la página oficial; es un dato editable que se ajusta en la fase 7.
- **FFmpeg en el servidor y credenciales desde AWS Secrets Manager:** fase 8.
- **Música definitiva** (P-02): las tres pistas actuales son de relleno.
- **Prueba con Veo real:** cuando haya una cuenta de servicio de Google Cloud.

### Decisiones

- **Veo 3.1 en Google Cloud (Vertex AI):** Google cerró los Veo 3.1 *preview* de la API de Gemini el 22/10/2026. El plan Lite sigue con Veo 3.1 Lite (`veo-3.1-lite-generate-001`, en vista previa en Google Cloud); Veo 3.1 y Veo 3.1 Fast son GA. El modelo es un dato editable.
- **Sin credencial todavía:** el adaptador se probó contra un servidor simulado y, sin `VEO_CREDENCIAL_ARCHIVO`, todos los planos usan el acercamiento de FFmpeg.
- **Una regeneración que falla no se cobra** y conserva el clip anterior. Sin IA disponible, no se ofrece regenerar.
- **El video del día se devuelve en el día del error** (día de Lima), una sola vez.
- **Música y tipografías dentro del paquete de nicho**, como el resto del contenido del nicho.
- **FFmpeg instalado en la PC con winget** y en el CI con apt; en producción, en la imagen del servidor (fase 8).

## [Fase 4 — Guion] - 2026-11-04

### Agregado

- **Propuesta del video (storyboard)** (RF-21, RF-22): al terminar el chat, "Ver mi propuesta" abre el storyboard. Mientras se escribe el guion, "Estoy preparando tu propuesta…"; si tarda más de un minuto, un aviso de que se puede salir y volver.
  1. Gancho, producto y relleno: cada uno con su foto (detalle, entera, porción), el movimiento de cámara en palabras simples y su texto.
  2. Cierre armado por el sistema, sin IA: el precio tal como lo escribió el usuario (editable, hasta 40 caracteres; si es más largo, se propone recortado sin dejar la frase cortada), el llamado según los canales del negocio, el distrito y el logo sobre la foto del local o los colores de la marca.
  3. Descripción de la publicación y hashtags.
- **Edición del storyboard:** cambiar los textos con contador de palabras (V21: un texto por encima del límite muestra un error), cambiar la foto de un plano por otra del producto o por una nueva solo para ese video, y descartar la propuesta. "Crear mi video" se ve, pero se activa en la fase 5.
- **Mis videos:** nueva pestaña con las propuestas del negocio, las más recientes primero.
- **Guion con IA** (Gemini) a partir de las respuestas del chat, el perfil y la descripción del producto, con hasta 3 intentos. El modelo de texto se elige desde la base de datos (fábrica de proveedores) y la clave se lee de `GEMINI_API_KEY`, nunca del código ni de la base de datos (RN-55).
- **Validador del guion** (RN-21 a RN-24, V18, V19): planos y fotos de la plantilla, textos dentro de su máximo de palabras, sin precios ni promociones que el usuario no haya escrito (también en palabras, como "45 soles", y en los hashtags), sin palabras prohibidas del nicho ni del negocio, sin emojis ni caracteres de control en pantalla, textos de hasta 200 caracteres, descripción sin hashtags dentro y, con los hashtags, de hasta 600 caracteres y entre 5 y 8 hashtags con uno del distrito.
- **Guion de respaldo** (RN-25, V20): si la IA no responde, no hay clave o devuelve un guion inválido tres veces, el usuario igual recibe su propuesta, armada con sus respuestas y textos fijos del paquete de nicho, con un aviso para que la revise. Si el negocio prohibió una palabra de los textos fijos o de los hashtags base, se omite sin descartar el resto.
- **Paquete de nicho Pastelería:** plantilla de 4 planos, movimientos de cámara, vocabulario por estilo y las instrucciones del guion (`plantilla.json`, `movimientos.json`, `vocabulario.json`, `guion.md`), validados al arrancar.
- **Registro de costos** (RN-32): cada respuesta del modelo de texto guarda su costo.
- **El video guarda el plan y los modelos** con los que se creó (RN-54).
- **Migración `V5`:** `video`, `plano` y `registro_costo`.
- **Pruebas** (193 en total): validador del guion, guion de respaldo, recorte del precio del cierre, adaptador de Gemini contra un servidor simulado y 9 pruebas de integración del storyboard (V18, V19, V20, V21, sin clave, proveedor caído, proceso interrumpido, aislamiento y "Mis videos").

### Pendiente en fases siguientes

- **"Crear mi video"** (aprobar el storyboard, consumir el video del día con RN-26 y V22) y la generación de los clips: fase 5.
- **Editar la descripción y los hashtags** del storyboard: todavía solo se muestran.
- **Aviso al administrador** cuando falla una integración de IA (RN-57): por ahora queda en el registro de la aplicación; la notificación llega en la fase 7.
- **Credenciales desde AWS Secrets Manager:** fase 8.

### Decisiones

- **El cierre lo arma el sistema**, no la IA: así el precio siempre es el que escribió el usuario.
- **Sin clave de Gemini todavía:** el adaptador se probó contra un servidor simulado y, sin `GEMINI_API_KEY`, la propuesta usa el guion de respaldo.
- **"Mis videos" desde esta fase**, como base del historial de la fase 5.
- **Sin temporadas ni fechas especiales** en el guion, igual que en el chat (decisión de la fase 3).

## [Fase 3 — Chat] - 2026-11-04

### Agregado

- **Chat del video del día** (RF-19, RF-20): la pantalla principal del negocio. Preguntas fijas, sin IA, definidas en el paquete de nicho (`chat.json`, RN-16):
  1. Tipo de video: producto estrella, promoción, tortas por encargo o recién salido del horno.
  2. Producto: elegirlo del catálogo (solo activos y con sus tres fotos, V11) o subir uno nuevo en el momento.
  3. Qué lo hace especial hoy, con la descripción del producto como sugerencia.
  4. Precio o promoción: opcional, y obligatorio en una promoción (RN-20, V17).
  5. Algo más que quieras que se diga: opcional.
- **Agrupación de mensajes** (RN-17, V15): los mensajes enviados con menos de 4 segundos de diferencia forman una sola respuesta. El backend lleva el reloj; el frontend le avisa cuándo revisar.
- **Detección de preguntas** (RN-18, V16): si la respuesta tiene "?" o "¿", el chat recuerda que no responde preguntas y repite la suya. También pide más detalle si la respuesta es muy corta y que la resuma si pasa de 500 caracteres.
- **Producto subido desde el chat** (RN-14, V14): el usuario decide si lo guarda en su catálogo o lo usa solo para ese video. Si no lo guarda, no aparece en el catálogo.
- **Una conversación en curso por negocio** (RN-19): al volver, "¿Continuamos donde quedamos?" o "Empezar de nuevo".
- **Sin onboarding no se crean videos** (RN-09, V10).
- **Resumen final** con las respuestas, que serán la entrada del guion de la fase 4, y "Crear otro video".
- **Migración `V4`:** `conversacion`, `mensaje_chat` y `producto.en_catalogo`.
- **Pruebas** (125 en total): agrupación de mensajes, evaluación de respuestas, guion del chat y 16 pruebas de integración del chat (V10, V11, V14, V15, V16, V17, RN-19, aislamiento y concurrencia).

### Corregido

- En el celular, la barra de navegación del negocio aparecía arriba y tapaba el logo: ahora queda al pie.

### Decisiones

- **Sin fechas especiales ni temporadas en el flujo del video**, por decisión del dueño del producto: el chat no ofrece "Fecha especial" ni ordena el carrusel por temporada. V13 queda fuera del MVP y se retomará junto con el calendario de fechas especiales (RF-31).

## [Fase 2 — Negocio] - 2026-11-04

### Agregado

- **Paquete de nicho Pastelería** (`PaqueteNicho` / `PaquetePasteleria`): estilos, preguntas del onboarding y temporadas en `resources/nichos/pasteleria/`, validados al arrancar. Cálculo de temporadas vigentes y fechas especiales próximas (15 días), listo para el chat de la fase 3.
- **Almacenamiento de archivos** (`AlmacenDeArchivos` → `S3Almacen`):
  - Subida directa desde el navegador con URL firmada (tipo y tamaño exactos).
  - Verificación del contenido real (formato, medidas, transparencia del logo, orientación de la cámara, límites contra imágenes enormes o animadas).
  - Copia a una clave definitiva que nadie puede sobrescribir.
  - SeaweedFS en docker compose para desarrollo; Amazon S3 en producción sin cambiar código.
- **Onboarding y perfil** (RF-16, RF-17, RN-09): asistente de tres pasos en pantalla completa (marca, negocio, toque), que se retoma donde quedó. Logo PNG, dos colores con "Tomar del logo", estilo, preguntas del paquete, foto del local opcional. Edición en Ajustes.
- **Catálogo de productos** (RF-18, RN-10 a RN-13): nombre, descripción, temporadas y tres fotos (entero, detalle, porción) con imagen de ejemplo e indicaciones. Productos incompletos marcados; archivar y reactivar.
- **Fotos preparadas en el navegador:** se aplica la orientación de la cámara, se reducen a 2560 px y se quitan los metadatos (GPS).
- **Ajustes** del negocio: perfil y cambio de contraseña (RF-15).
- **Panel de administración:** el detalle del negocio muestra su perfil y su catálogo (RF-05).
- **Negocio de ejemplo** en el perfil `dev` (sección 16), con imágenes de relleno generadas.
- **Migración `V3`:** `perfil_negocio`, `producto` y `foto_producto`.
- **Pruebas** (93 en total): paquete de nicho y temporadas, validación de respuestas, verificador de imágenes (incluidos logos de paleta y PNG animados), adaptador S3 contra SeaweedFS real, e integración para V08, V11, V12, aislamiento de archivos entre negocios, reintento tras un guardado fallido, onboarding y catálogo.

### Pendiente en fases siguientes

- **V10, V13 y V14** se verifican con el chat (fase 3); la regla RN-09 ya está guardada en `onboardingCompleto`.
- **RN-14** (producto subido desde el chat sin guardarlo) llega con el chat.
- **RN-15** (detección de rostros con Rekognition) en la fase 9.
- **Limpieza de subidas abandonadas:** regla de expiración del bucket en la fase 8.

## [Fase 1 — Base] - 2026-11-03

### Agregado

- **Repositorio y monorepo:** `backend/` (Spring Boot 4.1.2, Java 21, Maven wrapper, paquete `com.autoreel`) y `frontend/` (React 19, TypeScript, Vite 8, Tailwind CSS 4).
- **Entorno local:** `docker-compose.yml` con PostgreSQL 17 y `.env.example` con las variables de todas las fases.
- **Base de datos con Flyway:**
  - `V1`: negocios y usuarios.
  - `V2`: modelos de IA, planes y suscripciones.
  - Datos iniciales: Gemini (texto), Veo 3.1 Lite (video) y el plan Lite (sección 16).
- **Autenticación (RF-01, RF-13, RF-14):**
  - JWT en una cookie `HttpOnly`, con protección CSRF y sesión de 8 horas.
  - Roles ADMIN y NEGOCIO verificados en el backend.
  - Contraseña temporal de 12 caracteres que vence a las 48 horas y obliga a crear una propia.
  - Reglas de contraseña (RN-04).
  - Bloqueo de 15 minutos tras 5 intentos fallidos.
  - Cierre de sesiones al reiniciar la contraseña o suspender el negocio (RN-01 a RN-08).
- **Panel de administración:**
  - Lista de negocios con búsqueda, filtro y color por estado de pago (RF-02, parcial).
  - Crear negocio con nicho, plan, monto mensual y fecha del primer pago, mostrando la contraseña temporal una sola vez (RF-03, RN-41).
  - Detalle del negocio (RF-05, parcial).
  - Reinicio de contraseña (RF-04).
  - Suspender y reactivar (RF-06, parcial).
- **Administrador inicial** creado desde `ADMIN_EMAIL` y `ADMIN_PASSWORD`.
- **Errores de la API** con la forma `{"codigo", "mensaje"}` en español, incluidos los de seguridad.
- **Documentación OpenAPI** con springdoc, solo en el perfil `dev`.
- **Endurecimiento del inicio de sesión:**
  - Los correos inexistentes se bloquean igual que las cuentas reales y responden en el mismo tiempo, para no revelar qué correos están registrados.
  - Los intentos simultáneos se cuentan uno a uno (bloqueo de fila).
  - El límite de 72 bytes de BCrypt se valida como regla de contraseña.
  - Los errores estándar de Spring MVC conservan su estado HTTP.
- **Frontend:** si la sesión termina en el servidor, la aplicación vuelve al inicio de sesión; los fallos de red se muestran con un mensaje en español y la opción de reintentar.
- **Pruebas** (45):
  - Reglas de contraseña, generador de contraseñas temporales, usuario y cálculo del estado de pago.
  - Integración con PostgreSQL real (Testcontainers) para V01 a V07, V09 y V33, bloqueo y su duración, desbloqueo al reiniciar, correos inexistentes, CSRF y datos iniciales.
- **CI con GitHub Actions** en cada push y pull request: compilación y pruebas del backend; lint y build del frontend.
- **Documentación:** `README.md`, `CLAUDE.md` y este changelog. `docs/` es local y no se versiona.

### Pendiente en fases siguientes

- **RF-02:** la columna "videos del mes" muestra "—" hasta la fase 5.
- **RF-05:** pagos, catálogo, videos y consumo aparecen como "Disponible en la fase N".
- **RF-06:** el cambio de plan llega en la fase 7.
