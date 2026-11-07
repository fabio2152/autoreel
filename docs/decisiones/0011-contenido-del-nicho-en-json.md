# ADR 0011 — Contenido del paquete de nicho en archivos JSON

- **Estado:** Aceptada
- **Fecha:** 2026-11-04

## Contexto

La regla 5 de la sección 0 dice que **todo lo propio del nicho** (preguntas, plantilla, movimientos, vocabulario, temporadas) vive en su paquete (sección 7) y nunca se dispersa por el código. En la fase 2 el paquete Pastelería necesita, por primera vez, contenido real:

- los **estilos** de video (`CASERO`, `ELEGANTE`, `DIVERTIDO`);
- las **preguntas del onboarding** de los pasos 2 y 3 (sección 7.1), con validaciones (obligatoria, longitud máxima, opciones, obligatoria según otra respuesta, celular peruano);
- las **9 temporadas y fechas especiales** de la sección 7.2, con reglas de fecha precisas para calcular cuáles están vigentes y cuáles son próximas (sección 7.7).

Hacía falta decidir dónde vive ese contenido (código Java, base de datos o archivos) y cómo lo muestra el frontend sin pantallas propias de cada nicho.

## Decisión

1. **El contenido vive en archivos JSON del paquete**, en `backend/src/main/resources/nichos/pasteleria/`:
   - `onboarding.json`: `{ "estilos": [Opcion], "preguntas": [Pregunta] }`.
   - `temporadas.json`: `{ "temporadas": [Temporada] }`.
2. **El comportamiento vive en el dominio común** (`com.autoreel.nicho.dominio`), no en el JSON ni en cada paquete:
   - `PaqueteNicho` (interfaz): `id()`, `nombre()`, `estilos()`, `preguntasOnboarding()`, `temporadas()`, y los métodos por defecto `admiteEstilo`, `admiteTemporada`, `temporadasVigentes(hoy)` y `fechasEspecialesProximas(hoy)` (15 días, `DIAS_FECHA_PROXIMA`).
   - `Pregunta` (tipos `TEXTO`, `SELECCION_MULTIPLE`, `TELEFONO`, con `obligatoriaSi`), `Opcion`, `Temporada` (clases `TEMPORADA`, `FECHA_ESPECIAL`, `SIEMPRE`; reglas `RANGO`, `FECHAS`, `N_ESIMO_DOMINGO`, `MES`, `SIEMPRE`) y `ValidadorDeRespuestas` (`normalizar` y `exigirCompletas`).
   - Solo se modela lo que la fase 2 usa (regla 8). El guion del chat, la plantilla, los movimientos, el vocabulario, la música y las instrucciones se agregan en sus fases.
3. **Cada paquete es un `@Component` que lee y valida sus archivos al arrancar.** `PaquetePasteleria` (`com.autoreel.nicho.pasteleria`) lee los dos archivos con el `ObjectMapper` de Jackson y comprueba: preguntas sin id repetido, `paso` 2 o 3, opciones en toda `SELECCION_MULTIPLE`, `obligatoriaSi` que apunta a una pregunta existente, temporadas sin id repetido y al menos un estilo. Si algo falla, lanza `IllegalStateException` y **el backend no arranca**: un error del archivo nunca llega a producción. `PaquetePasteleriaTest` prueba los archivos reales.
4. **Registro automático.** `RegistroDeNichos` recibe por constructor la lista de todos los beans `PaqueteNicho` y expone `todos()`, `buscar(id)` y `de(id)`. Agregar un paquete no requiere tocar el registro.
5. **El frontend es genérico.** `GET /api/nicho/configuracion` (`NichoController`, rol NEGOCIO) devuelve el paquete del negocio de la sesión: `{nicho, nombre, estilos, preguntas, temporadas: [{id, nombre}], temporadasVigentes}`. `CampoPregunta` (`frontend/src/componentes/CampoPregunta.tsx`) dibuja cada pregunta **según su tipo**: chips para `SELECCION_MULTIPLE`, campo con prefijo `+51` para `TELEFONO` y campo de texto con contador para `TEXTO`. `AsistenteOnboarding` y `PaginaAjustes` filtran las preguntas por `paso`, y `esObligatoria` (`frontend/src/api/negocio.ts`) replica `obligatoriaSi` para marcar "(opcional)".
6. **Las respuestas se guardan como JSON** en `perfil_negocio.respuestas` (`jsonb`), por id de pregunta, y las temporadas del producto como lista de ids en `producto.temporadas` (`jsonb`). La base de datos no tiene tablas de preguntas ni de temporadas.

## Alternativas consideradas

- **Contenido escrito en Java** (listas o enumeraciones en `PaquetePasteleria`). Se valida al compilar, pero mezcla textos de negocio con código y cada ajuste de una pregunta exige programar.
- **Contenido en la base de datos** (tablas de preguntas y temporadas). Permitiría editarlo desde un panel, pero ese panel no existe ni está pedido, exige migraciones y semillas, y la regla 4 (leer de la base) aplica a planes y modelos de IA, no a los nichos.
- **YAML en lugar de JSON.** Más legible para textos largos, pero el JSON es el mismo formato que viaja al frontend y Jackson ya está en el proyecto.
- **Pantallas del onboarding escritas para Pastelería** en el frontend. Más libertad de diseño, pero un nicho nuevo obligaría a programar pantallas nuevas.

## Consecuencias

- **Positivas:**
  - Cambiar un texto, una opción o una fecha es editar un archivo, sin tocar el código de la aplicación.
  - Un nicho nuevo es una clase pequeña más sus JSON; el registro, la API y el frontend lo toman solos (ver `docs/guias/nuevo-nicho.md`).
  - Un archivo mal formado se detecta al arrancar, en el CI y en local, no con el usuario.
- **Negativas y riesgos:**
  - Los JSON viajan dentro del jar: cambiarlos exige volver a desplegar el backend.
  - **Los ids son contrato:** los ids de preguntas, opciones y temporadas se guardan en `respuestas` y en `producto.temporadas`. Renombrar o quitar uno deja datos guardados que ya no se reconocen: una respuesta con un id que ya no existe se rechaza con `RESPUESTA_INVALIDA` si se vuelve a enviar, y un producto con una temporada quitada responde `TEMPORADA_INVALIDA` al editarlo. Cambiarlos requiere una migración de datos.
  - La validación al arrancar vive en `PaquetePasteleria`; un paquete nuevo debe repetirla o moverla a un lugar común cuando haya dos.
  - El frontend solo sabe dibujar los tres tipos de pregunta actuales; un tipo nuevo requiere programar su campo.
- **Cuándo revisar esta decisión:** si se necesita editar el contenido sin desplegar (por ejemplo, desde el panel de administración) o cuando se agregue el segundo nicho.
