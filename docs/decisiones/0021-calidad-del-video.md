# ADR 0021: Calidad del video: guion publicitario, cierre de la IA, cámara 3D y fundidos

- **Estado:** Aceptada
- **Fecha:** 2026-11-12
- **Modifica:** las secciones 7.1 (fondo del cierre sin foto del local), 7.4 (cierre y transiciones), 7.5 (movimientos y prohibiciones), 8.0, 8.1, 8.2, 8.5 y 9.2 de la especificación, RNF-09, RN-21, RN-22, RN-25, RN-27, V19 y V20; el ADR 0014 (punto 6: el reclamo vencido ya no usa el respaldo); el ADR 0016 (la unión con corte directo y el degradado del cierre); y el ADR 0020 (el guion de respaldo ya no se mantiene).

## Contexto

El usuario revisó un video real creado con el plan Lite (Gemini para el guion y Gemini Omni Flash para los clips) y encontró tres problemas. La música estuvo bien y no se tocó.

1. **Frases feas y copiadas.** Ese video salió del **guion de respaldo** porque Gemini respondió con un error de facturación (402). El respaldo copiaba lo que el negocio escribió en el chat, recortado: en pantalla apareció "Fruta de estacion", sin tilde. Además, aunque Gemini hubiera respondido, `guion.md` no le pedía reescribir en tono publicitario, no le prohibía copiar literal y no traía ejemplos.
2. **Un cierre que mostraba una orden.** El cierre lo armaba el sistema con la respuesta del precio **tal cual**. El negocio había escrito "no menciones precio solo que estan recien salidas del horno", y eso apareció en grande como si fuera el precio. Pasaba con o sin Gemini: la IA nunca interpretaba el precio.
3. **Clips que solo se acercaban o se alejaban.** Los clips sí eran de IA, pero el catálogo de `movimientos.json` solo tenía acercamientos, alejamientos, un deslizamiento "de unos centímetros" y efectos, y prohibía girar más de 15 grados. El prompt de Omni era pobre y Veo recibía solo la frase del movimiento. El usuario quiere una **animación 3D de cámara desde la foto**: órbita alrededor de la torta, macro de detalle, grúa.

La investigación y la propuesta están en el historial de la rama `mejora-calidad-video`. El usuario tomó estas decisiones el 2026-11-12.

## Decisión

### 1. Guion publicitario, sin copiar

`guion.md` (paquete de nicho) pasa a ser el de un **copywriter publicitario** de la pastelería:

- Las respuestas del negocio son **indicaciones**, no textos para copiar. Una instrucción ("no menciones precio") se cumple y nunca se muestra. Si la instrucción dice **qué comunicar** ("solo di que están recién salidas del horno"), ese mensaje es el material del gancho o de la frase del cierre, bien escrito.
- Cada plano tiene su papel: el 1 engancha, el 2 presenta el producto y el 3 remata el antojo. Las frases son sensoriales, con buena ortografía y tildes, y hay ejemplos buenos y malos que no inventan sabores.
- Siguen todas las reglas anteriores (RN-21 a RN-24).

### 2. El cierre lo escribe la IA

El esquema JSON de la respuesta suma el objeto `cierre`: `{precio: string|null, frase: string, ubicacion: string|null}`. El llamado a la acción sigue saliendo de la plantilla según los canales del negocio.

- **`precio`:** solo si el negocio escribió un precio o una promoción real. La IA lo resume (por ejemplo, "S/ 65 la torta entera"), con los **mismos números y moneda** de la respuesta del precio. Si lo escrito no trae números (una instrucción, o nada), va `null`.
- **`frase`:** siempre. Es un gancho de hasta 6 palabras y 40 caracteres que se muestra en grande cuando no hay precio ("Recién salidas del horno").
- **`ubicacion`:** opcional. Es un lugar más preciso que el distrito, tomado de lo que dijo el negocio ("Plaza Lima Sur, Chorrillos"). Si falta, se usa el distrito.

