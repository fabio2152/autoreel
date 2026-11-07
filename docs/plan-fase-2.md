# Fase 2 — Negocio: diseño

Rama `fase-2-negocio`. Alcance de la sección 19: **onboarding, perfil, subida de archivos a S3 y catálogo**.
Casos de la sección 17 que se cierran aquí: **V08, V11 (parcial: el producto incompleto queda marcado como no utilizable), V12**. V10, V13 y V14 dependen del chat (fase 3); aquí se deja lista la regla RN-09 (`onboardingCompleto`) y el cálculo de temporadas que usará el carrusel.

Decisiones ya tomadas con el usuario: S3 local con **SeaweedFS 4.48** en docker compose (credenciales por variables de entorno, firma verificada); negocio de ejemplo con **imágenes de relleno** generadas en el perfil `dev`.

## 1. Paquete de nicho (`nicho/`)

- `PaqueteNicho` (interfaz) con lo que la fase 2 usa: `id()`, `nombre()`, `estilos()`, `preguntasOnboarding()`, `temporadas()`. Los métodos del chat, plantilla, movimientos, vocabulario, música e instrucciones se agregan en sus fases (regla 8: sin abstracciones sin uso).
- `PaquetePasteleria` (`@Component`) lee `resources/nichos/pasteleria/onboarding.json` y `temporadas.json` al arrancar y valida su forma (falla el arranque si el archivo está mal).
- `RegistroDeNichos` pasa a recibir todos los `PaqueteNicho` (lista de beans) y expone `buscar(id)` / `obligatorio(id)` / `todos()`. `Nicho(id, nombre)` se mantiene para el panel de admin.
- **Preguntas** (`Pregunta`): `id`, `paso` (2 o 3), `texto`, `ayuda`, `tipo` (`TEXTO`, `SELECCION_MULTIPLE`, `TELEFONO`), `opciones` (`{valor, texto}`), `obligatoria`, `maxLongitud`, `obligatoriaSi` (`{pregunta, contiene}`) y `grupo`.
- Preguntas de Pastelería (sección 7.1):
  - Paso 2: `especialidad` (multi, 5 opciones), `publico` (multi, 4 opciones), `ciudad` (texto, 60), `distrito` (texto, 60), `canales` (multi: `WHATSAPP`, `DELIVERY`, `TIENDA`; obligatoria: "al menos uno"), `whatsapp` (teléfono, obligatoria si `canales` contiene `WHATSAPP`), `horario` (texto, 120, obligatoria).
  - Paso 3 (opcional): `distintivo` (texto, 80), `palabrasProhibidas` (texto, 300, separadas por comas).
- **Temporadas** (`Temporada`): `id`, `nombre`, y su regla de fechas: `RANGO` (desde/hasta mes-día, puede cruzar el año: verano 21/12–20/03), `FECHA_FIJA` (mes-día, uno o varios días: Fiestas Patrias 28–29/07), `N_ESIMO_DOMINGO` (mes, n: Día de la Madre 2.º domingo de mayo, Día del Padre 3.º de junio), `MES` (Navidad: diciembre) y `TODO_EL_ANIO`. `CalculadoraDeTemporadas.vigentes(fecha)` y `proximas(fecha, 15 días)` (RN de la sección 7.7). Se usa en la fase 3; se escribe y prueba ahora porque define los datos del paquete.
- Estilos: `CASERO`, `ELEGANTE`, `DIVERTIDO` con su texto visible.
- `GET /api/nicho/configuracion` (rol NEGOCIO): nicho, estilos, preguntas, temporadas (id, nombre).

## 2. Almacenamiento (`almacenamiento/`)

