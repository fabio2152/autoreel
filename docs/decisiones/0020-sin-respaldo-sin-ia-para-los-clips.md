# ADR 0020: Sin respaldo sin IA para los clips

- **Estado:** Aceptada; modificada por el ADR 0021 (el guion de respaldo tampoco se mantiene)
- **Fecha:** 2026-11-06
- **Modifica:** la sección 8.5 de la especificación (plan de respaldo), RN-30, V24 y el uso del acercamiento descrito en el ADR 0016.

## Contexto

Hasta ahora, si un clip de IA fallaba dos veces o tardaba más de 10 minutos, ese plano se reemplazaba por la foto con un acercamiento lento hecho por FFmpeg, y el video se entregaba igual. Sin credencial, el video entero se armaba así.

Al revisar el flujo del negocio, el usuario decidió quitar ese respaldo: "eso no importa ni sirve para el MVP". Un video con planos quietos no es el producto que se vende.

## Decisión

- **Regeneración que falla.** En la revisión, el plano **conserva su clip anterior**.
  - La regeneración no se descuenta y el video avisa: "No pudimos crear el clip nuevo. Dejamos el anterior y no se descontó la regeneración."
  - Al cambiar una foto en la revisión también se conserva el clip anterior hasta que llegue el nuevo.
  - Implementación: `Plano.conservarClipAnterior` y `Video.regeneracionFallida`.
- **Primera generación sin todos sus clips.** El video pasa a **ERROR**.
  - Mensaje: "No pudimos crear los clips con IA. Te devolvimos el video del día; inténtalo de nuevo en un rato".
  - Se devuelve el video del día (RN-27).
  - Los clips ya pedidos se registran como costo (RN-32).
  - No se piden más clips de ese video.
  - Implementación: `ServicioGeneracion.sinClip`.
- **Sin el modelo de video disponible** (inactivo, sin adaptador o sin credencial):
  - "Crear mi video" se desactiva y la API responde 409 `IA_NO_DISPONIBLE`.
  - En la revisión no se puede regenerar ni cambiar una foto.
  - Los textos se siguen editando, porque solo recomponen.
- **El guion de respaldo se mantiene** (sección 8.1): es solo texto y el usuario lo revisa en el storyboard.

## Consecuencias

- Un video entregado siempre tiene sus tres clips de IA.
- Si el proveedor falla, el negocio no pierde su video del día, pero tiene que volver a intentarlo. El administrador recibe la notificación INTEGRACION_CON_ERROR (RN-57).
- **Datos viejos.**
  - El estado de plano `RESPALDO`, la columna `plano.uso_respaldo` y la composición con la foto en `FfmpegComponedor` quedan solo para los videos creados antes.
  - Se pueden quitar cuando ya no haya videos con planos de respaldo.
