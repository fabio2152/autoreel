# ADR 0026: Edición con correcciones, una foto por producto y mejoras del admin

- **Estado:** Aceptada
- **Fecha:** 2026-11-25
- **Modifica:** el ADR 0022 (ya no hay tres fotos por producto: la IA trabaja con una sola) y la sección 8.4 de la especificación (ya no se cambia la foto de un plano). Deja fuera del MVP la publicación en Instagram (fase 6).

## Contexto

Al revisar los videos y el panel, el usuario (Fabio) pidió estos cambios:

- **Storyboard.** El texto de la publicación (descripción y hashtags) quedaba al final de la página, debajo de todos los momentos. Con el video listo, se pierde de vista.
- **Editar un plano.** Cambiar la foto de un plano casi nunca arreglaba lo que no le gustaba del clip: lo que fallaba era lo que Veo dibujaba (por ejemplo, hojas de menta que la torta no tenía). Pidió poder escribir lo que no quiere que aparezca y regenerar el plano con eso.
- **Fotos.** Con dos o tres fotos, la IA mezclaba ángulos y el video perdía continuidad. La IA trabaja mejor con una sola foto, la mejor del producto.
- **Costos del admin.** La tabla da el total del mes, pero no muestra cómo crece el gasto de cada negocio día a día.
- **Beneficios.** Un beneficio por fechas no dice cuántos videos regala: un negocio que no crea videos esos días no lo aprovecha. Prefiere regalar una cantidad de videos (5 o 6 usos).
- **Pagos.** Los clientes pagan por Yape o transferencia y mandan una captura. No había dónde guardarla.
- **Contraseña olvidada.** El negocio no tenía cómo avisar desde AutoReel que olvidó su contraseña.
- **Instagram.** Ajustes decía "Podrás conectar tu cuenta profesional de Instagram muy pronto". La fase 6 está en pausa (aprobación de Meta) y el negocio descarga y sube su video a mano.

## Decisión

Decisiones tomadas por el usuario el 2026-11-25:

1. **Gemini reescribe la toma con las correcciones.** Cada toma tiene un campo **correcciones** (hasta 300 caracteres, "¿Qué no quieres que aparezca?"). Antes de regenerar el plano, el modelo de texto del video reescribe la toma una sola vez con la toma actual, las correcciones, la foto y las reglas del paquete de nicho (`nichos/pasteleria/correccion-toma.md`, `CorrectorDeTomas`). Cuesta un guion más (unos US$ 0,01) y se registra como costo `GUION`. La toma nueva **no nombra** lo que se quiere quitar: en la primera prueba real, la toma decía "no flowers or floral decor anywhere" y Veo igual puso un florero, porque dibuja lo que lee. Por eso Gemini devuelve aparte los `negativos` (en inglés, separados por comas), que se guardan en el plano (`V19`, `plano.negativos_correccion`) y se suman al prompt negativo de Veo en el clip y en su extensión. Si se borran las correcciones, sus negativos se borran también.
   - Si la reescritura falla, responde 503 `CORRECCION_NO_DISPONIBLE` y **no se gasta la regeneración**.
   - Las correcciones guardadas en el storyboard se aplican en la primera generación, antes de pedir el clip a Veo. Si ahí falla la reescritura, el clip sale con la toma de siempre y las correcciones quedan pendientes para una regeneración.
   - Guardar correcciones no recompone el video: solo regenerar el plano las aplica. "Regenerar plano" aparece solo dentro de la edición.
   - Se quita el cambio de foto de un plano (`foto.tipo` y `foto.clave` en `PUT .../planos/{orden}`).
   - Migración `V14`: `plano.correcciones` y `plano.correcciones_aplicadas`.