- Puerto `AlmacenDeArchivos`: `urlDeSubida(clave, tipoContenido, tamano, duracion)`, `urlDeDescarga(clave, duracion)`, `leer(clave, maxBytes)` → `ArchivoAlmacenado(bytes, tipoContenido, tamano)` u `Optional.empty()`, `eliminar(clave)`.
- Adaptador `S3Almacen` (AWS SDK v2 `S3Client` + `S3Presigner`, BOM 2.46.x): región, bucket, endpoint opcional (`S3_ENDPOINT`) con `forcePathStyle` cuando hay endpoint. Credenciales: cadena por defecto del SDK (variables `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` en dev; rol de la instancia en prod). Propiedad `autoreel.almacenamiento.crear-bucket` (solo `dev`) crea el bucket si falta.
- El SDK lee las variables `AWS_*` del entorno del proceso, no del `.env` importado por Spring: `PropiedadesAlmacenamiento` las recibe por propiedades (`${AWS_ACCESS_KEY_ID:}`) y, si vienen, usa `StaticCredentialsProvider`; si no, la cadena por defecto.
- **Subida directa desde el navegador** (sección 11.4): `POST /api/archivos/url-subida` con `{proposito: LOGO|FOTO_PRODUCTO|FOTO_LOCAL, tipoContenido, tamano}`.
  - Valida tipo y tamaño antes de firmar: LOGO solo `image/png` ≤ 5 MB (RN-10); fotos `image/jpeg|png|webp` ≤ 10 MB (RN-11).
  - Clave: `negocios/{negocioId}/{proposito-en-minusculas}/{uuid}.{ext}`. El negocio sale de la sesión, nunca del cliente.
  - Firma con `Content-Type` y `Content-Length` exactos; vence en 10 minutos. Responde `{clave, url, metodo: "PUT", cabeceras}`.
- **Verificación al usar la clave** (`VerificadorDeImagenes` en `almacenamiento/aplicacion`): cuando el perfil o un producto referencian una clave nueva, el backend:
  1. comprueba que la clave empiece por `negocios/{negocioId}/{proposito}/` (RNF-05: nadie puede usar archivos de otro);
  2. la lee (máx. tamaño del propósito + 1 byte) y comprueba tamaño real y formato real por contenido (no por extensión);
  3. lee ancho y alto sin decodificar la imagen entera (`ImageReader`), con soporte WebP por TwelveMonkeys `imageio-webp` 3.12.0;
  4. fotos: lado menor ≥ 1080 px (RN-11, V12) → si no, `FOTO_RESOLUCION_INSUFICIENTE`; horizontales se aceptan y la respuesta marca `horizontal=true` para el aviso de recorte. Se respeta la orientación EXIF de JPEG al decidir "horizontal".
  5. logo: PNG ≤ 5 MB; si no tiene canal alfa, se acepta con aviso `logoSinTransparencia`.
- Lectura: las respuestas que muestran imágenes incluyen `url` prefirmada de lectura (1 h).
- Archivos huérfanos (subidos y nunca usados) se toleran en el MVP; limpieza programada queda anotada para la fase 8 (regla de ciclo de vida del bucket sobre objetos sin uso).

## 3. Perfil y onboarding (`negocio/`)

- Migración `V3__perfil_y_catalogo.sql`:
  - `perfil_negocio`: `id`, `negocio_id` (único), `logo_clave`, `color_primario` (`#RRGGBB`), `color_secundario`, `estilo`, `foto_local_clave`, `respuestas jsonb`, `fecha_actualizacion`.
  - `producto`: `id`, `negocio_id`, `nombre` (50), `descripcion` (200), `temporadas jsonb` (lista de ids), `estado` (`ACTIVO`/`ARCHIVADO`), `fecha_creacion`.
  - `foto_producto`: `id`, `producto_id`, `tipo` (`ENTERO`/`DETALLE`/`PORCION`), `clave_s3`, `ancho`, `alto`; único (`producto_id`, `tipo`).
- El perfil se crea vacío junto con el negocio (en `ServicioGestionNegocios.crear`) y la migración crea perfiles vacíos para los negocios existentes.
- `GET /api/perfil` → nombre comercial (de `Negocio`), logo (clave + url), colores, estilo, foto del local, respuestas, `onboardingCompleto`.
- `PUT /api/perfil` → guarda parcialmente (borrador del asistente y edición en Ajustes): valida formato de lo que viene (colores hex, estilo del paquete, respuestas conocidas y del tipo correcto, longitudes, archivos verificados), pero no exige obligatorias. Si el onboarding ya está completo, sí exige obligatorias (RF-17: no se puede dejar el perfil incompleto después).
- `POST /api/perfil/completar-onboarding` → valida todas las obligatorias (nombre, logo, dos colores, estilo, paso 2) con `ValidadorDeRespuestas` y marca `negocio.onboardingCompleto = true` (RN-09).
- Nombre comercial editable por el negocio (máx. 120), validado igual que en el panel.

