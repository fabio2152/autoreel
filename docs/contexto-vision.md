# AutoReel — Contexto, visión y hoja de ruta

Este documento reúne el **contexto completo** de AutoReel: de dónde nace, qué problema resuelve, por qué se tomaron las decisiones principales, cómo se piensa convertir en negocio y hacia dónde va. Complementa a `docs/especificacion.md`, que describe en detalle **qué construir en el MVP**.

Es un documento vivo: se actualiza cada vez que cambia una decisión, se valida una hipótesis o se completa una etapa.

---

## 1. Origen del proyecto

AutoReel nace en el curso **Diseño de Software**, a partir de una propuesta del docente **Jaime Moshe Farfán Madariaga**: una aplicación que automatice la creación de contenido que normalmente hace una agencia de marketing, conversando con una IA para producir contenido según los gustos del cliente, publicándolo directamente en Meta y con un administrador que gestione a todos los clientes.

El proyecto lo desarrolla una sola persona: **Fabio Ezequiel Malpartida Lema**. Piero Anghelo Pittman Tolentino, que formó parte del equipo inicial, ya no participa.

Desde el inicio se planteó con una doble intención:

- **Académica:** el MVP es el proyecto del curso y debe demostrar buenas decisiones de diseño, una arquitectura clara y una construcción ordenada.
- **Comercial:** después de la evaluación, la plataforma debe poder convertirse en un negocio real con clientes que paguen por ella.

Esa doble intención explica muchas decisiones: se eligió una arquitectura pensada para crecer, pero un alcance inicial pequeño y muy controlado.

---

## 2. El problema

Los negocios pequeños dependen cada vez más de Instagram y Facebook para vender, y el formato que más alcance logra hoy es el video corto (Reels). Pero:

- **No tienen presupuesto** para una agencia, un community manager o un productor de video.
- **No tienen tiempo ni conocimientos** para grabar, editar, escribir textos y elegir música.
- **Las herramientas generales de edición** (y las de IA) exigen saber qué pedir y cómo hacerlo.
- **La IA de video usada sin control produce resultados extraños:** manos deformes, caras raras, letras ilegibles y productos que cambian de forma. Un negocio no puede publicar eso sin dañar su imagen.

El resultado: publican poco, publican mal o no publican.

---

## 3. Visión y misión

**Visión:** que cualquier negocio pequeño, sin importar su presupuesto ni sus conocimientos, pueda mostrar sus productos en redes sociales con la calidad de una agencia.

**Misión:** convertir las fotos de los productos de un negocio en Reels atractivos y listos para publicar, en menos de dos minutos, con IA controlada que no comete los errores típicos.

**Propuesta de valor en una frase:** "Tu Reel del día, hecho y publicado en dos minutos, sin saber de marketing."

---

## 4. Principios del producto

Estas ideas guían todas las decisiones, presentes y futuras:

1. **Especialización por nicho.** Cada rubro tiene su propio paquete: preguntas, plantilla, movimientos, vocabulario y fechas. Un producto especializado da mejores resultados que uno general.
2. **La IA no inventa el producto.** Siempre se parte de fotos reales del negocio; la IA solo les da movimiento. Esto garantiza que el cliente vea su producto real y evita la publicidad engañosa.
3. **La IA solo hace lo que hace bien.** Textos, logos, precios y música los pone el sistema; la IA anima imágenes y redacta. Nunca se le pide generar personas, manos ni texto dentro del video.
4. **El sistema decide casi todo.** El usuario responde pocas preguntas con botones, aprueba y publica. Cada decisión que se le quita al usuario es una oportunidad menos de equivocarse.
5. **El video siempre se entrega.** Si la IA falla, hay respaldos que producen un resultado correcto sin ella.
6. **Proveedores intercambiables.** Ningún proveedor de IA es indispensable: los modelos cambian de precio y de calidad cada pocos meses, y AutoReel debe poder cambiarlos sin reescribirse.
7. **Crecer desbloqueando, no rehaciendo.** Cada versión futura habilita opciones que la arquitectura ya contempla.

---

## 5. Decisiones clave y su porqué

Un resumen de cómo se llegó al diseño actual, para no volver a discutir lo ya resuelto sin una razón nueva.

**Formato: solo video, 15 segundos, vertical.** Es el formato de mayor alcance en Instagram, y la duración corta reduce el costo de IA y la probabilidad de errores. Todos los videos comparten una estructura fija de 4 planos: gancho, producto, relleno o detalle, y cierre.