2. **Una sola foto por producto** (`FOTO_1`, "Foto del producto"). Todas las tomas la usan. Una `FOTO_2` o `FOTO_3` en la API responde 400 `SOLICITUD_INVALIDA`. Migración `V15`: la foto de menor número pasa a `FOTO_1` si faltaba, se borran las demás filas (sus objetos en el almacenamiento se conservan) y los planos anteriores conservan su `clave_foto`.
3. **Beneficios por cantidad de usos** en lugar de un rango de fechas. Un **uso** es un video creado con el beneficio y aprobado con "Crear mi video". El administrador otorga de 1 a 30 usos.
   - El beneficio está vigente mientras no esté terminado y le queden usos.
   - Si el video falla y se devuelve el video del día (RN-27), también vuelve el uso.
   - Un video creado con el beneficio no se aprueba si el beneficio ya no tiene usos (409 `BENEFICIO_AGOTADO`) o si el administrador lo terminó (409 `BENEFICIO_TERMINADO`): el negocio lo descarta y crea otro con su plan.
   - Migración `V16`: `usos_otorgados`, `usos_consumidos` y `terminado` en `beneficio_temporal`, `desde` y `hasta` opcionales, y `video.beneficio_id`. Los beneficios por fechas anteriores quedan terminados en el historial.
4. **Comprobante de pago de hasta 5 MB.** Al registrar un pago, el administrador puede adjuntar una imagen (JPG, PNG o WebP; el navegador convierte cualquier otra imagen a JPG). Se guarda en `negocios/{id}/comprobantes/` con la subida en dos tiempos del ADR 0010 (propósito `COMPROBANTE_PAGO`, endpoint `POST /api/admin/negocios/{id}/pagos/url-subida-comprobante`). Migración `V17`: `pago.comprobante_clave`.
5. **La solicitud de contraseña avisa al administrador.** En Ajustes, el botón "¿Olvidaste tu contraseña?" abre una ventana con el correo, un WhatsApp opcional y un mensaje. Crea la notificación `SOLICITUD_CONTRASENA` en la campana (una por negocio y por día) y el administrador usa el "Reiniciar contraseña" que ya existe (RF-04). No hay correos automáticos. Migración `V18`.
6. **Instagram queda fuera del MVP.** Se quita la sección Instagram de Ajustes y los textos "muy pronto". RF-27, RF-28 y `/api/instagram/**` pasan a ser futuros: el negocio descarga su video y lo sube a mano.

Además, sin decisión de arquitectura:

- El texto de la publicación va justo debajo del reproductor cuando el video está listo o aprobado.
- La página Costos suma un gráfico de líneas con el costo acumulado de cada negocio por día del mes (`GET /api/admin/costos/diario`, SVG propio, sin dependencias nuevas), entre las cifras y la tabla.

## Alternativas consideradas

- **Pasar las correcciones al prompt de Veo sin reescribir la toma.** Más barato, pero Veo entiende mal las negaciones ("sin menta" puede traer menta) y la toma queda con instrucciones que se contradicen. Descartada.
- **Mantener el cambio de foto junto con las correcciones.** Con una sola foto por producto ya no tiene sentido.
- **Beneficio por fechas y por usos a la vez.** Más reglas para un caso que no se usa. Descartada.
- **Recuperar la contraseña por correo.** Necesita un proveedor de correo y queda fuera del MVP (sección 21 de la especificación).
- **Mantener Instagram como "muy pronto".** Promete algo sin fecha. Descartada.

## Consecuencias

- El negocio corrige lo que no le gusta con sus palabras, y una corrección que falla no le cuesta una regeneración. Cada reescritura cuesta unos US$ 0,01 más.
- El catálogo es más simple: una foto por producto. Las tomas del video parten siempre de la misma foto.
- El administrador regala videos concretos. Un beneficio puede durar semanas si el negocio no crea videos; no vence solo.
- Los pagos quedan con su respaldo. El comprobante es opcional.
- El negocio pide ayuda desde AutoReel. Si pide dos veces el mismo día, el segundo mensaje no llega (ya hay un aviso ese día) y la notificación no se marca leída sola al reiniciar la contraseña.
- La fase 6 sigue en el plan de desarrollo, pero fuera del MVP.