## 4. Catálogo (`catalogo/`)

- `GET /api/productos?estado=ACTIVO|ARCHIVADO` (por defecto activos) → productos del negocio de la sesión con temporadas, fotos (`tipo`, `url`, `ancho`, `alto`, `horizontal`) y `completo` (tiene las 3 fotos, RN-12).
- `GET /api/productos/{id}`, `POST /api/productos`, `PUT /api/productos/{id}`: `{nombre, descripcion, temporadas[], fotos: {ENTERO: clave, DETALLE: clave, PORCION: clave}}`. Las fotos pueden faltar (producto incompleto, V11); cada clave nueva pasa por el verificador. Temporadas validadas contra el paquete; al menos una (por defecto "todo el año" en el formulario).
- `POST /api/productos/{id}/archivar` y `/reactivar` (RN-13: archivado no se borra).
- **Aislamiento (RNF-05, V08)**: todas las consultas filtran por `negocioId` de la sesión; un id de otro negocio responde 404 `NO_ENCONTRADO` (no 403, para no revelar que existe).
- Admin: `GET /api/admin/negocios/{id}/productos` (solo lectura) y el detalle del negocio muestra el catálogo y el perfil (RF-05).

## 5. Frontend

- Navegación del negocio (móvil primero): barra inferior con **Inicio** (marcador del chat, fase 3), **Catálogo** y **Ajustes**.
- `RutaOnboarding`: si `onboardingCompleto=false`, el negocio va a `/bienvenida` (pantalla completa) antes que a cualquier otra ruta propia.
- `onboarding/AsistenteOnboarding`: 3 pasos con barra de progreso y el aviso "Podrás cambiar estos datos cuando quieras en Ajustes". Guarda cada paso con `PUT /api/perfil` (si sale a mitad, retoma). Paso 1: nombre, logo (PNG, vista previa sobre fondo a cuadros), dos colores con selector y botón "Tomar del logo" (colores dominantes con canvas), estilo con botones. Pasos 2 y 3: preguntas del paquete renderizadas por tipo. Foto del local opcional en el paso 3. Al terminar: pantalla "Registra tus primeros productos" → catálogo.
- `catalogo/`: lista con fotos y etiquetas de temporada, filtro activos/archivados, aviso "Incompleto: faltan fotos". Formulario de producto con contadores de caracteres, selector de temporadas y tres `SubidorDeFoto` (entero, detalle, porción), cada uno con imagen de ejemplo (SVG ilustrativo) y las tres indicaciones (luz natural, sin personas ni manos, en vertical). Validación previa en el navegador (tipo, tamaño, lado menor ≥ 1080) para responder rápido; el backend decide.
- `ajustes/`: Perfil (mismos campos del asistente), Contraseña (RF-15, usa `cambiar-contrasena` con la actual) e Instagram (marcador, fase 6).
- `componentes/SubidorDeArchivo`: pide URL firmada → `PUT` directo con `fetch` → devuelve la clave; muestra progreso indeterminado y errores en español.
- Admin: detalle del negocio con perfil y catálogo en lugar de "Disponible en la fase 2".

## 6. Desarrollo local y configuración

- `docker-compose.yml`: servicio `seaweedfs` (`chrislusf/seaweedfs:4.48`, `server -s3 -dir=/data`, puerto `127.0.0.1:${S3_PORT:-8333}`, volumen, credenciales `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` del `.env`).
- `.env.example`: se activan `AWS_REGION`, `AWS_S3_BUCKET`, `S3_ENDPOINT`, `S3_PORT`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (valores de ejemplo, no reales), `DEMO_NEGOCIO_EMAIL`, `DEMO_NEGOCIO_PASSWORD`.
- **Negocio de ejemplo** (`DatosDeEjemplo`, `@Profile("dev")`, sección 16): si no existe el correo `DEMO_NEGOCIO_EMAIL`, crea "Pastelería Dulce Encanto" (Miraflores, Lima) con perfil completo, logo y tres productos (Torta Tres Leches de Fresa —verano—, Cheesecake de Maracuyá, Alfajores de Maicena), con imágenes de relleno 1080×1920 generadas en Java (color + texto) y subidas al almacén. Contraseña propia desde `DEMO_NEGOCIO_PASSWORD` (sin cambio obligatorio).
- Perfil `test`: un `AlmacenEnMemoria` (`@Primary` en la configuración de pruebas) para las pruebas de integración; además una prueba del adaptador `S3Almacen` contra SeaweedFS con Testcontainers (subida con URL firmada, lectura, firma inválida rechazada).

