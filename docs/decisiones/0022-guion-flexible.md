# ADR 0022: Guion flexible: la IA ve las fotos, elige la estructura y la creatividad, y escribe las tomas

- **Estado:** Aceptada
- **Fecha:** 2026-11-25
- **Modifica:** las secciones 7.2 (fotos del producto), 7.3 (producto nuevo en el chat), 7.4 (plantilla de cuatro planos fijos), 7.5 (lista cerrada de movimientos y mínimo de cámara 3D) y 8.1 (respuesta del guion) de la especificación, RN-12, y el ADR 0021 (punto de los movimientos: la lista cerrada y el mínimo de cámara 3D).

## Contexto

Después del ADR 0021 el video ya se movía de verdad, pero seguía siendo siempre igual: tres planos fijos (detalle, entero y porción, de 3, 4 y 4 segundos), un movimiento elegido de una lista y el cierre de 4 segundos. El usuario quiere más presencia en Reels con videos menos de plantilla: que la IA elija la mejor foto (o use una sola), que pueda hacer menos o más tomas según lo necesite el producto, y que sea más creativa (por ejemplo, que la torta con frutas aparezca luego en la tienda o en una mesa), manteniendo el hook y que lo animado tenga sentido con lo que dice el texto.

Además, pedir tres fotos obligatorias (entero, detalle, porción) era una barrera para el negocio, y Gemini nunca veía las fotos: escribía las tomas a ciegas.

## Decisión

Decisiones del usuario del 2026-11-25:

1. **Fotos libres.** El producto tiene la **Foto 1** (principal, obligatoria) y la **Foto 2** y la **Foto 3** opcionales, de cualquier ángulo (`TipoFoto` = `FOTO_1`, `FOTO_2`, `FOTO_3`; la migración `V10` renombra ENTERO, DETALLE y PORCION en ese orden). Un producto con su Foto 1 ya puede usarse en un video (RN-12).
2. **Gemini ve las fotos.** El guion se pide con las fotos del producto adjuntas (JPEG de hasta 1024 px, `PreparadorDeFotos.paraLaIa`) como partes `inlineData` después de las instrucciones, en ambos adaptadores de Gemini. La IA elige qué foto usa cada toma; puede usar una sola o repetirla.
3. **Cuatro estructuras** en `plantilla.json` (la IA elige una; todas suman 15 s y la toma 1 es siempre el gancho):

   | Estructura | Tomas | Cierre | Palabras por toma |
   |---|---|---|---|
   | RAPIDA | 4 de 3 s | 3 s | 5 |
   | CLASICA | 3 de 4 s | 3 s | 6 |
   | HISTORIA | 2 de 6 s | 3 s | 7 |
   | PLANO_UNICO | 1 de 8 s | 7 s | 8 |

   El texto en pantalla es obligatorio solo en el gancho. El cierre es el plano sin foto que sigue a la última toma.
4. **Tres niveles de creatividad** en `movimientos.json` (la IA elige uno): **PRODUCTO** (el producto en su escena, cámara de comercial), **AMBIENTE** (el mismo producto en otro lugar creíble: la vitrina, una mesa de cumpleaños) y **ORIGEN** (los ingredientes que se ven en la foto o que dice la descripción cayendo o flotando alrededor). Cada nivel trae una guía para escribir la toma y sus prohibiciones y negativos extra.
5. **La IA escribe la toma.** Cada plano lleva `toma` (en inglés, para el modelo de video), `descripcionToma` (en español, para el storyboard) y su texto; el video lleva `idea` (el concepto en una o dos frases). Los 13 movimientos del catálogo quedan como inspiración en el prompt y para los videos anteriores. El prompt del clip es la plantilla de toma con la toma, las prohibiciones generales y las del nivel de creatividad. Se mantiene que el producto no se deforma, no se corta ni cambia su decoración, y que no hay personas, manos ni texto.
6. **Duración de cada clip.** Cada plano guarda sus `segundos`. Al modelo de video se le pide la duración más corta que admite y que cubre el plano (parámetro `duracionesClip`; Veo: 4, 6 u 8 s); FFmpeg recorta el sobrante. El costo del clip usa esa duración.
7. **Video con Veo en Google Cloud.** Con la clave de AI Studio solo existe Gemini Omni Flash (Veo salió de la API de Gemini el 2026-10-22). El usuario decidió usar Veo y dejar Omni de lado: la migración `V11` agrega Veo 3.1 Fast y Veo 3.1 (estándar) copiando la configuración de Veo 3.1 Lite (720p, 9:16, sin audio) y pone Veo 3.1 Lite en el plan Lite. Se compararán Lite, Fast y estándar a 720p.

El validador (`ValidadorDeGuion`) revisa que la estructura y la creatividad existan, que haya tantas tomas como pide la estructura, que cada foto exista, que haya toma y descripción, y el texto del gancho; siguen RN-21 a RN-24 y las reglas del cierre del ADR 0021. Se quitaron la lista cerrada de movimientos del esquema, el mínimo de planos con cámara 3D y la regla de no repetir el movimiento.

## Consecuencias

- Videos más variados y con una idea propia; el negocio puede registrar un producto con una sola foto.
- Más riesgo de que Veo invente o deforme con AMBIENTE y ORIGEN: se controla con las prohibiciones del nivel y se afina en los prompts del paquete de nicho, sin programar.
- La estructura RAPIDA pide 4 clips de 4 s (16 s pagados para 12 s de video): con Veo 3.1 Lite, unos US$0,80 frente a US$0,60 de las demás.
- El guion cuesta algo más por las imágenes (cuatro imágenes de 1024 px por intento).
- Los videos anteriores siguen funcionando: tienen estructura CLASICA, segundos 3/4/4/4 y su toma sale del movimiento del catálogo.
