# ADR 0013 — Sin fechas especiales ni temporadas en el flujo del video del MVP

- **Estado:** Aceptada
- **Fecha:** 2026-11-04

## Contexto

La especificación usa las temporadas y fechas especiales de Perú (sección 7.7) en tres lugares del chat (sección 7.3):

- El botón **"Fecha especial"** en "¿Qué video quieres crear hoy?", que muestra las fechas próximas.
- El **carrusel de productos ordenado por temporada**: primero los de la temporada actual, con un saludo como "Es verano: estos suelen funcionar mejor".
- El caso **V13**: "En verano, el carrusel muestra primero los productos etiquetados como 'verano'".

Además, **RF-31** (prioridad S) pide un calendario de fechas especiales con recordatorios ("Faltan 10 días para el Día de la Madre").

La fase 2 dejó lista la base: `temporadas.json` con las 9 temporadas de la sección 7.2, el record `Temporada` con sus reglas de fecha, los cálculos `PaqueteNicho.temporadasVigentes(hoy)` y `fechasEspecialesProximas(hoy)` (15 días, `DIAS_FECHA_PROXIMA`) y las etiquetas de temporada de cada producto (`producto.temporadas`). El primer diseño de la fase 3 (`docs/plan-fase-3.md`, secciones 1 y 5) agregaba al chat un tipo de paso `FECHA_ESPECIAL`, un paso `fechaEspecial` que solo aparecía si se elegía ese tipo de video, el orden del carrusel por temporada y el saludo de temporada.

Al revisar ese diseño, el usuario (dueño del producto) decidió que **las fechas especiales y las temporadas no son prioridad para el MVP**: el valor del MVP está en convertir las fotos en un Reel bueno, y las fechas agregan pasos condicionales, un tipo de paso con opciones calculadas, un mensaje más y una variante del guion de la fase 4.

## Decisión

1. **El chat no usa fechas especiales ni temporadas.**
   - `tipoVideo` tiene cuatro opciones en `chat.json`: `PRODUCTO_ESTRELLA` (Producto estrella), `PROMOCION` (Promoción), `TORTAS_POR_ENCARGO` (Tortas por encargo) y `RECIEN_HORNEADO` (Recién salido del horno). No existe la opción "Fecha especial".
   - No existe el paso `fechaEspecial`. `GuionChat.TipoPaso` solo tiene `BOTONES`, `PRODUCTO` y `TEXTO`.
   - `GuionChat.Mensajes` no tiene saludo de temporada.
2. **El carrusel no se ordena por temporada.** `ServicioCatalogo.carrusel(negocioId)` usa `ProductoRepositorio.listarParaCarrusel` (activos, en el catálogo, ordenados por `fechaCreacion desc`, del más reciente al más antiguo) y se queda con los que tienen sus tres fotos. Las tarjetas del carrusel (`SelectorDeProducto`) no muestran la temporada.
3. **V13 queda fuera del MVP** y se registra como trabajo futuro, junto con el calendario de fechas especiales (RF-31). Los casos que cierra la fase 3 son V10, V11, V14, V15, V16 y V17.
4. **Se conserva todo lo de la fase 2**, sin cambios:
   - `nichos/pasteleria/temporadas.json`, el record `Temporada` (clases `TEMPORADA`, `FECHA_ESPECIAL`, `SIEMPRE`; reglas `RANGO`, `FECHAS`, `N_ESIMO_DOMINGO`, `MES`, `SIEMPRE`) y su validación al arrancar.
   - `PaqueteNicho.temporadas()`, `admiteTemporada`, `temporadasVigentes(hoy)` y `fechasEspecialesProximas(hoy)`, con sus pruebas en `PaquetePasteleriaTest`.
   - La columna `producto.temporadas` (`jsonb`), el selector **Temporada** del formulario del producto (al menos una; `PRODUCTO_SIN_TEMPORADA` y `TEMPORADA_INVALIDA`) y las temporadas en las tarjetas del catálogo.
   - `GET /api/nicho/configuracion` sigue devolviendo `temporadas` y `temporadasVigentes`.
5. **Un producto subido desde el chat** (`ServicioCatalogo.crearDesdeChat`) se crea con la temporada **"Todo el año"** (`TEMPORADA_POR_DEFECTO = "TODO_EL_ANIO"`). Si se guardó en el catálogo, la persona puede cambiarla después al editarlo.