## 7. Pruebas (sección 18, sin TDD)

- Unitarias: `ValidadorDeRespuestas` (obligatorias, condicionales, grupo "al menos uno", longitudes, opciones), `CalculadoraDeTemporadas` (verano cruzando el año, 2.º domingo de mayo, 3.º de junio, próximas a 15 días), `VerificadorDeImagenes` (PNG/JPEG/WebP, 800 px rechazada, horizontal, tipo real distinto del declarado, tamaño excedido).
- Integración: V08 (otro negocio → 404 en GET/PUT/archivar), V11 (producto con 2 fotos → `completo=false`), V12 (foto de 800 px → 400 `FOTO_RESOLUCION_INSUFICIENTE`), clave de otro negocio rechazada, completar onboarding con faltantes → 400 y completo → `onboardingCompleto=true`, archivar/reactivar, admin ve el catálogo.

## 8. Cosas que quedan para otras fases

- RN-09 bloquea crear videos en la fase 3 (el chat consulta `onboardingCompleto`); V10, V13 y V14 se verifican allí.
- RN-14 (producto subido desde el chat sin guardarlo) agrega su columna en la fase 3.
- RN-15 (Rekognition) en la fase 9.


## Anexo: cambios tras la revisión del diseño (todos aceptados)


**ALTA**

1. **Hay una carrera entre verificar y usar la foto (TOCTOU), y el prefijo de la clave no aísla a cada negocio** (§2). Hoy la URL PUT firmada sirve 10 minutos para la misma clave que se guarda, así que con ella se puede reemplazar una foto ya verificada. El paso 1 compara solo un prefijo de texto, y una clave con `..` lo cumple. Además, una regla de ciclo de vida de S3 no puede reconocer "objetos sin uso". Arreglo:
   - `url-subida` firma solo claves de `subidas/negocios/{id}/{proposito}/{uuid}.{ext}`.
   - Se valida la clave completa con una regex anclada, armada con el negocio de la sesión y el propósito del campo: `^subidas/negocios/{id}/(logo|foto_producto|foto_local)/[0-9a-f-]{36}\.(png|jpg|webp)$`.
   - `leer` devuelve también el `eTag`. Después de verificar, se ejecuta `copiarSiCoincide(origen, destino, eTag)` (CopyObject con `copySourceIfMatch`) hacia `negocios/{id}/{proposito}/{uuid}.{ext}` y se borra la temporal.
   - En la BD solo se guardan claves definitivas, para las que nunca se firma una URL de escritura. El front toma la clave definitiva de la respuesta del PUT.
   - `ArchivoAlmacenado` lleva el `eTag`. Los huérfanos se resuelven con una regla de expiración de 1 día sobre `subidas/` (anotada para la fase 8), que reemplaza la "regla sobre objetos sin uso".
   - Pruebas de integración: un caso con `..` y otro con `%2e%2e`.

2. **No hay límite superior de dimensiones** (decompression bomb, una imagen chica en bytes que ocupa mucha memoria al decodificarse). En `VerificadorDeImagenes`, responder `IMAGEN_DEMASIADO_GRANDE` cuando el lado mayor pase de 8000 px o el total de 40 MP. Para el logo, lado menor ≥ 200 px y lado mayor ≤ 4000 px. Agregar el caso a la prueba unitaria.

