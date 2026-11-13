# ADR 0016 — Composición del video con FFmpeg como proceso externo

- **Estado:** Aceptada; el acercamiento de respaldo para los clips dejó de usarse (ADR 0020); fundidos entre planos y cierre sin degradado (ADR 0021)
- **Fecha:** 2026-11-04

## Contexto

En la fase 5, el sistema une los clips (o las fotos de respaldo) con los textos, el cierre y la música en el video final (secciones 7.4 y 8.3). El resultado tiene reglas estrictas:

- **Formato fijo** (RN-31, V23): vertical 1080×1920, 30 cuadros por segundo, H.264 con AAC y **15 s exactos**.
- **Textos legibles y completos.** Cada plano lleva su texto dentro de la zona segura de Reels, sin tapar los botones del borde derecho. Los textos los escribe el negocio o la IA: pueden traer `%`, `:`, comillas, `\`, `¿`, `¡` o `ñ`, y su largo varía.
- **El cierre lo arma el sistema:** la foto del local o un degradado con los dos colores de marca, el logo, el precio literal, el llamado, el distrito y el WhatsApp.
- **Música** de la biblioteca del nicho, con fundido de salida, y un fundido hacia el cierre.
- **Plan de respaldo** (sección 8.5): un plano sin clip usa su foto con un acercamiento lento, y el video se entrega igual.
- **Fotos de celular.** Muchas vienen giradas por la orientación EXIF, con cualquier proporción y con metadatos (incluida la ubicación).

La sección 19 ya nombra FFmpeg para la composición. Faltaba decidir cómo se usa desde el backend en Java y qué trabajo se hace en Java y qué en FFmpeg, para que el resultado sea el mismo en la PC, en el CI y en producción.

## Decisión

**El backend ejecuta el binario de FFmpeg como proceso externo, detrás del puerto `ComponedorDeVideo`. Java prepara todo lo que depende de la versión de FFmpeg (las fotos, el degradado y la medida de los textos), y FFmpeg solo compone.**

1. **Puerto y adaptador.** `ComponedorDeVideo` (`video/dominio`) recibe una `Composicion` (los planos con su clip o su foto, sus segundos y su texto, el cierre, la música y la tipografía) y devuelve el MP4. Lo implementa `FfmpegComponedor` (`video/infraestructura`), con `autoreel.ffmpeg` (`ruta` desde `FFMPEG_PATH`, por defecto `ffmpeg`; `tiempo-maximo` de 2 minutos; `hilos` de codificación, 2). `ServicioComposicion` arma la entrada y guarda el resultado.
2. **Proceso externo con `ProcessBuilder`.**
   - Cada composición trabaja en su propia carpeta temporal (`Files.createTempDirectory`), con **rutas relativas**, que se borra al terminar.
   - Opciones fijas: `-nostdin -hide_banner -nostats -loglevel error -y`. El gráfico de filtros va como argumento de `-filter_complex`.
   - La salida de FFmpeg va a `ffmpeg.log` en la carpeta temporal. Si FFmpeg termina con error, sus últimas 3 líneas van en el mensaje de `ErrorDeComposicion`, que queda en el registro del backend; el negocio ve un `motivo_error` fijo y en español.
   - Si pasa el tiempo máximo, el proceso se termina con `destroyForcibly`, se espera a que salga y recién entonces se borra la carpeta.
   - Al arrancar, `FfmpegComponedor` registra la versión de FFmpeg, o avisa si no lo encuentra.
3. **Salida.** H.264 (`libx264`, `-preset veryfast`, `-crf 23`, `yuv420p`), AAC, `-movflags +faststart` y la duración de la plantilla. Cada segmento se normaliza a 1080×1920 y 30 fps antes del `concat`; hay un fundido de 0,4 s hacia el cierre, y la música se recorta con un fundido de salida de 1,5 s. Sin pista, el audio es silencio (`anullsrc`).
4. **Las fotos se normalizan en Java** (`PreparadorDeFotos`, con los lectores de metadatos de TwelveMonkeys, que ya estaban en el proyecto): aplica la orientación EXIF, recorta al centro a 9:16 y guarda un JPEG de 1080×1920 sin metadatos. **La misma foto** va a Veo y al respaldo de FFmpeg, así el resultado no depende de cómo cada versión de FFmpeg trate el EXIF. El degradado del cierre también se genera en Java (`PreparadorDeFotos.degradado`, PNG de 1080×1920), sin la fuente `gradients` de FFmpeg.
5. **Los textos se miden en Java y FFmpeg solo los dibuja.**
   - `drawtext` no ajusta ni recorta el texto. `DisenoDeTextos` reparte cada texto en líneas con `FontMetrics` de la **misma tipografía TTF** que usa FFmpeg, y baja el tamaño hasta que cabe en el ancho útil (de x = 90 a x = 900) con un máximo de líneas.
   - Cada línea va en su archivo UTF-8 y se dibuja con `drawtext textfile=… expansion=none`: ningún `%`, `:` o comilla del usuario rompe el filtro.
   - La tipografía del estilo sale del paquete de nicho como bytes y se escribe en la carpeta temporal como `fuente.ttf` (dentro del JAR no hay ruta de archivo).
   - `Texto.tieneCaracteresNoDibujables` es una lista blanca (U+0020–007E, U+00A0–00FF y ‘ ’ “ ” – — … €), y los datos del perfil o del catálogo que van al cierre se limpian con `Texto.soloDibujable`.
6. **Versiones por entorno.**
   - **PC de desarrollo:** FFmpeg 9, instalado con winget. Si no queda en el `PATH`, `FFMPEG_PATH` apunta al ejecutable (`.env.example`).
   - **CI:** el job del backend corre en `ubuntu-24.04` (ya no en `ubuntu-latest`) e instala el FFmpeg de apt; el paso imprime su versión.
   - **Producción:** FFmpeg irá dentro de la imagen Docker del backend (futuro, fase 8).

## Alternativas consideradas

- **Enlaces nativos de FFmpeg para Java** (por ejemplo, JavaCV). Evitan el proceso externo, pero agregan bibliotecas nativas grandes por sistema operativo, y un fallo nativo puede tumbar la JVM del backend. Con un proceso aparte, un FFmpeg que falla o se cuelga solo afecta a esa composición.
- **Una biblioteca que envuelva la línea de comandos.** Arma los argumentos con métodos, pero es una dependencia más para lo que `ProcessBuilder` ya hace, y el gráfico de filtros igual se escribe a mano.
- **Un servicio de edición de video en la nube** (de AWS o de terceros). Quita la carga de CPU del backend, pero suma otro proveedor, otra credencial y un costo por video, y complica las pruebas locales.
- **Dejar todo a FFmpeg** (orientación de la foto, degradado con `gradients`, partir el texto). Es menos código Java, pero el resultado cambia entre la versión de la PC y la del CI, y `drawtext` no sabe repartir líneas: un texto largo se saldría de la pantalla.
- **Dibujar los textos como imágenes PNG en Java** y superponerlas. Da control total, pero suma un paso por texto. `drawtext` con la medida hecha en Java da el mismo resultado con la misma tipografía.

## Consecuencias

- **Positivas:**
  - FFmpeg es la herramienta estándar para esto y no tiene costo por video.
  - El resultado se prueba de verdad: `FfmpegComponedorTest` ejecuta FFmpeg real con entradas sintéticas, y `ffprobe` confirma 15 s, 1080×1920, 30 fps, H.264 y AAC (V23), con clips, con respaldo y con textos difíciles.
  - Las fotos y los textos se ven igual con cualquier versión de FFmpeg, porque Java los prepara.
  - El puerto `ComponedorDeVideo` permite cambiar FFmpeg por otra herramienta sin tocar la generación ni la revisión. Las pruebas de integración usan un componedor de prueba.
  - Un FFmpeg que falla o se cuelga no tumba el backend. Si falla la primera composición dos veces, el video pasa a `ERROR` y se devuelve el consumo del día (RN-27); si falla una recomposición, el video vuelve a `LISTO` con su MP4 anterior.
- **Negativas y riesgos:**
  - **Hay que instalar FFmpeg en cada entorno.** Sin él, el backend arranca con un aviso, pero ningún video se puede componer. `./mvnw verify` también lo necesita: `FfmpegComponedorTest` usa `FFMPEG_PATH` y `FFPROBE_PATH`, o los del `PATH`.
  - **Las versiones no son iguales:** FFmpeg 9 en la PC y el de los repositorios de Ubuntu 24.04 en el CI. Por eso el gráfico usa solo filtros comunes y la versión se registra al arrancar. La versión de producción se fija con la imagen Docker (futuro, fase 8).
  - **Usa CPU del mismo servidor que la API.** Se limita con `hilos: 2`, con el tope de 2 minutos y con un pool de 3 hilos para las tareas programadas (guiones, clips y composición), para que una composición no frene lo demás. Con mucho volumen, la composición tendría que ir a otro servidor.
  - **Disco temporal.** Cada composición escribe sus clips, fotos, música y salida en una carpeta temporal mientras dura.
  - La medida de los textos depende de que `DisenoDeTextos` y `drawtext` usen el mismo TTF; una tipografía nueva en el paquete de nicho se mide sola, pero conviene revisarla con un video real.
- **Cuándo revisar esta decisión:** si la composición frena la API o el volumen exige más de una composición a la vez, si se separa en un servicio aparte, o si la imagen Docker de producción no puede llevar FFmpeg.