## Cómo se reactivaría

Cuando se retome (después del MVP, junto con RF-31), los cambios serían:

1. **Carrusel por temporada (V13).** En `ServicioCatalogo.carrusel`, ordenar los productos completos poniendo primero los que tienen alguna temporada de `paquete.temporadasVigentes(LocalDate.now(reloj))` (día de Lima) y, dentro de cada grupo, los más recientes. Devolver las temporadas en `ConversacionVista.ProductoCarrusel` y mostrarlas en `SelectorDeProducto`.
2. **Saludo de temporada.** Agregar un mensaje fijo a `chat.json` (por ejemplo, `"temporada": "Es {nombre}: estos suelen funcionar mejor"`), a `GuionChat.Mensajes` y a la lista de mensajes que exige `GuionChat.validar()`; `ServicioChat.preguntar` lo diría al llegar al paso `PRODUCTO` si hay productos de la temporada vigente.
3. **Botón "Fecha especial".** Agregar la opción `FECHA_ESPECIAL` a `tipoVideo` y un paso `fechaEspecial` con un tipo nuevo (por ejemplo `TipoPaso.FECHA_ESPECIAL`), cuyas opciones se calculan con `temporadasVigentes` y `fechasEspecialesProximas` (solo la clase `FECHA_ESPECIAL`). El motor hoy **no tiene pasos condicionales** (`obligatoriaSi` cambia si una pregunta es obligatoria, no si aparece): habría que agregar a `PasoChat` una condición del tipo `soloSi: {paso, valor}`, validarla como `obligatoriaSi` y hacer que `GuionChat.siguiente` salte los pasos que no aplican. El frontend necesitaría dibujar el nuevo tipo de paso.
4. **Contrato con la fase 4.** `conversacion.respuestas` tendría la clave `fechaEspecial` (id de la temporada); el guion de la fase 4 debería usarla en el gancho y en los hashtags.
5. **Pruebas.** V13 como prueba de integración con `RelojAjustable` en verano, y la validación del paso condicional en `PaquetePasteleriaTest`.
6. **Calendario (RF-31):** pantalla y recordatorios propios, que reutilizan `fechasEspecialesProximas`.

Nada de esto exige migraciones: las temporadas de los productos ya se guardan.

## Alternativas consideradas

- **Implementar la sección 7.3 completa** (botón, paso condicional, carrusel por temporada y saludo). Es lo que pide la especificación, pero el usuario lo descartó para el MVP por prioridad y complejidad.
- **Quitar también las temporadas de la fase 2** (columna, selector del catálogo y `temporadas.json`). Simplificaría el catálogo, pero tiraría trabajo ya probado y exigiría una migración para borrar `producto.temporadas` y otra para volver a crearla. Conservarlo cuesta poco.
- **Dejar el botón "Fecha especial" sin la lista de fechas**, como un tipo de video más. Sería una función a medias: el guion no sabría de qué fecha se trata.

## Consecuencias

- **Positivas:**
  - El chat y su motor son más simples: tres tipos de paso, sin pasos condicionales ni opciones calculadas, y el mismo flujo para todos los videos.
  - El contrato de `respuestas` con la fase 4 es más chico (`tipoVideo`, `producto`, `especial`, `precio`, `extra`).
  - Las etiquetas de temporada siguen guardándose, así que reactivar el orden del carrusel no exige que los negocios vuelvan a etiquetar sus productos.
- **Negativas y riesgos:**
  - **Desvío de la especificación:** la sección 7.3 y V13 no se cumplen en el MVP. La especificación y el plan de desarrollo deben decir que V13 queda fuera.
  - En el catálogo, la temporada se pide pero hoy no cambia nada en el video. **El formulario del producto (`PaginaProducto.tsx`) todavía muestra la ayuda "En su temporada, el producto aparece primero al crear tus videos."**, que ya no es cierta: hay que cambiarla en el código.
  - `ServicioCatalogo.TEMPORADA_POR_DEFECTO = "TODO_EL_ANIO"` está en el código del catálogo, no en el paquete: un nicho nuevo debe tener una temporada con ese id o `crearDesdeChat` responderá `TEMPORADA_INVALIDA` (ver `docs/guias/nuevo-nicho.md`).
- **Cuándo revisar esta decisión:** al planificar el trabajo después del MVP, o si los negocios piden videos para fechas especiales.
