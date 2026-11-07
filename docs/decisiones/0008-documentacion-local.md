# ADR 0008 — Documentación local, fuera del control de versiones

- **Estado:** Aceptada
- **Fecha:** 2026-11-03

## Contexto

La sección 23 de la especificación pide mantener en el repositorio una carpeta `docs/`. Debe contener la especificación, la arquitectura, el modelo de datos, los ADR, la guía de la API, las guías de nicho y de proveedores de IA, el despliegue en AWS y los manuales.

AutoReel pasó a ser un **proyecto individual**: el dueño es Fabio Malpartida, y Piero Pittman ya no participa. El dueño decidió que esa documentación de trabajo, junto con la configuración local de Claude Code (`.claude/`), **no se suba al repositorio**, aunque se siga manteniendo al día en cada fase (regla 11 de la sección 0).

## Decisión

1. **`docs/` y `.claude/` se excluyen de git.** Están en `.gitignore`, ancladas a la raíz: `/docs/` y `/.claude/`.
2. **La documentación de `docs/` se mantiene igual que si estuviera versionada:** se actualiza al terminar cada fase, como lo exigen `CLAUDE.md` y la sección 23. Esto incluye:
   - Este directorio `decisiones/`.
   - `especificacion.md` y `contexto-vision.md`.
   - `plan-de-desarrollo.md`, `arquitectura.md`, `modelo-de-datos.md` y `api.md`.
   - Las guías y los manuales.
3. **Lo que sí se versiona** son los documentos que necesita cualquiera que clone el repositorio:
   - **`CLAUDE.md`:** las reglas de trabajo.
   - **`README.md`:** qué es, tecnologías, ejecución local y una nota que explica que la documentación está en la carpeta `docs/` local, no versionada.
   - **`CHANGELOG.md`:** los cambios de cada fase.
   - **`.env.example`:** las variables de entorno con valores de ejemplo.
   - Los comentarios del código, que remiten a los ADR por su número. Por ejemplo, `ConfiguracionSeguridad` y `src/api/cliente.ts` citan el ADR 0007.
4. **Respaldo manual periódico.** Como `docs/` no tiene respaldo en git, el dueño copia la carpeta completa a un almacenamiento externo (por ejemplo, su nube personal):
   - como mínimo al cerrar cada fase, antes de abrir su pull request;
   - y cada vez que se agregue o modifique un ADR.

## Alternativas consideradas

- **Versionar `docs/` en el mismo repositorio**, como dice la sección 23. Tendría historial y respaldo automáticos, pero en un proyecto individual nadie más necesita la documentación desde el repositorio, y el dueño decidió mantenerla fuera de él.
- **Un repositorio privado aparte solo para la documentación.** Daría historial y respaldo, pero agrega un segundo repositorio que sincronizar en cada fase. Se puede adoptar más adelante si el respaldo manual resulta insuficiente.
- **Una wiki o un gestor de documentos en la nube.** Perdería la cercanía con el código y los diagramas Mermaid que se escriben junto a él.

## Consecuencias

- **Positivas:**
  - El repositorio contiene solo el código y lo necesario para ejecutarlo.
  - La documentación de trabajo queda bajo el control exclusivo del dueño.
  - Claude Code sigue leyendo `docs/` en local, como indica `CLAUDE.md`.
- **Negativas y riesgos:**
  - **Riesgo principal: sin respaldo en git.** Si se pierde el equipo o se borra la carpeta, se pierden la especificación, los ADR y los manuales. **Medida:** el respaldo manual periódico del punto 4, más los documentos versionados (`CLAUDE.md`, `README.md`, `CHANGELOG.md`), que permiten reconstruir lo esencial.
  - **Sin historial de cambios** de la documentación. Para saber qué cambió y cuándo, hay que apoyarse en `CHANGELOG.md` y en los commits del código.
  - **Quien clone el repositorio no tendrá los ADR ni los manuales.** El `README.md` lo advierte. Si en el futuro se suma otra persona al proyecto, habrá que compartirle la carpeta o revisar esta decisión.
  - **Los enlaces desde el código o el README a archivos de `docs/` no funcionan en GitHub.** Por eso el README no enlaza a `docs/`, y el código cita los ADR solo por su número.
- **Cuándo revisar esta decisión:** si se incorpora otra persona al equipo, si el proyecto pasa a ser evaluado desde el repositorio o si ocurre una pérdida de datos.