3. **Falta la sección de documentación** (regla 11, §23). Agregar "§9 Documentación" con:
   - README: SeaweedFS en compose, cuenta demo y variables nuevas.
   - `arquitectura.md`: módulos `nicho`, `almacenamiento`, `catalogo` y `negocio`; puerto `AlmacenDeArchivos` → `S3Almacen`; diagrama de subida → verificación → copia.
   - `modelo-de-datos.md`: ER de V3, con la nota de que se guardan claves y no `logoUrl`/`fotoLocalUrl` como en 9.1.
   - `api.md`.
   - `guias/nuevo-nicho.md`: firma real de `PaqueteNicho`, `onboarding.json`/`temporadas.json`, registro por lista de beans; quitar los "(futuro, fase 2)".
   - `manual-negocio.md`: onboarding, catálogo y cómo tomar buenas fotos.
   - `manual-administrador.md`: detalle con perfil y catálogo.
   - `despliegue-aws.md`: bucket privado, CORS PUT/GET solo desde `APP_URL` con `AllowedHeaders: content-type`, IAM `s3:PutObject/GetObject/DeleteObject` sobre el bucket y regla de expiración de `subidas/`.
   - ADRs: SeaweedFS como S3 local; subida prefirmada con verificación y copia; contenido del nicho en JSON.
   - CHANGELOG.
   - `plan-de-desarrollo.md` (fila de la fase 2 y §5): cambiar los casos a "V08, V11 (parcial), V12" y pasar V10 a la fase 3.

**MEDIA**

4. **Las temporadas no tienen una semántica precisa** (§1). En `temporadas.json`:
   - Listar las 9 temporadas de 7.2 con un id estable y `clase` (`TEMPORADA` | `FECHA_ESPECIAL` | `SIEMPRE`):
     - VERANO: RANGO 12-21..03-20
     - INVIERNO: RANGO 06-21..09-22
     - NAVIDAD: MES 12
     - SAN_VALENTIN: FECHA_FIJA 02-14
     - DIA_MADRE: N_ESIMO_DOMINGO 5/2
     - DIA_PADRE: N_ESIMO_DOMINGO 6/3
     - FIESTAS_PATRIAS: FECHA_FIJA 07-28, 07-29
     - HALLOWEEN_CANCION_CRIOLLA: FECHA_FIJA 10-31
     - TODO_EL_ANIO: SIEMPRE
   - `vigentes(fecha)` excluye SIEMPRE; si no, V13 empata a verano con todo el año.
   - `proximas(fecha)` solo considera `FECHA_ESPECIAL`: no vigentes, con su siguiente inicio en (fecha, fecha+15], calculado en el año de `fecha` y en el siguiente.
   - Se llama siempre con `LocalDate.now(reloj)` (America/Lima).
   - Pruebas: 31/01 → San Valentín próxima; 15/02 → no; 10/12 → verano no vigente.

5. **La semántica de `PUT /api/perfil` está indefinida.** Un campo ausente no cambia; un `null` explícito borra (solo los opcionales). `respuestas` se fusiona por id de pregunta y un `null` elimina la clave. Si `onboardingCompleto=true`, `ValidadorDeRespuestas` completo se aplica al perfil resultante y, si falla, responde 400 `PERFIL_INCOMPLETO`. Al retomar, el asistente abre el primer paso con obligatorias faltantes. El logo y la foto del local se guardan apenas termina su subida.

6. **Catálogo** (§4):
   - Nombre obligatorio (1–50, se recortan los espacios) y descripción obligatoria (1–200), con `PRODUCTO_NOMBRE_INVALIDO` y `PRODUCTO_DESCRIPCION_INVALIDA`.
   - `PUT` reemplaza el mapa de fotos: un tipo ausente o `null` borra su fila, y una clave igual a la guardada no se vuelve a verificar.
   - El reemplazo se hace actualizando en el lugar la fila de ese tipo (`FotoProducto.reemplazar(clave, ancho, alto)`), no con orphanRemoval + insert: Hibernate hace el INSERT antes del DELETE y viola el único (`producto_id`, `tipo`).
   - Prueba de integración: reemplazar la foto ENTERO.

7. **La orientación EXIF no está resuelta.** `ImageReader.getWidth/getHeight` no aplica EXIF. Leer Orientation con `JPEGSegmentUtil.readSegments(iis, JPEG.APP1, "Exif")` + `TIFFReader` (de `imageio-metadata`, que ya llega con `imageio-jpeg`). Con los valores 5–8, intercambiar ancho y alto. Guardar en `foto_producto` las dimensiones ya orientadas más una columna `orientacion smallint` (o dejar escrito que la fase 5 la aplica al componer), y calcular `horizontal = ancho > alto`. Prueba unitaria con un JPEG con Orientation=6.

