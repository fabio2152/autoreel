# ADR 0025: Un solo modo de tomas: historia con ambiente y producto en movimiento

- **Estado:** Aceptada
- **Fecha:** 2026-11-25
- **Reemplaza:** el paso del chat y los cinco modos del ADR 0024. Modifica el ADR 0022: la IA ya no elige la estructura ni la creatividad.

## Contexto

Con el ADR 0024 se generaron 10 videos con Veo 3.1 (2 productos × 5 modos). El usuario los revisó:

- **El mejor fue "Historia" con creatividad "Ambiente"** (torta de yogurt con frutos rojos). La cámara sube y el pastel aparece dentro de una vitrina de pastelería con mármol y luces; la porción aparece en una mesa elegante junto a la ventana. El fondo cambia y se ve rico.
- **De "Tomas largas con transiciones" le gustó el final**: la porción se levanta y se acerca a la cámara, y la fruta se ve de cerca. **El inicio no le gustó**: la transición a la foto siguiente (`lastFrame`) produjo un corte brusco.
- También le gustaron el plano único y el plano extendido, pero pidió dejar una sola forma, sin planos simples (solo acercar, alejar o girar alrededor del postre), con el producto en movimiento y el modelo Veo 3.1 Fast.

## Decisión

1. **Un solo modo de tomas, `HISTORIA_EN_MOVIMIENTO`** ("Historia con ambiente y movimiento"), el único de `modosDeToma` en `plantilla.json`: estructura **Historia** (2 tomas de 6 s y cierre de 3 s), creatividad **Ambiente** y sin transiciones con foto final. `ModoDeTomas` suma `creatividades` (las que puede elegir la IA) y el esquema del guion solo ofrece esas.
2. **Se quita el paso "tomas" del chat.** Todos los videos usan el modo por defecto.
3. **Prompts nuevos** en el paquete de nicho:
   - La creatividad Ambiente pide dos cosas en cada toma: un lugar bonito y creíble (la vitrina con luces cálidas, vidrio y mármol; una mesa junto a la ventana) y el producto en movimiento (una porción que se levanta y se acerca, frutos que se acomodan, un glaseado que brilla). Prohíbe los fondos vacíos y el producto estático.
   - `guion.md` deja de ofrecer la lista de movimientos de cámara como inspiración, prohíbe las tomas simples e incluye como ejemplo las tomas del video que más le gustó.
4. **El plan Lite usa Veo 3.1 Fast** (cambio de datos desde el panel, no de código).

Las estructuras Rápida, Clásica, Plano único y Extendida, las transiciones con foto final y la extensión de clips siguen en el código y en la plantilla por si se retoman, pero ningún modo las usa.

## Consecuencias

- Videos más parecidos entre sí y al que más le gustó al usuario; el negocio responde una pregunta menos.
- Cada video pide 2 clips de 6 s: unos US$ 1,21 con Veo 3.1 Fast.
- Con un producto de una sola foto, las dos tomas parten de la misma foto.
- El movimiento del producto depende de Veo: puede deformarlo. Se controla con las prohibiciones y se afina en los prompts, sin programar.