`ValidadorDeGuion` lo revisa, y sus errores vuelven al modelo en el siguiente intento, como antes:

- El precio solo puede tomar números de la respuesta del precio, no de la descripción del producto.
- Un precio real con moneda no se puede omitir. Un texto sin números no puede ser precio.
- La frase respeta sus límites y no lleva precios, promociones inventadas ni palabras prohibidas.
- Cada palabra de la ubicación debe estar en los datos del negocio.

El cierre guarda la frase en la columna nueva `plano.frase` (migración `V9`). `Plano.textoPrincipalDelCierre()` devuelve el precio o, si no hay, la frase. El negocio puede editar ambos en el storyboard y en la revisión.

### 3. Sin guion de respaldo

El guion de respaldo copiaba textos del negocio, que es justo lo que se quiere evitar. **Ya no existe**: se borraron `GuionDeRespaldo` y `MotivoRespaldo`, y los textos de respaldo de `plantilla.json`.

- **Si el proveedor de texto falla** (un `ErrorDeProveedor`, una falla inesperada del adaptador, sin credencial o con el modelo inactivo), el video **sigue en `GUION_PENDIENTE`** y se vuelve a intentar solo:
  - cada **45 s** (`ESPERA_ENTRE_INTENTOS`, con el mismo `guion_reclamado_hasta`);
  - durante **5 minutos** (`PLAZO_DE_REINTENTOS`), contados desde `video.guion_iniciado_en`, la columna nueva de `V9`: la creación o el último "Reintentar". En los videos viejos se usa la fecha de creación.
- **Vencido el plazo**, el video pasa a `ERROR` con "No pudimos escribir tu guion con IA. Inténtalo de nuevo en un rato."
- **Si las 3 respuestas no pasan el validador**: `ERROR` con "No logramos escribir un guion que cumpla las reglas. Inténtalo de nuevo."
- **Si el producto ya no existe o no tiene fotos**: `ERROR` **definitivo** (columna `video.error_definitivo`, `V9`). No se llama al modelo, no se cobra y no se ofrece "Reintentar".
- **Si falla algo nuestro** (no del proveedor): `ERROR` con "Tuvimos un problema al preparar tu guion. Inténtalo de nuevo." No se reintenta solo, para no pagar el guion en un ciclo.
- **Si vence el reclamo de un proceso cortado** (por ejemplo, un reinicio), el siguiente ciclo vuelve a intentar el guion con la IA, en lugar de usar el respaldo.
- En todos estos casos el video del día **no se consumió**: se consume al aprobar el storyboard (RN-26).

**"Reintentar" también sirve en la etapa del guion.** El botón y la ruta de PR #12 (`POST /api/videos/{id}/reintentar`) aceptan un video en `ERROR` que falló al escribir su guion: sin consumo, sin planos y sin error definitivo (`Video.falloEnElGuion`).

- El video vuelve a `GUION_PENDIENTE`, con los intentos en 0 y un plazo nuevo de 5 minutos (`Video.reintentarGuion`).
- **No usa el video del día**, y la pantalla lo dice: "Volveremos a escribir tu propuesta. No usa tu video de hoy."
- El reintento de un video que falló **al generarse** no cambia: vuelve a consumir el video del día (`generacion.reintentoUsaVideoDelDia`).

### 4. Catálogo de movimientos 3D y prompt en el paquete de nicho

`movimientos.json` sigue siendo una **lista cerrada** (ADR 0003): Gemini elige un id y no escribe la toma. Cambia esto:

- **Movimientos nuevos de cámara 3D:**
  - `ORBITA_FRONTAL`: arco de 60°.
  - `ARCO_HEROICO`: toma baja, arco de 45°.
  - `MACRO_DETALLE`: entra hasta la textura, con un arco de 20°.
  - `GRUA_REVELADO`: sube desde la base y revela la decoración de arriba.
  - `PARALAJE_LATERAL`: desplazamiento lateral con arco de 45°.
  - `DIAGONAL_ENFOQUE`: pasa en diagonal con cambio de foco.