8. **Contrato de la subida directa** (§2 y §5):
   - Backend: `cabeceras` solo `{"Content-Type": tipo}`, sin `host` ni `content-length`, que el navegador prohíbe fijar. Validar `tamano > 0`.
   - Front: `SubidorDeArchivo` no usa `api/cliente.ts`, porque mandaría `credentials:'include'` y `X-XSRF-TOKEN` a S3. Usa `XMLHttpRequest` con `withCredentials=false` y `upload.onprogress` para mostrar el porcentaje, enviando `tamano = file.size` y `tipoContenido = file.type`.
   - Input con `accept="image/jpeg,image/png,image/webp"`, sin `capture`. Rechazar en el front un `file.type` vacío o fuera de la lista.
   - Ante 403 o un fallo de red, pedir una URL nueva y reintentar una vez. Guardar/Siguiente quedan desactivados mientras haya subidas en curso.

9. **El perfil `test` no arranca con S3.** Crear `S3Almacen`, `S3Client` y `S3Presigner` con `@ConditionalOnProperty(name="autoreel.almacenamiento.tipo", havingValue="s3", matchIfMissing=true)`. `AlmacenEnMemoria` se crea con `havingValue="memoria"`, sin `@Primary`, y `application-test.yml` lleva `autoreel.almacenamiento.tipo: memoria`. `S3AlmacenIT` arma `S3Almacen` a mano:
   - Contenedor: `chrislusf/seaweedfs:4.48`, `server -s3 -dir=/data`, con las variables de entorno de credenciales.
   - Espera: `Wait.forHttp("/").forPort(8333).forStatusCode(403)`, timeout de 2 min.
   - El mismo endpoint mapeado en el cliente, el presigner y el HTTP de la prueba.
   - Bucket de 3 a 63 caracteres.
   - Casos: PUT exacto → 200; tamaño distinto → 403; tipo distinto → 403; copia con ETag viejo → 412.

10. **El negocio de ejemplo está incompleto** (§6). Lo crea `ServicioGestionNegocios.crear`, con plan Lite, un monto y la fecha de pago a +30 días; el listado del admin parte de las suscripciones. Después:
    - Contraseña de `DEMO_NEGOCIO_PASSWORD` validada con `ReglasDeContrasena` y `debeCambiarContrasena=false`.
    - Perfil válido según `ValidadorDeRespuestas` y `onboardingCompleto=true`.
    - 3 fotos por producto (9 en total). Temporadas: Tres Leches → VERANO; las otras dos → TODO_EL_ANIO.
    - Idempotente por correo.
    - Si faltan las variables `DEMO_*` o el almacén falla: registrar un aviso y omitir la carga sin tumbar el arranque.
    - README: la demo en vivo (sección 20) usa otro correo, porque la Tres Leches ya queda sembrada.

11. **No hay tabla de errores de archivos.** Todos con 400 y en español:
    - `TIPO_NO_PERMITIDO`
    - `LOGO_DEBE_SER_PNG`
    - `ARCHIVO_DEMASIADO_GRANDE` (con el límite en MB)
    - `IMAGEN_DEMASIADO_GRANDE`
    - `FOTO_RESOLUCION_INSUFICIENTE`, con las medidas: "Tu foto mide 800 × 600 px; necesitamos al menos 1080 px en el lado más corto…"
    - `ARCHIVO_NO_SUBIDO` (`leer` vacío)
    - `ARCHIVO_INVALIDO` (contenido distinto del tipo declarado)
    
    Aceptar solo lectores con `formatName` en {png, jpeg, webp} que coincidan con el tipo declarado y la extensión. Rechazar WebP animado (bandera de animación de VP8X) y APNG (`acTL`). Al arrancar: `ImageIO.setUseCache(false)` y `ImageIO.scanForPlugins()`, y fallar el arranque si no hay lector `image/webp`.