**Objetos y lugares, no personas.** Se evaluó el formato de "UGC con IA" (personas generadas que hablan a cámara). Se descartó para el MVP porque es más caro, es donde la IA más se equivoca (caras, manos, labios) y Meta exige etiquetar los anuncios con humanos fotorrealistas generados por IA. Animar productos es mucho más barato y más confiable.

**Primer nicho: pastelerías y postres.** Se compararon pastelerías, cafeterías de especialidad, florerías, productos de regalo, restaurantes, hospedajes, inmobiliarias y agencias de turismo. Ganaron las pastelerías por el equilibrio entre: producto muy visual que no necesita personas, contenido que se comparte solo (antojo y cumpleaños), movimientos simples y seguros, y gran cantidad de negocios en el Perú. Le siguen cafeterías y otros negocios de comida; florerías es la alternativa si los videos de comida no funcionan bien.

**Quién crea el video: el propio negocio.** No una agencia. La plataforma está pensada para que cualquier persona designada por el negocio, sin conocimientos, pueda usarla.

**Chat sin IA para reunir la información.** Las preguntas son fijas para que la experiencia sea predecible, rápida y probada. La IA entra después, para escribir el guion a partir de respuestas ya ordenadas.

**Dos modelos de IA, una sola integración.** Gemini escribe el guion y los textos; Veo anima las fotos. Gemini solo elige el movimiento de una lista permitida, y el sistema arma la instrucción para Veo con una plantilla fija. Ambos se usan con la misma API de Google.

**Modelo económico para empezar: Veo 3.1 Lite.** Permite validar el producto con el menor costo (alrededor de 0,60 dólares por video). Los modelos superiores quedan para planes más caros.

**Sin voz en off en la primera versión.** El costo no es el problema, sino el riesgo: pronunciaciones raras, sincronización y voces robóticas. Música y texto en pantalla son más seguros.

**Catálogo de productos con tres fotos.** Cada producto se registra una vez (entero, detalle y porción), y el video del día solo pide elegirlo. Se pueden subir productos nuevos el mismo día. Mostrar primero los de temporada era parte de la visión, pero por decisión del usuario queda fuera del MVP: el chat no usa temporadas ni fechas especiales y el carrusel va del producto más reciente al más antiguo (ADR 0013, `docs/decisiones/0013-sin-temporadas-en-el-mvp.md`).

**Administración manual en el MVP.** El administrador crea cada negocio, entrega una contraseña temporal y registra los pagos. Se evitan los correos automáticos y los pagos en línea hasta validar el producto.

**Arquitectura: API REST en Spring Boot y frontend en React, como monolito modular.** El curso parte de Java, la interfaz necesita interactividad (chat, storyboard, avisos), la misma API servirá a una app móvil, y el monolito modular es simple de desplegar pero ordenado para crecer. El backend usa **Spring Boot 4** (4.1.2, con Java 21) y no Spring Boot 3: la línea 3.x salió del soporte libre y ya no se ofrece en Spring Initializr (ADR 0006, `docs/decisiones/0006-spring-boot-4.md`).

**Planes y modelos de IA en la base de datos.** Agregar un plan o un modelo nuevo de un proveedor ya integrado no requiere programar, lo que permite probar modelos, crear planes superiores y otorgar beneficios temporales desde el panel.

**Despliegue en AWS.** S3 para los archivos (y para entregar a Instagram el video desde una dirección pública), RDS para PostgreSQL, EC2 para el backend con FFmpeg, y CloudFront para el frontend.

**Desarrollo sin TDD, con pruebas solo para la lógica crítica.** El foco del MVP está en la integración y la experiencia; las reglas más delicadas sí se cubren con pruebas.

---

## 6. Modelo de negocio

### 6.1 Clientes

- **Hoy:** pastelerías y negocios de postres independientes, principalmente en Lima.
- **Mañana:** cafeterías, restaurantes, heladerías, juguerías y otros negocios de comida; luego otros rubros visuales, como florerías y productos de regalo.
- **A futuro, como canal:** agencias de marketing que gestionen a varios negocios desde una sola cuenta (sección 7, etapa 4).

### 6.2 Ingresos

Suscripción mensual por negocio, según el plan:

- **Lite (MVP):** un video por día con los modelos más económicos. Precio a validar, sugerido entre 49 y 59 dólares mensuales.
- **Pro (futuro):** modelos de video de mayor calidad y más regeneraciones.
- **Premium (futuro):** los mejores modelos y opciones de producción personalizables.

Los **beneficios temporales** (por ejemplo, un día de Pro) sirven para fidelizar, compensar inconvenientes y mostrar el valor de los planes superiores.