- **Los 7 ids anteriores se conservan**, porque los videos guardados y "Reintentar" los usan.
  - Los acercamientos, alejamientos y deslizamientos ahora son *dolly* con paralaje, no zoom.
  - Los efectos (`BRILLO_GLASEADO`, `AZUCAR_CAYENDO`, `VAPOR_SUAVE`, `LUZ_CALIDA`) suman un arco de cámara de unos 30°.
  - Los arcos no pasan de 60°: con una sola foto, una vuelta completa deforma el lado oculto del producto.
- **Variedad obligatoria.** Los movimientos que rodean, suben o cruzan el producto llevan `"camara3d": true`. Con `minimoPlanosCamara3d: 2`, al menos dos de los tres planos usan uno de ellos, y los tres no pueden repetir el mismo movimiento. Lo exige el validador.
- **El prompt completo vive en el paquete**, en campos nuevos de `movimientos.json`, y no en los adaptadores (regla 5):
  - `plantillaDeToma`: el envoltorio en inglés. Comercial de postres, toma continua, el producto se mantiene idéntico mientras la cámara se mueve por el espacio 3D, y la marca `{instruccion}`.
  - `prohibiciones`: en frases. Sin manos, personas, cuchillos, texto ni logos; sin deformar ni duplicar el producto; sin zoom digital. Se quitaron el límite de 15° y "cortes de cámara", que ya cubre "una sola toma continua".
  - `negativos`: los mismos conceptos como lista de sustantivos, para el prompt negativo.
- **Un solo armado para los dos adaptadores.**
  - `Movimientos.promptDeToma(movimiento)` arma el prompt y `ServicioGeneracion` lo envía en `SolicitudDeClip(foto, tipoFoto, prompt, negativos, segundos)`.
  - **Gemini Omni Flash** (`GEMINI_OMNI`, el del plan Lite) recibe el prompt más "Avoid: {negativos}.", porque no admite un prompt negativo.
  - **Veo** recibe el mismo prompt y los negativos en `negativePrompt`, así que queda igual de bien alimentado si se activa desde el panel.
- **Lo que no cambia:** el modelo (Gemini Omni Flash) y la duración de los clips (4 s). Se ajustan en la base de datos sin programar (regla 4).

### 5. Composición: fundidos, textos animados y un cierre más atractivo

`FfmpegComponedor`:

- **Fundido de 0,3 s** (`xfade`) entre todos los planos, en lugar del corte directo y del fundido a negro hacia el cierre.
  - Los planos con clip duran exactamente sus segundos, para que el clip no se congele durante el fundido.
  - El cierre, que es un fondo fijo, absorbe las tres transiciones (4,9 s): el total sigue siendo **15 s exactos** (RN-31).
- **Textos de los planos en el tercio inferior**, sobre una franja oscura degradada, sin caja negra.
  - Entran con un fundido y una subida de 40 px, escalonados por línea.
  - Se desvanecen antes del siguiente fundido.
- **Cierre:**
  - **Fondo:** la foto del local, o, si no la hay, el último cuadro del plano 3 desenfocado y oscurecido. Reemplaza al degradado de los colores de marca.
  - **Logo:** en una insignia redonda con sombra.
  - **Texto principal:** el precio o la frase, en grande.
  - **Llamado:** en una píldora del color de marca.
  - **Ubicación y WhatsApp:** más chicos.
  - Todo entra con animación escalonada.
- **Nitidez:** escalado `lanczos`, un `unsharp` suave y CRF 20 (antes 23).

Las posiciones, los tiempos y los tamaños son reglas generales de la composición, no de Pastelería. Por eso quedan como constantes de `FfmpegComponedor`, y la tipografía de cada estilo sigue en el paquete de nicho.

## Alternativas consideradas