12. **Las URLs de lectura firmadas se regeneran en cada respuesta y vencen.** En el backend, guardar en memoria la URL por clave y reutilizarla mientras le queden más de 10 min de 1 h. En las consultas `perfil` y `productos`: `staleTime` de 30 min, `refetchOnWindowFocus: true`, `refetchInterval` de 45 min y un `onError` del `<img>` que invalide la consulta una vez. Hace falta porque `main.tsx` tiene `refetchOnWindowFocus:false` global.

**BAJA**

13. **`Sesion`** (`auth/api/Sesion.java`) no trae `onboardingCompleto`. Agregarlo. El orden del router es `RutaProtegida` (cambio de contraseña) → `RutaOnboarding` → rutas del negocio. Tras `completar-onboarding`, invalidar `CLAVE_SESION` antes de navegar.

14. **Preguntas** (§1): quitar el campo `grupo` del modelo y de la prueba, porque `canales` + `obligatoria` ya cubren "al menos uno" (salvo que la pregunta 3 lo cambie). `TELEFONO`: se normalizan espacios y guiones, se acepta `^(\+?51)?9\d{8}$` y se guarda `+519XXXXXXXX`. El error es `RESPUESTA_INVALIDA` con "Escribe un celular de 9 dígitos que empiece con 9". En el front: `type="tel" inputMode="tel"`, con +51 visible.

15. **FOTO_LOCAL no tiene reglas propias.** Declararle las de RN-11 (jpeg/png/webp ≤ 10 MB, lado menor ≥ 1080, horizontal con aviso), porque es el fondo del cierre vertical.

16. **Logo transparente:** marcar la transparencia solo si algún píxel tiene alfa < 255 (`ColorModel.hasAlpha()` como filtro previo). Un PNG RGBA opaco hoy pasaría sin el aviso.

17. **"Tomar del logo"** calcula los colores desde el `File` local (`URL.createObjectURL`). Sobre un logo ya guardado, el canvas cargado desde la URL firmada de otro origen se contamina; en ese caso, `crossOrigin="anonymous"` más el CORS GET del bucket.

18. **Fin del onboarding (6.2.4):** la pantalla "Registra tus primeros productos" tiene dos botones: "Ir al catálogo" y "Más tarde", que lleva a Inicio (marcador del chat).

19. **Admin (RF-05):** agregar `perfil` a `NegocioDetalle`, con logo (URL firmada), colores, estilo, foto del local y respuestas con el texto de la pregunta resuelto desde el paquete. El listado de productos del admin incluye los archivados, con su estado.

20. **SDK:** el presigner usa `.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())`, porque su builder no tiene `forcePathStyle`. Usar `StaticCredentialsProvider` solo cuando hay `S3_ENDPOINT`; en otro caso, `DefaultCredentialsProvider`, para no perder `AWS_SESSION_TOKEN` en producción.

21. **Prueba en celular (RNF-03):** separar `S3_ENDPOINT` (el que usa el backend) de `S3_ENDPOINT_PUBLICO` (el del presigner; por defecto el mismo), y usar una variable `S3_BIND` en el puerto del compose. El README documenta la prueba desde la LAN (IP local y `npm run dev -- --host`) y la prueba manual en Safari de iOS y Chrome de Android antes de cerrar la fase.

Descartados:
- **Límite de tasa en `url-subida`:** los negocios los crea el admin y la expiración de `subidas/` ya acota el abuso.
- **Pregunta sobre la clave definitiva distinta:** es un detalle interno, ya resuelto en el punto 1.
- **"¿Archivado se reactiva?" y "¿incompleto como borrador?":** RN-12 y V11 suponen productos con dos fotos, y reactivar no contradice RN-13.


### Decisiones del usuario (2026-11-04)

- **Logo:** se exige PNG; uno sin transparencia se acepta con el aviso `logoSinTransparencia`.
- **Fotos:** se normalizan en el navegador antes de subirlas (rotación EXIF aplicada, lado mayor ≤ 2560 px, JPEG 0,88, sin metadatos ni GPS). El backend igual verifica todo.
- **Cómo te piden:** canales combinables (WhatsApp, delivery, tienda), al menos uno; WhatsApp obligatorio si se elige; horario siempre obligatorio.