### 6.3 Costos por cliente

- **Plan Lite:** unos 0,60 dólares por video con Veo 3.1 Lite. Con uso diario, unos 18 dólares al mes, y hasta unos 36 en el peor caso con todas las regeneraciones.
- **Plan Pro de referencia:** con Veo 3.1 Fast a 1080p, unos 1,44 dólares por video.
- **Costos fijos:** infraestructura en AWS, que se reparte entre todos los clientes y baja por cliente a medida que crece la base.
- **Precios de los proveedores:** cambian con frecuencia. Cada video registra su costo estimado para monitorear el margen real.

### 6.4 Canales de venta previstos

- **Venta directa** a pastelerías, con una demostración en vivo de su propio producto.
- **Referidos** entre negocios del mismo rubro.
- **Alianzas** con proveedores de insumos de pastelería, que ya visitan a estos negocios.
- **Contenido propio:** los Reels de AutoReel creados con AutoReel son la mejor demostración.

### 6.5 Diferenciación

Frente a editores generales (CapCut, Canva) y a herramientas generales de video con IA:

- **Cero curva de aprendizaje:** se responde un chat, no se edita.
- **Especialización:** plantillas, vocabulario y fechas pensados para cada rubro.
- **Calidad controlada:** la IA nunca genera lo que suele salir mal.
- **Todo en un lugar:** del producto al Reel publicado, sin descargar ni editar.

---

## 7. Hoja de ruta

### Etapa 0 — MVP del curso

- Un nicho: pastelerías.
- Un plan: Lite, con Gemini y Veo 3.1 Lite.
- Plantilla fija de 15 segundos, sin voz y sin personas.
- Panel de administración con negocios, pagos manuales, notificaciones, planes e integraciones.
- Publicación en Instagram.
- Despliegue en AWS.

### Etapa 1 — Piloto comercial

- **Entre 5 y 10 pastelerías** usando la plataforma durante uno o dos meses.
- Validar el precio, la frecuencia real de uso y la calidad percibida.
- Ajustar la plantilla, los movimientos y las preguntas según los resultados.
- Aprobar la revisión de la app en Meta para poder usarla con cualquier cuenta.
- Registrar la marca y el dominio.

### Etapa 2 — Producto más completo

- **Plan Pro** con un modelo de video superior y beneficios temporales en uso.
- **Opciones de producción, versión 2:** activar o desactivar música, texto y subtítulos, e incluir el local en las escenas.
- **Publicación en Facebook** además de Instagram.
- **Calendario de fechas especiales** con recordatorios.
- **Detección automática de rostros** en las fotos.
- **Métricas de las publicaciones** (alcance, reproducciones, interacciones) obtenidas de Instagram.

### Etapa 3 — Nuevos nichos

- **Cafeterías**, reutilizando gran parte del paquete de pastelería.
- **Otros negocios de comida:** heladerías, juguerías, restaurantes.
- **Florerías**, como rubro visual fuera de la comida.
- Posiblemente **varias plantillas por nicho**, para que los videos de un mismo negocio no se repitan.

### Etapa 4 — Escala

- **Registro público y pagos en línea** con una pasarela local.
- **Cuenta de agencia:** una agencia gestiona a varios negocios desde un solo acceso, retomando la idea original del proyecto.
- **Voz en off** generada (opciones de producción, versión 3).
- **Personas reales:** clips que el negocio sube y se insertan sin IA (versión 4).
- **Aplicación móvil**, usando la misma API.

### Etapa 5 — Inteligencia propia

- **Aprender qué funciona:** usar las métricas de los videos publicados para sugerir los mejores ganchos, productos y horarios de cada negocio.
- **Pruebas A/B de ganchos:** generar dos variantes y aprender cuál rinde más.
- **Personas generadas por IA** en un plan premium, con la etiqueta que exige Meta (versión 5), si la calidad y el costo lo justifican.

---

## 8. Riesgos y cómo se mitigan

