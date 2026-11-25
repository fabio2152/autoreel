# ADR 0024: Modos de tomas elegidos en el chat: transiciones entre fotos y plano extendido

- **Estado:** Aceptada (experimental: se probará y luego se decidirá la mejor experiencia)
- **Fecha:** 2026-11-25
- **Modifica:** el ADR 0022 (la IA ya no elige entre todas las estructuras: elige entre las del modo que pidió el negocio) y la sección 7.3 de la especificación (el chat suma un paso).

## Contexto

Con el guion flexible (ADR 0022) cada toma es un clip de Veo independiente que empieza en su foto, y AutoReel las une con un fundido de 0,3 s. Al ver el primer video con Veo 3.1 Lite, el usuario notó que el paso de una toma a otra se siente algo abrupto y preguntó por qué Veo no crea el video "de corrido".

Veo genera como máximo 8 segundos por pedido, cada toma parte de una foto distinta y el cierre con el logo lo compone AutoReel. Veo 3.1 en Google Cloud ofrece dos funciones que permiten algo más continuo:

- **Foto inicial y foto final** (`lastFrame` junto a `image` en `instances[]`): el clip termina exactamente en otra foto.
- **Extender un clip** (`video` en `instances[]`): Veo continúa un clip que generó, unos 7 segundos más, y devuelve el video completo.

La documentación confirma ambas para Veo 3.1 y Veo 3.1 Fast; para Veo 3.1 Lite es contradictoria.

## Decisión

Por pedido del usuario, se prueban como **opciones** y no como cambio del comportamiento por defecto:

1. **Paso nuevo del chat, el último: "¿Cómo quieres las tomas del video?"** (`tomas`, en `chat.json`), con cinco opciones. Se guarda en `video.modo_tomas` (migración `V12`).
2. **Modos de tomas** en `plantilla.json` (`modosDeToma`, `modoDeTomasPorDefecto`), cada uno con las estructuras que puede elegir la IA y una indicación para el guion:

   | Modo | Estructuras | Qué cambia |
   |---|---|---|
   | Tomas cortas (por defecto) | Rápida o Clásica | Lo de antes: clips independientes con fundido |
   | Tomas largas con transiciones | Clásica o Historia | Cada toma termina en la foto de la siguiente (`lastFrame`): un recorrido continuo |
   | Historia | Historia | 2 tomas de 6 s |
   | Plano único | Plano único | 1 toma de 8 s y cierre de 7 s |
   | Plano extendido | Extendida (nueva) | 1 toma de 12 s: un clip de 8 s que Veo extiende |

   El esquema JSON del guion solo ofrece las estructuras del modo, y `guion.md` recibe la indicación del modo (`{modoDeTomas}`). Los videos anteriores usan el modo por defecto.
3. **Transiciones:** `SolicitudDeClip.fotoFinal` lleva la foto (9:16) de la toma siguiente; el adaptador de Veo la envía como `lastFrame`. La última toma termina libre.
4. **Plano extendido:** la estructura `EXTENDIDA` tiene `extender: true`. Cuando llega el clip base, se guarda y se pide su extensión (`GeneradorDeClips.extender`, adaptador de Veo); el plano queda generando con `plano.extension_en_curso`. Si la extensión llega, reemplaza al clip base; si falla o se agota el tiempo, el plano se queda con el clip base y la composición congela su último cuadro. La extensión se cobra como 7 segundos del modelo.

## Consecuencias

- El negocio puede probar videos más continuos sin cambiar el comportamiento por defecto.
- Se paga lo mismo con transiciones; el plano extendido cuesta 8 + 7 = 15 segundos de Veo (unos US$ 1,50 con Veo 3.1 Fast).
- Con Veo 3.1 Lite, las transiciones y la extensión pueden no estar disponibles: si Veo rechaza la extensión, el plano usa el clip base; si rechaza `lastFrame`, el clip falla como cualquier otro.
- La experiencia (dónde y cómo se elige) se revisará después de las pruebas.