- **Mantener el guion de respaldo, pero con frases fijas del paquete, sin copiar al negocio.** Da una propuesta siempre, pero genérica e igual para todos los negocios: no es el producto que se vende. El usuario prefirió esperar a la IA.
- **Sin respaldo y sin reintento automático** (avisar "inténtalo más tarde" de inmediato). Un corte breve del proveedor (un 429 o un 503) dejaría el video en error sin necesidad. Unos minutos de reintentos cubren esos cortes sin cobrar de más, porque una llamada que falla no se cobra.
- **Que Gemini escriba libremente la toma de cámara de cada plano.** Da más variedad, pero rompe la plantilla fija del ADR 0003: el modelo podría pedir algo que el modelo de video no sabe hacer o que deforma el producto. La lista cerrada, con más movimientos 3D, mantiene el control.
- **Órbitas completas de 360°.** Con una sola foto, el modelo de video tiene que inventar el lado oculto, y la torta se deforma. Por eso los arcos llegan como máximo a 60°.
- **Pasar ya a Veo 3.1 Fast.** Cuesta más por segundo y exige la cuenta de Google Cloud. Primero se prueba Omni con el prompt nuevo; Veo queda listo con el mismo prompt.
- **Guardar la frase en la columna `precio`.** Evita la migración, pero el nombre engañaría y no se podría saber si el negocio escribió un precio. Se agregó `plano.frase`.
- **Mover la animación de los textos a `plantilla.json`.** No es propio de Pastelería: cualquier nicho usa la misma composición. Se puede hacer si un nicho necesita otra.

## Consecuencias

- **Positivas:**
  - Los textos en pantalla son publicitarios y ya no copian al negocio. El cierre nunca muestra una orden como precio.
  - Los clips piden movimientos de cámara reales alrededor del producto, con un prompt más rico que vive en el paquete de nicho y que reciben igual Omni y Veo.
  - El video se ve más pulido (fundidos, textos animados y un cierre con marca) y sigue durando 15 s exactos.
  - Un corte breve del proveedor de texto ya no produce un guion pobre: se reintenta solo.
- **Negativas y riesgos:**
  - **Sin la IA de texto no hay propuesta.** Si Gemini no está disponible (por ejemplo, la facturación de AI Studio), el negocio espera unos minutos y luego ve el error. Puede reintentarlo sin gastar su video del día. El administrador recibe la notificación `INTEGRACION_CON_ERROR` (RN-57).
  - Con más libertad para el guion, el validador puede rechazar más respuestas. Cada respuesta se cobra (US$ 0,01 en Gemini), y conviene medir cuántas hacen falta.
  - Un arco de cámara puede deformar el producto en algunas fotos. Falta probar con 2 o 3 videos reales de Omni antes de dar el cambio por cerrado. Si no convence, se puede pasar a Veo 3.1 Fast desde el panel.
  - Recomponer un video viejo usa la composición nueva, con fundidos y el fondo desenfocado.
- **Datos viejos:**
  - Las columnas `video.guion_de_respaldo` y `video.motivo_respaldo` quedan en la base de datos, pero la entidad ya no las mapea, y la vista ya no trae `avisoDeRespaldo`. Los videos que salieron del respaldo se ven como cualquier otro.
  - `plano.frase` es `null` en los cierres anteriores: muestran el precio, si lo tenían, o solo el llamado.
  - Las columnas se pueden quitar con una migración cuando ya no haga falta auditarlas.
- **Pruebas:**
  - `ValidadorDeGuionTest`: variedad de movimientos, cámara 3D, precio frente a instrucción, números del precio, frase y ubicación.
  - `VideoIntegracionTest`: V19 con una instrucción que no se muestra, V20 en `ERROR` con "Reintentar" sin consumo, sin credencial con reintentos automáticos y luego `ERROR`, un 503 que se reintenta, un proceso interrumpido que se reintenta, edición del precio y la frase, y un producto sin fotos que da un error definitivo sin cobrar.
  - `PaquetePasteleriaTest`: los 13 movimientos y el prompt de `ORBITA_FRONTAL`.
  - `FfmpegComponedorTest`: los fundidos mantienen los 15 s y 450 cuadros, y no alargan los planos con clip.