- **Dependencia de Meta.** Sin la revisión aprobada, la app solo funciona con cuentas de prueba, y Meta puede cambiar sus reglas. *Mitigación:* iniciar la revisión temprano y mantener siempre la opción de descargar el video.
- **Cambios de precio o de calidad de los modelos de IA.** *Mitigación:* proveedores intercambiables, modelos administrables desde el panel y registro del costo de cada video.
- **Cierre del identificador *preview* de Veo 3.1 Lite en la API de Gemini.** Google lo cerró el 22/10/2026, así que el modelo de video previsto para el plan Lite no puede darse por disponible tal como estaba planeado. *Mitigación:* el proveedor de video es intercambiable (adaptador detrás de una interfaz) y el modelo es un dato editable de la tabla `modelo_ia` (la semilla usa `veo-3.1-lite-generate-001`); el proveedor real se decide en la fase 5, con su propio ADR. *Resuelto:* en la fase 5 se eligió Veo en Google Cloud (ADR 0015) y el 2026-11-05 el plan Lite pasó a Gemini Omni Flash en Google AI Studio, con Google Cloud listo como alternativa (ADR 0017). Desde la fase 7, el administrador cambia de modelo o de clave desde el panel sin programar (ADR 0018).
- **Videos de mala calidad.** *Mitigación:* plantilla fija, movimientos permitidos, prohibiciones explícitas, storyboard previo, regeneración por plano y respaldos sin IA.
- **Fotos malas del cliente.** *Mitigación:* guía con ejemplos al pedir cada foto y validación automática de resolución, orientación y, a futuro, rostros.
- **Publicidad engañosa.** *Mitigación:* la IA nunca altera el producto ni inventa precios, ingredientes o promociones.
- **Derechos de autor de la música y las tipografías.** *Mitigación:* solo material con licencia de uso comercial y con la licencia registrada.
- **Protección de datos personales.** *Mitigación:* cumplimiento de la Ley 29733, contraseñas cifradas, tokens cifrados y credenciales en Secrets Manager.
- **Margen insuficiente.** *Mitigación:* límite de un video diario y de regeneraciones por plan, y monitoreo mensual del costo por cliente.
- **Baja adopción:** el negocio deja de usarla después de unas semanas. *Mitigación:* un flujo de dos minutos y, después del MVP, recordatorios de fechas especiales y sugerencias de temporada (ADR 0013).

---

## 9. Indicadores de éxito

**Del producto**

- Tiempo promedio desde el inicio del chat hasta la aprobación del storyboard (meta: menos de 2 minutos).
- Porcentaje de videos aprobados sin regenerar ningún plano.
- Porcentaje de videos que usan algún respaldo sin IA.
- Porcentaje de videos generados que se publican.

**Del uso**

- Videos publicados por negocio por semana.
- Negocios activos en la semana.
- Retención mensual de clientes.

**Del negocio**

- Ingreso mensual recurrente.
- Costo de IA por cliente y por video.
- Margen por plan.
- Pagos vencidos y tiempo promedio de regularización.

---

## 10. Preguntas abiertas

- ¿Cuál es el precio que las pastelerías realmente están dispuestas a pagar?
- ¿Usarán un video diario, o la frecuencia real será menor?
- ¿Veo 3.1 Lite alcanza la calidad que esperan, o desde el inicio conviene un modelo superior?
- ¿Qué plantilla y qué ganchos generan más reproducciones?
- ¿Las agencias de marketing serán clientes o competidores?
- ¿Cuál es el siguiente nicho con mejor resultado: cafeterías o restaurantes?

---

## 11. Glosario

- **Reel:** video vertical corto de Instagram y Facebook.
- **Nicho:** rubro de negocio que la plataforma atiende con un paquete propio.
- **Paquete de nicho:** el conjunto de preguntas, plantilla, movimientos, vocabulario y fechas de un rubro.
- **Plano:** cada una de las cuatro partes de un video.
- **Gancho:** el primer plano, cuyo objetivo es detener el desplazamiento del usuario.
- **Storyboard:** la vista previa de los planos con sus fotos y textos, antes de generar el video.
- **Regeneración:** volver a generar con IA un solo plano.
- **Plan efectivo:** el plan que se aplica a un negocio en un momento dado; normalmente el de su suscripción, o el de un beneficio temporal si está vigente.
- **Beneficio temporal:** un plan superior otorgado por un período limitado.
- **Adaptador:** la pieza de código que conecta AutoReel con un proveedor de IA específico.
- **Imagen a video:** técnica en la que el modelo de IA anima una foto existente en lugar de inventar la escena.
- **MVP:** producto mínimo viable; la primera versión que resuelve el problema central.

---

## 12. Documentos relacionados

- **`docs/especificacion.md`:** especificación detallada del MVP, para el desarrollo.
- **`docs/decisiones/`**: los registros de decisiones de arquitectura.
- **`docs/manual-administrador.md`** y **`docs/manual-negocio.md`**: los manuales de uso.

La carpeta `docs/` es local y no se versiona en git (ADR 0008).
