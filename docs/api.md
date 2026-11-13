# AutoReel — API REST

> Estado: **fases 1 a 5 y 7** en `main`, más los proveedores de Google (V7) y la revisión del flujo del negocio (2026-11-06). *Actualización del 2026-11-12:* incluye `POST /api/videos/{id}/reintentar` (PR #12) y los cambios de la mejora de la calidad del video (ADR 0021): la `frase` del cierre, sin `avisoDeRespaldo` y "Reintentar" en la etapa del guion. Describe los endpoints que existen hoy en el código; los de Instagram (fase 6, en pausa) se marcan como **(futuro, fase 6)**. Arquitectura, flujo de seguridad, subida de archivos y agrupación de mensajes del chat: `docs/arquitectura.md`.

---

## 1. Documentación OpenAPI (springdoc)

El backend publica su documentación OpenAPI con **springdoc-openapi 3.2.0**, **solo en el perfil `dev`** (`application-dev.yml`). En los demás perfiles, `application.yml` la desactiva (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`) y esas rutas no existen.

| Recurso | Dirección |
|---|---|
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| Especificación OpenAPI (JSON) | <http://localhost:8080/v3/api-docs> |

La definición se arma en `compartido/config/ConfiguracionOpenApi`: título "AutoReel API"; la versión declarada en el código sigue siendo `fase-2` (no se actualizó en las fases 3 a 7).

Para levantarla:

```bash
docker compose up -d                                         # Postgres y SeaweedFS (desde la raíz)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev        # Git Bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"  # PowerShell
```

El perfil `dev` lee las variables del `.env` de la raíz (copiado desde `.env.example`). El administrador inicial se crea con `ADMIN_EMAIL` y `ADMIN_PASSWORD`, y el negocio de ejemplo "Pastelería Dulce Encanto" con `DEMO_NEGOCIO_EMAIL` y `DEMO_NEGOCIO_PASSWORD` (con perfil completo y tres productos). Con esa cuenta se pueden probar los endpoints del rol `NEGOCIO` sin pasar por el onboarding.

---

## 2. Cómo autenticarse

La API no usa la cabecera `Authorization`. La sesión es un **JWT HS256 en la cookie HttpOnly `autoreel_sesion`**, y las peticiones que modifican datos (todo lo que no es `GET`, `HEAD` u `OPTIONS`, **incluido el login**) llevan protección **CSRF**: la cabecera `X-XSRF-TOKEN` debe repetir el valor de la cookie `XSRF-TOKEN`.

| Cookie | La escribe | `HttpOnly` | Uso |
|---|---|---|---|
| `XSRF-TOKEN` | Spring Security (`csrf().spa()` + `FiltroCookieCsrf`), en cualquier respuesta | No (el JavaScript la lee) | Se copia en la cabecera `X-XSRF-TOKEN` |
| `autoreel_sesion` | `POST /api/auth/login`, `POST /api/auth/cambiar-contrasena` y, desde la fase 7, `POST /api/admin/negocios/{id}/entrar` y `POST /api/auth/volver-al-panel` | Sí | Sesión de 8 h (`SESION_HORAS`). `Secure` excepto en los perfiles `dev` y `test`, `SameSite=Lax`, `Path=/` |

Pasos:

1. **`GET /api/auth/yo`** para obtener la cookie `XSRF-TOKEN`. Sin sesión responde `401 NO_AUTENTICADO`, pero la cookie llega igual.
2. **`POST /api/auth/login`** con el cuerpo `{"correo": "...", "contrasena": "..."}` y la cabecera `X-XSRF-TOKEN: <valor de la cookie XSRF-TOKEN>`. La respuesta deja la cookie `autoreel_sesion`.
3. Las siguientes peticiones envían ambas cookies y, si modifican datos, también la cabecera `X-XSRF-TOKEN`.
4. Si alguna responde `403 CSRF_INVALIDO`, repetir el paso 1 para renovar el token y reintentar (es lo que hace `frontend/src/api/cliente.ts`).

El `PUT` del archivo al almacenamiento (sección 3.3) es la excepción: va a otro servidor, **sin cookies ni `X-XSRF-TOKEN`**, y lo autoriza la firma de la URL.

### 2.1 Desde Swagger UI

`application-dev.yml` activa `springdoc.swagger-ui.csrf.enabled=true`: Swagger UI lee la cookie `XSRF-TOKEN` y envía `X-XSRF-TOKEN` por su cuenta.

1. Abrir <http://localhost:8080/swagger-ui.html>.
2. Ejecutar **`GET /api/auth/yo`** ("Try it out" → "Execute"). Responde 401 y deja la cookie `XSRF-TOKEN`.
3. Ejecutar **`POST /api/auth/login`** con el correo y la contraseña. El navegador guarda `autoreel_sesion` (en `dev` la cookie no es `Secure`, así que funciona con `http://localhost`).
4. Probar el resto de endpoints. Para cerrar sesión, `POST /api/auth/logout`.

Un usuario de negocio con contraseña temporal solo puede usar `GET /api/auth/yo` y `POST /api/auth/cambiar-contrasena` hasta crear su contraseña (RN-03).

### 2.2 Con curl (Git Bash)

```bash
API=http://localhost:8080
JAR=cookies.txt

# 1. Obtener XSRF-TOKEN (responde 401 sin sesión)
curl -s -c $JAR $API/api/auth/yo
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' $JAR)

# 2. Iniciar sesión
curl -s -b $JAR -c $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
     -d '{"correo":"admin@ejemplo.com","contrasena":"********"}' $API/api/auth/login

# 3. Petición autenticada de lectura
curl -s -b $JAR $API/api/admin/negocios

# 4. Petición autenticada que modifica datos (lleva la cabecera CSRF)
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' $JAR)
curl -s -b $JAR -c $JAR -H "X-XSRF-TOKEN: $XSRF" -X POST $API/api/admin/negocios/1/suspender
```

El correo y la contraseña del ejemplo son marcadores: usar los de `ADMIN_EMAIL` y `ADMIN_PASSWORD` (o `DEMO_NEGOCIO_EMAIL` y `DEMO_NEGOCIO_PASSWORD` para el negocio de ejemplo) del `.env` local.

Subir una foto con la sesión de un negocio (requiere `jq`; la foto debe medir al menos 720 px en su lado menor):

```bash
FOTO=torta.jpg
TAMANO=$(stat -c %s $FOTO)
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' $JAR)

# 1. Pedir la URL firmada (clave temporal subidas/negocios/{id}/foto_producto/{uuid}.jpg)
SUBIDA=$(curl -s -b $JAR -c $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
     -d "{\"proposito\":\"FOTO_PRODUCTO\",\"tipoContenido\":\"image/jpeg\",\"tamano\":$TAMANO}" \
     $API/api/archivos/url-subida)

# 2. PUT directo al almacenamiento, sin cookies, con el mismo Content-Type firmado
curl -s -X PUT -H "Content-Type: image/jpeg" --data-binary @$FOTO "$(echo $SUBIDA | jq -r .url)"

# 3. Usar la clave temporal: el backend la verifica y la copia a su clave definitiva
curl -s -b $JAR -c $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
     -d "{\"nombre\":\"Torta de chocolate\",\"descripcion\":\"Bizcocho de cacao con fudge.\",\"fotos\":{\"ENTERO\":\"$(echo $SUBIDA | jq -r .clave)\"}}" \
     $API/api/productos
```

Sin `temporadas`, el producto queda en `["TODO_EL_ANIO"]` (sección 4.4).

Recorrer el chat con la sesión del negocio de ejemplo (requiere `jq`; el onboarding debe estar completo):

```bash
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' $JAR)
POST() { curl -s -b $JAR -c $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" -X POST "$@"; }

# 1. Empezar (o retomar) la conversación
CONV=$(POST -d '{}' $API/api/chat/conversaciones)
ID=$(echo $CONV | jq .id)

# 2. Responder el paso actual: un botón, un producto del carrusel y un texto
PRODUCTO=$(POST -d '{"paso":"tipoVideo","opcion":"PRODUCTO_ESTRELLA"}' $API/api/chat/conversaciones/$ID/mensajes \
     | jq '.productos[0].id')
POST -d "{\"paso\":\"producto\",\"productoId\":$PRODUCTO}" $API/api/chat/conversaciones/$ID/mensajes | jq '.paso.id'
POST -d '{"paso":"especial","texto":"Recién horneada"}' $API/api/chat/conversaciones/$ID/mensajes | jq .procesarEnMs

# 3. Pasados 4 s, cerrar la respuesta agrupada
sleep 4; POST $API/api/chat/conversaciones/$ID/procesar | jq '.paso.id, .mensajes[-1].texto'
```

En el primer paso también se puede escribir en lugar de tocar un botón (`paso.admiteTexto: true`, desde la revisión del 2026-11-06): el texto no se agrupa, se guarda en `respuestas.tipoVideoLibre` y `tipoVideo` queda en `PRODUCTO_ESTRELLA` (sección 4.5).

```bash
POST -d '{"paso":"tipoVideo","texto":"Quiero mostrar la torta de fresa que más nos piden"}' \
     $API/api/chat/conversaciones/$ID/mensajes | jq '.respuestas, .paso.id'
```

Revisar y ajustar la propuesta (storyboard) de una conversación completa (fase 4):

```bash
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' $JAR)
PUT() { curl -s -b $JAR -c $JAR -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" -X PUT "$@"; }

# 1. El chat completo trae el id del video
VIDEO=$(curl -s -b $JAR $API/api/chat/conversaciones/actual | jq .videoId)

# 2. Consultar hasta que deje GUION_PENDIENTE (el procesador revisa cada 3 s)
curl -s -b $JAR $API/api/videos/$VIDEO | jq '.estado, [.planos[].texto], .cierre.precio, .cierre.frase'

# 3. Cambiar el texto del plano 1, la foto del plano 2 y el precio y la frase del cierre
PUT -d '{"texto":"Fresas recién llegadas hoy"}' $API/api/videos/$VIDEO/planos/1 | jq '.planos[0].texto'
PUT -d '{"foto":{"tipo":"DETALLE"}}' $API/api/videos/$VIDEO/planos/2 | jq '.planos[1].foto.tipo'
PUT -d '{"precio":"S/ 65","frase":"Recién salida del horno"}' $API/api/videos/$VIDEO/planos/4 | jq '.cierre.precio, .cierre.frase'

# 4. Lista de "Mis videos"
curl -s -b $JAR $API/api/videos | jq '.[] | {id, estado, producto}'
```

Crear el video, revisarlo, aprobarlo y descargarlo (fase 5; usa `POST` y `PUT` de los ejemplos anteriores):

```bash
# 1. "Crear mi video": consume el video del día y pasa a GENERANDO
#    (409 IA_NO_DISPONIBLE si el modelo de video del plan no está activo o no tiene credencial)
POST $API/api/videos/$VIDEO/aprobar-storyboard | jq '.estado, .generacion.videosHoy'

# 2. Consultar hasta LISTO (los clips se revisan cada 10 s; luego se compone)
curl -s -b $JAR $API/api/videos/$VIDEO | jq '.estado, [.planos[] | {orden, estado}], .generacion.urlVideo'

# 3. Revisión (con el video en LISTO): otro clip para el plano 2 (vuelve a GENERANDO)...
POST $API/api/videos/$VIDEO/planos/2/regenerar | jq '.estado, .generacion.regeneracionesUsadas'
# ...o, cuando vuelva a LISTO, solo un texto nuevo: recompone (COMPONIENDO) sin gastar regeneraciones
PUT -d '{"texto":"Recién salida del horno"}' $API/api/videos/$VIDEO/planos/1 | jq .estado

# 4. Aprobar y pedir el enlace de descarga (vence en 15 minutos)
POST $API/api/videos/$VIDEO/aprobar | jq .estado
curl -s -b $JAR $API/api/videos/$VIDEO/descarga | jq -r .url
```

Con la sesión del administrador (fase 7; usa `POST` de los ejemplos anteriores): registrar un pago, probar un modelo de IA y entrar como el negocio.

```bash
NEGOCIO=1

# 1. Registrar un pago: cubre la próxima fecha de pago y la avanza un mes (RN-42)
POST -d '{"fechaPago":"2026-11-06","monto":49.00,"medio":"YAPE","nota":"Pago de noviembre"}' \
     $API/api/admin/negocios/$NEGOCIO/pagos | jq '.pago.periodoCubierto, .proximaFechaPago'

# 2. Probar la conexión de un modelo (sin generar contenido) y ver el resultado guardado
MODELO=$(curl -s -b $JAR $API/api/admin/integraciones | jq '.modelos[] | select(.adaptador=="GEMINI_OMNI") | .id')
POST $API/api/admin/modelos-ia/$MODELO/probar | jq '.exitosa, .mensaje'

# 3. Entrar como el negocio: la cookie autoreel_sesion pasa a ser la del negocio
POST $API/api/admin/negocios/$NEGOCIO/entrar | jq '.rol, .nombreNegocio, .administrador'
curl -s -b $JAR $API/api/videos | jq 'length'        # ve lo mismo que el negocio

# 4. Volver al panel con la sesión normal del administrador
XSRF=$(awk '$6 == "XSRF-TOKEN" { print $7 }' $JAR)
POST $API/api/auth/volver-al-panel | jq '.rol, .administrador'
```

---

## 3. Endpoints

Rol: **Público** = sin sesión; **Autenticado** = cualquier sesión válida, incluida la de quien tiene contraseña temporal; **ADMIN** = rol administrador con su contraseña ya creada; **NEGOCIO** = usuario de un negocio activo con su contraseña ya creada. Todos los `POST` y `PUT` exigen la cabecera `X-XSRF-TOKEN`.

Además de los errores propios de cada fila, cualquier endpoint protegido puede responder `401 NO_AUTENTICADO`, `403 ACCESO_DENEGADO` o `403 CSRF_INVALIDO`; un cuerpo JSON ilegible o un parámetro con valor desconocido, `400 SOLICITUD_INVALIDA`; y cualquiera, `500 ERROR_INTERNO`.

### 3.1 Autenticación (fase 1)

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `POST` | `/api/auth/login` | Público | `{correo, contrasena}` | `200` `Sesion` + `Set-Cookie: autoreel_sesion` | `DATOS_INVALIDOS`, `CREDENCIALES_INVALIDAS`, `CUENTA_BLOQUEADA`, `NEGOCIO_SUSPENDIDO`, `CONTRASENA_TEMPORAL_VENCIDA` |
| `POST` | `/api/auth/logout` | Público | — | `204`, borra `autoreel_sesion` (`Max-Age=0`) | — |
| `GET` | `/api/auth/yo` | Autenticado | — | `200` `Sesion` | `NO_AUTENTICADO` |
| `POST` | `/api/auth/cambiar-contrasena` | Autenticado | `{contrasenaActual?, nueva, confirmacion}` | `200` `Sesion` + cookie nueva (la anterior deja de valer) | `DATOS_INVALIDOS`, `CONTRASENA_ACTUAL_INCORRECTA`, `CONTRASENA_TEMPORAL_VENCIDA`, `CONTRASENA_MUY_CORTA`, `CONTRASENA_MUY_LARGA`, `CONTRASENA_SIN_LETRA`, `CONTRASENA_SIN_NUMERO`, `CONTRASENAS_NO_COINCIDEN`, `CONTRASENA_REPETIDA`; desde la fase 7, `403 ACCION_NO_PERMITIDA_COMO_ADMINISTRADOR` si el administrador entró como el negocio |
| `POST` | `/api/auth/volver-al-panel` | NEGOCIO (solo la sesión de un administrador que entró como el negocio) | — | `200` `Sesion` del administrador (`rol: "ADMIN"`, `administrador: null`) + cookie nueva con su sesión normal. **Fase 7**, ADR 0019 | `ACCESO_DENEGADO` (403) con la sesión propia de un negocio |
| `GET` | `/actuator/health` | Público | — | `200` `{"status":"UP"}`, sin detalles | — |

**Entrar como el negocio (fase 7, RN-58, ADR 0019).** `POST /api/admin/negocios/{id}/entrar` (sección 3.2) cambia la cookie `autoreel_sesion` por una sesión **del administrador** con el claim del negocio: `FiltroJwtCookie` la atiende con el rol `NEGOCIO` y ese `negocioId` (`UsuarioAutenticado.comoAdministrador = true`), así que el administrador usa todos los endpoints del negocio (sección 3.3 en adelante) y **ninguno** de `/api/admin/**` (403 `ACCESO_DENEGADO`) hasta volver con `POST /api/auth/volver-al-panel`. `GET /api/auth/yo` devuelve entonces la `Sesion` del negocio con el campo `administrador` (sección 4.1). La sesión sigue siendo del administrador: vence a las 8 h y se invalida con su `version_sesion`, no con la del negocio. Cada entrada queda en el log de la aplicación.

### 3.2 Administración: negocios

Origen: `admin/api/AdminNegocioController`. Los pagos, el plan, los beneficios, las notificaciones, los planes, las integraciones y los costos de la fase 7 están en las secciones 3.6 y 3.7.

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `GET` | `/api/admin/negocios` | ADMIN | `?busqueda=` (nombre o correo), `?estadoPago=` (`AL_DIA`, `POR_VENCER`, `VENCE_HOY`, `VENCIDO`) | `200` `NegocioResumen[]`, ordenado por próxima fecha de pago y nombre. Desde la fase 5, con `videosDelMes`; desde la fase 7, con el plan que rige hoy, su `modeloVideo` y `conBeneficio` | `SOLICITUD_INVALIDA` (estado de pago desconocido) |
| `POST` | `/api/admin/negocios` | ADMIN | `SolicitudCrearNegocio` | `201` `ContrasenaTemporal`, `Cache-Control: no-store`. Desde la fase 2 también crea el perfil vacío del negocio | `DATOS_INVALIDOS`, `NICHO_INVALIDO`, `PLAN_INVALIDO`, `CORREO_YA_REGISTRADO`, `CONFLICTO` |
| `GET` | `/api/admin/negocios/{id}` | ADMIN | — | `200` `NegocioDetalle` (desde la fase 2, con `perfil`; desde la fase 5, con `videos`; desde la fase 7, con `planEfectivo`, `costoDelMes` y el modelo y el costo de cada video) | `NO_ENCONTRADO` |
| `POST` | `/api/admin/negocios/{id}/entrar` | ADMIN | — | `200` `Sesion` del negocio con `administrador` = correo del administrador, y `Set-Cookie: autoreel_sesion` con la sesión "como el negocio" (sección 3.1). También con el negocio suspendido. **Fase 7**, RN-58 | `NO_ENCONTRADO` |
| `GET` | `/api/admin/negocios/{id}/productos` | ADMIN | — | `200` `ProductoVista[]`: **todo** el catálogo, activos y archivados, del más nuevo al más antiguo (RF-05). **Fase 2** | `NO_ENCONTRADO` |
| `POST` | `/api/admin/negocios/{id}/reiniciar-contrasena` | ADMIN | — | `200` `ContrasenaTemporal`, `Cache-Control: no-store` | `NO_ENCONTRADO` |
| `POST` | `/api/admin/negocios/{id}/suspender` | ADMIN | — | `204` | `NO_ENCONTRADO` |
| `POST` | `/api/admin/negocios/{id}/reactivar` | ADMIN | — | `204` | `NO_ENCONTRADO` |
| `GET` | `/api/admin/nichos` | ADMIN | — | `200` `Nicho[]` (uno por cada `PaqueteNicho` registrado) | — |

`GET /api/admin/planes` (la lista para elegir el plan al crear un negocio, `facturacion/api/PlanController`) ya no es de solo lectura: desde la fase 7 está en la sección 3.7, junto con crear y editar planes.

### 3.3 Negocio: nicho, archivos, perfil y catálogo (fase 2)

Todos usan el negocio de la sesión (`UsuarioAutenticado.negocioId`); el cliente nunca envía el id del negocio. Un producto de otro negocio responde `404 NO_ENCONTRADO`, igual que uno inexistente (RNF-05, V08).

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `GET` | `/api/nicho/configuracion` | NEGOCIO | — | `200` `ConfiguracionNicho`: estilos, preguntas del onboarding, temporadas y temporadas vigentes hoy (Lima) | `NO_ENCONTRADO`, `NICHO_DESCONOCIDO` (500) |
| `POST` | `/api/archivos/url-subida` | NEGOCIO | `SolicitudSubida {proposito, tipoContenido, tamano}` | `200` `SubidaFirmada {clave, url, metodo: "PUT", cabeceras}`, `Cache-Control: no-store`. La URL vence en 10 minutos | `DATOS_INVALIDOS`, `LOGO_DEBE_SER_PNG`, `TIPO_NO_PERMITIDO`, `ARCHIVO_VACIO`, `ARCHIVO_DEMASIADO_GRANDE` |
| `PUT` | *(`url` de `SubidaFirmada`)* | — (firma de la URL) | Bytes del archivo, con las `cabeceras` recibidas (`Content-Type`) y exactamente `tamano` bytes; sin cookies | `200` del almacenamiento (S3 o SeaweedFS) | `403` del almacenamiento (XML, no `ErrorApi`) si la URL venció o cambian el tipo o el tamaño |
| `GET` | `/api/perfil` | NEGOCIO | — | `200` `Perfil` | `NO_ENCONTRADO` |
| `PUT` | `/api/perfil` | NEGOCIO | Cambios parciales (ver 4.3) | `200` `Perfil`, con `avisos` | `SOLICITUD_INVALIDA`, `DATOS_INVALIDOS` (nombre), `COLOR_INVALIDO`, `ESTILO_INVALIDO`, `RESPUESTA_INVALIDA`, `PERFIL_INCOMPLETO` (si el onboarding ya estaba completo) y los errores de archivos de la sección 5.2 |
| `POST` | `/api/perfil/completar-onboarding` | NEGOCIO | — | `200` `Perfil` con `onboardingCompleto: true` (RN-09). Si ya estaba completo, responde igual | `PERFIL_INCOMPLETO` |
| `GET` | `/api/productos` | NEGOCIO | `?estado=` `ACTIVO` (por defecto) o `ARCHIVADO` | `200` `ProductoVista[]`, del más nuevo al más antiguo | `SOLICITUD_INVALIDA` (estado desconocido) |
| `GET` | `/api/productos/{id}` | NEGOCIO | — | `200` `ProductoVista` | `NO_ENCONTRADO` |
| `POST` | `/api/productos` | NEGOCIO | `SolicitudProducto` | `201` `ProductoVista`, con `avisos` | `PRODUCTO_NOMBRE_INVALIDO`, `PRODUCTO_DESCRIPCION_INVALIDA`, `TEMPORADA_INVALIDA` y los errores de archivos de la sección 5.2 |
| `PUT` | `/api/productos/{id}` | NEGOCIO | `SolicitudProducto` (reemplaza todo, incluido el mapa de fotos) | `200` `ProductoVista`, con `avisos` | `NO_ENCONTRADO` y los mismos de `POST /api/productos` |
| `POST` | `/api/productos/{id}/archivar` | NEGOCIO | — | `200` `ProductoVista` con `estado: "ARCHIVADO"` (no se borra, RN-13) | `NO_ENCONTRADO` |
| `POST` | `/api/productos/{id}/reactivar` | NEGOCIO | — | `200` `ProductoVista` con `estado: "ACTIVO"` | `NO_ENCONTRADO` |

El backend **no exige** el onboarding completo para usar el catálogo o el perfil: es el frontend (`RutaOnboarding`) el que lleva al negocio a `/bienvenida`. RN-09 (no crear videos sin onboarding) se aplica al empezar el chat (sección 3.4).

Desde la fase 3, `GET /api/productos`, `GET`/`PUT /api/productos/{id}`, `archivar`, `reactivar` y `GET /api/admin/negocios/{id}/productos` **no ven** los productos subidos desde el chat sin guardarlos en el catálogo (`en_catalogo = false`, RN-14, V14): para esas rutas responden `404 NO_ENCONTRADO` o no aparecen en la lista.

### 3.4 Chat del video del día (fase 3)

Todos usan el negocio de la sesión. Una conversación de otro negocio responde `404 NO_ENCONTRADO`, igual que una inexistente (RNF-05). Las tres rutas que modifican datos responden la misma **`ConversacionVista`** (sección 4.5), con la que el frontend redibuja la pantalla completa.

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `POST` | `/api/chat/conversaciones` | NEGOCIO | Opcional: `{"empezarDeNuevo": true \| false}` (sin cuerpo = `false`) | `200` `ConversacionVista`. Sin `empezarDeNuevo`, **idempotente**: devuelve la `EN_CURSO` si existe o empieza una nueva. Con `empezarDeNuevo: true`, la `EN_CURSO` pasa a `ABANDONADA` y empieza otra (RN-19) | `ONBOARDING_INCOMPLETO` (409, RN-09, V10), `NO_ENCONTRADO` |
| `GET` | `/api/chat/conversaciones/actual` | NEGOCIO | — | `200` `ConversacionVista` de la `EN_CURSO` o, si no hay, de la última `COMPLETA` de hoy (día de Lima). **`204`** sin cuerpo si no hay ninguna. Solo lectura: no procesa mensajes pendientes | — |
| `POST` | `/api/chat/conversaciones/{id}/mensajes` | NEGOCIO | `SolicitudMensaje`: `paso` + una de cinco formas (sección 4.5). Desde la revisión del 2026-11-06, también `texto` en el paso `tipoVideo` | `200` `ConversacionVista` | `NO_ENCONTRADO`, `CONVERSACION_CERRADA`, `PASO_CAMBIADO`, `SOLICITUD_INVALIDA`, `PASO_NO_ADMITE_RESPUESTA`, `RESPUESTA_MUY_LARGA`, `OPCION_INVALIDA`, `PRODUCTO_NO_DISPONIBLE`, `PRODUCTO_INCOMPLETO`, `PRODUCTO_NOMBRE_INVALIDO`, `PRODUCTO_DESCRIPCION_INVALIDA` y los errores de archivos de la sección 5.2 |
| `POST` | `/api/chat/conversaciones/{id}/procesar` | NEGOCIO | — | `200` `ConversacionVista`. Cierra la respuesta agrupada si ya pasaron 4 s desde el último mensaje pendiente (RN-17); si no, devuelve la vista con el `procesarEnMs` que falta. Se puede llamar varias veces. Con la conversación fuera de `EN_CURSO` (completa o abandonada), 409 | `NO_ENCONTRADO`, `CONVERSACION_CERRADA` |

**Agregados a la sección 13 de la especificación:** `POST /api/chat/conversaciones/{id}/procesar` (la especificación solo lista `conversaciones`, `actual` y `mensajes`), el cuerpo opcional `empezarDeNuevo` de `POST /api/chat/conversaciones` y el campo `paso` obligatorio en `POST .../mensajes`. También queda documentado que los productos `en_catalogo = false` de conversaciones abandonadas se conservan en el MVP.

**Concurrencia:** `mensajes` y `procesar` bloquean la fila de la conversación (`PESSIMISTIC_WRITE`) hasta el fin de la transacción, y `POST /conversaciones` bloquea la fila del negocio: las peticiones simultáneas sobre la misma conversación se atienden de a una. El campo `paso` evita que una respuesta pensada para una pregunta se guarde en otra (doble toque, otra pestaña, reintento).

**Desde la fase 4, el video nace con el chat.** Cuando la conversación pasa a `COMPLETA`, `ServicioChat` publica el evento `ConversacionCompletada` y `video/aplicacion/CreadorDeVideos` crea el video en `GUION_PENDIENTE` **en la misma transacción**, con el plan efectivo del negocio y sus modelos (RN-54, `facturacion.ResolutorDePlanEfectivo`). La respuesta no espera a la IA: trae el **`videoId`** (sección 4.5) y el guion lo escribe después la tarea programada (sección 3.5). `GET .../actual` también devuelve el `videoId` de la conversación `COMPLETA` del día.

### 3.5 Videos: storyboard, generación, revisión y Mis videos (fases 4 y 5)

Todos usan el negocio de la sesión. Un video de otro negocio responde `404 NO_ENCONTRADO`, igual que uno inexistente (RNF-05, V08). Origen: `video/api/VideoController` y `video/aplicacion/ServicioVideos`. Todas las rutas de un video, salvo `descarga`, responden la misma **`VideoVista`** (sección 4.6), con la que el frontend redibuja la pantalla.

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `GET` | `/api/videos` | NEGOCIO | — | `200` `VideoResumen[]`: "Mis videos", los 50 más recientes (por `fechaCreacion` y luego `id`, de mayor a menor). Es el historial (RF-26) | — |
| `GET` | `/api/videos/{id}` | NEGOCIO | — | `200` `VideoVista`. Mientras el video está en `GUION_PENDIENTE`, `planos` viene vacío, `cierre` en `null` y `hashtags` vacío: la pantalla vuelve a consultar. En `GENERANDO` y `COMPONIENDO` también vuelve a consultar (cada 5 s) | `NO_ENCONTRADO` |
| `PUT` | `/api/videos/{id}/planos/{orden}` | NEGOCIO | `SolicitudPlano` (sección 4.6): en los planos 1 a 3, `texto`, `foto` o ambos; en el plano 4 (cierre), `precio`, `frase` (desde el 2026-11-12) o ambos | `200` `VideoVista` con el cambio. En `LISTO` (fase 5), el video pasa a `COMPONIENDO` o a `GENERANDO` (ver abajo) | `NO_ENCONTRADO`, `VIDEO_NO_EDITABLE`, `IA_NO_DISPONIBLE` y `SIN_REGENERACIONES` (foto nueva en `LISTO`), `SOLICITUD_INVALIDA`, `TEXTO_VACIO`, `TEXTO_MUY_LARGO`, `CARACTER_NO_PERMITIDO`, `PALABRA_PROHIBIDA`, `PRECIO_MUY_LARGO`, `FRASE_MUY_LARGA`, `FOTO_NO_DISPONIBLE` y los errores de archivos de la sección 5.2 |
| `POST` | `/api/videos/{id}/aprobar-storyboard` | NEGOCIO | — | `200` `VideoVista` con `estado: "GENERANDO"` y los planos 1 a 3 en `PENDIENTE`. "Crear mi video": consume el video del día (RN-26, V22). **Fase 5** | `NO_ENCONTRADO`, `VIDEO_NO_EDITABLE`, `IA_NO_DISPONIBLE` (desde la revisión del 2026-11-06), `VIDEOS_DEL_DIA_AGOTADOS` |
| `POST` | `/api/videos/{id}/planos/{orden}/regenerar` | NEGOCIO | — (`orden` de 1 a 3) | `200` `VideoVista` con `estado: "GENERANDO"`, ese plano en `PENDIENTE` y `regeneracionesUsadas` + 1 (RN-28, V26). El clip anterior se conserva hasta que llegue el nuevo. **Fase 5** | `NO_ENCONTRADO`, `VIDEO_NO_EDITABLE`, `IA_NO_DISPONIBLE`, `SIN_REGENERACIONES` |
| `POST` | `/api/videos/{id}/aprobar` | NEGOCIO | — | `200` `VideoVista` con `estado: "APROBADO"` (RF-24). La publicación llega en la fase 6. **Fase 5** | `NO_ENCONTRADO`, `VIDEO_NO_EDITABLE` |
| `GET` | `/api/videos/{id}/descarga` | NEGOCIO | — | `200` `Descarga` `{url}`: enlace firmado al MP4 final, vence en **15 minutos** y baja como archivo (`Content-Disposition: attachment`) con el nombre `autoreel-{producto}-{id}.mp4` (RF-25). En `LISTO`, `APROBADO`, `PROGRAMADO` o `PUBLICADO`. **Fase 5** | `NO_ENCONTRADO`, `VIDEO_NO_DISPONIBLE` |
| `POST` | `/api/videos/{id}/reintentar` | NEGOCIO | — | `200` `VideoVista`. Solo en `ERROR` con `generacion.puedeReintentar: true`. Si el video falló **al generarse** (PR #12), vuelve a `GENERANDO`, **consume el video del día** (RN-26) y pide solo los clips que faltan. Si falló **al escribir su guion** (desde el 2026-11-12, `reintentoUsaVideoDelDia: false`), vuelve a `GUION_PENDIENTE` con un plazo nuevo de 5 minutos, **sin consumir** el video del día y sin exigir la IA de video | `NO_ENCONTRADO`, `VIDEO_NO_EDITABLE` (fuera de `ERROR`), `VIDEO_NO_REINTENTABLE`, `IA_NO_DISPONIBLE` y `VIDEOS_DEL_DIA_AGOTADOS` (solo si falló al generarse) |
| `POST` | `/api/videos/{id}/descartar` | NEGOCIO | — | `200` `VideoVista` con `estado: "DESCARTADO"`. El video no se borra: sigue en "Mis videos". Desde la fase 5 también desde `LISTO`; el video del día ya usado **no se devuelve** | `NO_ENCONTRADO`, `VIDEO_NO_EDITABLE` |

- **Se edita o descarta en `STORYBOARD` y, desde la fase 5, en `LISTO` (revisión).** En cualquier otro estado (también mientras el guion está pendiente o el video se genera), `PUT` y `descartar` responden `409 VIDEO_NO_EDITABLE`. `aprobar-storyboard` solo acepta `STORYBOARD`; `regenerar` y `aprobar`, solo `LISTO`. Todas las que modifican bloquean la fila del video (`PESSIMISTIC_WRITE`) hasta el fin de la transacción.
- **Aprobar el storyboard (RN-26, V22).** Primero exige el modelo de video del video disponible (`FabricaDeProveedores.videoDisponible`: activo, con adaptador y con credencial); si no, `409 IA_NO_DISPONIBLE` sin consumir nada, porque desde la revisión del 2026-11-06 no hay respaldo sin IA para los clips. Después, `ServicioConsumo` crea (si falta) y bloquea la fila de `consumo_diario` del negocio para el **día de Lima**. Si `videos_generados` ya llegó a `videosPorDia` del plan aplicado al video (RN-54), responde `409 VIDEOS_DEL_DIA_AGOTADOS`; si no, suma 1, guarda `video.consumo_fecha`, elige la pista de música por el estilo del negocio (`video.pista_musical_id`, la misma para cada recomposición) y deja el video en `GENERANDO`. Los videos no usados no se acumulan: al día siguiente el contador empieza en 0.
- **Cambios en la revisión (`PUT` en `LISTO`):**
  - Un **texto**, un **precio** o una **frase** solo vuelven a componer: el video pasa a `COMPONIENDO` sin gastar regeneraciones ni videos del día (RN-29, V25).
  - Una **foto** nueva regenera ese clip y **cuenta como una regeneración** (`Video.regenerarPlano`, la misma puerta que `/regenerar`); con texto y foto en la misma solicitud cuenta una sola. Sin regeneraciones restantes, `409 SIN_REGENERACIONES` y no se guarda nada.
  - Una foto nueva **sin la IA disponible** (`iaDisponible: false`) responde `409 IA_NO_DISPONIBLE` y no se guarda nada (desde la revisión del 2026-11-06; antes el plano usaba el acercamiento de FFmpeg). El frontend desactiva el cambio de foto en la revisión.
- **Regenerar (RN-28).** Con la IA no disponible (sin credencial, modelo inactivo o sin adaptador), `409 IA_NO_DISPONIBLE`; con las regeneraciones del plan agotadas (`regeneracionesPorVideo`, 3 en Lite), `409 SIN_REGENERACIONES`. Un `orden` que no es un plano con foto (por ejemplo, el 4) responde `404 NO_ENCONTRADO` ("Ese plano no existe."). **Una regeneración que falla no se cobra:** se descuenta al pedirla y, si el plano no consigue el clip nuevo, el plano conserva su clip anterior, la regeneración se devuelve y la vista trae `generacion.avisoRevision` ("No pudimos crear el clip nuevo. Dejamos el anterior y no se descontó la regeneración.").
- **Cómo se generan los clips** (sin endpoint, fase 5): la tarea `ProcesadorDeVideos` (`@Scheduled`) tiene dos métodos, uno para los clips (`autoreel.tareas.clips-cada`, 10 s) y otro para la composición (`autoreel.tareas.composicion-cada`, 10 s). Cada uno toma hasta 5 videos y los reclama **5 minutos** con un `UPDATE` condicional (`video.trabajo_reclamado_hasta`).
  - `ServicioGeneracion`, por plano: `PENDIENTE` → envía la foto (normalizada en Java: orientación EXIF, recorte 9:16, JPEG) al modelo de video del plan con el puerto `GeneradorDeClips` (adaptador `GEMINI_OMNI` en el plan Lite desde V7, o `VEO` en Google Cloud) y pasa a `GENERANDO`; el intento se cuenta **antes** de enviar. `GENERANDO` → consulta: con el clip listo, lo guarda y registra su costo (segundos × costo referencial, RN-32; con Omni, los segundos que informa el proveedor); con una falla, vuelve a enviar.
  - **Sin clip (sección 8.5, RN-30), desde la revisión del 2026-11-06 sin respaldo sin IA (ADR 0020):** con **2 intentos fallidos**, **10 minutos** sin el clip desde el envío, **20 minutos** desde el último envío de un plano pendiente, o sin credencial, modelo inactivo o credencial o configuración rechazadas, el plano se queda sin clip. En una **regeneración**, conserva su clip anterior y la regeneración se devuelve (ver arriba). En la **primera generación**, el video pasa a `ERROR` con `motivoError` "No pudimos crear los clips con IA. Te devolvimos el video del día; inténtalo de nuevo en un rato.", los clips ya enviados se cobran (RN-32) y se devuelve el video del día (RN-27). Cada falla del proveedor crea además una notificación `INTEGRACION_CON_ERROR` para el administrador (RN-57, sección 3.7). El estado `RESPALDO` y `usoRespaldo` solo aparecen en videos anteriores a ese cambio.
  - Con los tres planos en `LISTO` (o `RESPALDO`, en videos anteriores), el video pasa a `COMPONIENDO`. `ServicioComposicion` arma el MP4 con `ComponedorDeVideo` (`FfmpegComponedor`: 1080×1920, 30 fps, H.264 y AAC, 15 s exactos, V23), lo guarda en `video.clave_final` y deja el video en `LISTO`.
  - **Si la primera composición falla 2 veces**, el video pasa a `ERROR` con `motivo_error` y se **devuelve el video del día** en el día de Lima del error, una sola vez (RN-27, V27). Si falla una **recomposición** (un cambio en la revisión), el video vuelve a `LISTO` con su MP4 anterior y `avisoRevision`.
  - La tarea se apaga con `autoreel.tareas.activas=false` (lo hace el perfil `test`). Detalle en `docs/arquitectura.md`.
- **Cómo se escribe el guion** (sin endpoint): la tarea `ProcesadorDeGuiones` (`@Scheduled`, cada 3 s, propiedad `autoreel.tareas.guiones-cada`) toma hasta 5 videos en `GUION_PENDIENTE` y los reclama 3 minutos con un `UPDATE` condicional, así dos ejecuciones nunca escriben el mismo. `ServicioGuion` llama al modelo de texto del video hasta 3 veces (RN-25) dentro de un plazo de 60 s, pasa cada respuesta por `ValidadorDeGuion` (RN-21 a RN-24) y crea los 4 planos (el cierre con el precio, la frase y la ubicación que propone la IA, desde el 2026-11-12) y deja el video en `STORYBOARD`. **Desde el 2026-11-12 no hay guion de respaldo** (ADR 0021):
  - Si el proveedor falla, no hay credencial o el modelo no se puede usar, el video sigue en `GUION_PENDIENTE` y se reintenta cada 45 s; pasados 5 minutos desde `inicioDelGuion`, pasa a `ERROR` ("No pudimos escribir tu guion con IA. Inténtalo de nuevo en un rato.").
  - Tres respuestas inválidas: `ERROR` ("No logramos escribir un guion que cumpla las reglas. Inténtalo de nuevo.").
  - Si el producto ya no tiene fotos: `ERROR` definitivo, sin llamar al modelo y con `puedeReintentar: false`.
  - Si un reclamo vence sin terminar (por ejemplo, el backend se reinició), el siguiente ciclo vuelve a llamar a la IA.
  - Un `ERROR` del guion se reintenta con `POST .../reintentar` sin consumir el video del día.
  - Antes, en todos estos casos (salvo sin fotos) se usaba el guion de respaldo.
  - La tarea se apaga con `autoreel.tareas.activas=false` (lo hace el perfil `test`). Detalle en `docs/arquitectura.md`.

**Agregados a la sección 13 de la especificación:** el cuerpo de `PUT .../planos/{orden}` (texto, foto por tipo o por clave subida, precio y, desde el 2026-11-12, frase del cierre), y el campo `videoId` en `ConversacionVista`. Desde el PR #12, `POST .../reintentar`. En la fase 5: `PUT .../planos/{orden}` y `descartar` también en `LISTO`, la forma `{url}` de `descarga` y el objeto `generacion` de `VideoVista` (`docs/plan-fase-5.md`).

### 3.6 Administración: pagos, plan y beneficios de un negocio (fase 7)

Origen: `facturacion/api/SuscripcionController` y `facturacion/aplicacion/ServicioSuscripciones`. Las cuatro rutas que modifican datos bloquean la fila de la suscripción (`SuscripcionRepositorio.bloquearPorNegocio`, `PESSIMISTIC_WRITE`): dos clics seguidos se atienden de a uno. Un negocio inexistente responde `404 NO_ENCONTRADO` ("No encontramos ese negocio."). Cuerpos y respuestas en la sección 4.8.

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `GET` | `/api/admin/negocios/{id}/pagos` | ADMIN | — | `200` `PagoVista[]`: todos los pagos del negocio, del período cubierto más reciente al más antiguo | `NO_ENCONTRADO` |
| `POST` | `/api/admin/negocios/{id}/pagos` | ADMIN | `SolicitudPago {fechaPago, monto, medio, nota?}` | `201` `PagoRegistrado {pago, proximaFechaPago}`. El pago cubre la `proximaFechaPago` actual (`periodoCubierto`) y la fecha avanza un mes conservando `diaPago`; si el mes no tiene ese día, se usa el último (RF-08, RN-42, V35). Marca como leídas las notificaciones de pago del negocio de esa fecha o anteriores | `DATOS_INVALIDOS`, `SOLICITUD_INVALIDA` (medio desconocido), `PAGO_FECHA_FUTURA`, `NO_ENCONTRADO`, `CONFLICTO` |
| `PUT` | `/api/admin/negocios/{id}/plan` | ADMIN | `SolicitudCambioPlan {planId, montoMensual}` | `204`. Cambia el plan y el monto de la suscripción; rige para los videos que se creen desde ahora, los que ya existen conservan su plan (RN-49, RN-54). No cambia la fecha de pago | `DATOS_INVALIDOS`, `PLAN_INVALIDO`, `NO_ENCONTRADO` |
| `GET` | `/api/admin/negocios/{id}/beneficios` | ADMIN | — | `200` `BeneficioVista[]`: los 10 más recientes (por `desde`, de mayor a menor), con su estado calculado | `NO_ENCONTRADO` |
| `POST` | `/api/admin/negocios/{id}/beneficios` | ADMIN | `SolicitudBeneficio {planId, desde, hasta, motivo}` | `201` `BeneficioVista`. Un `desde` ya pasado se guarda como "ahora" (RF-12, RN-50 a RN-53, V40, V41) | `DATOS_INVALIDOS`, `BENEFICIO_FECHAS_INVALIDAS`, `BENEFICIO_MUY_LARGO`, `PLAN_INVALIDO`, `BENEFICIO_MISMO_PLAN`, `BENEFICIO_CRUZADO`, `NO_ENCONTRADO` |
| `DELETE` | `/api/admin/negocios/{id}/beneficios/{beneficioId}` | ADMIN | — | `204`. Un beneficio vigente termina ahora (`hasta` = ahora) y queda en el historial; uno que todavía no empieza se borra; uno ya terminado no cambia | `NO_ENCONTRADO` (beneficio inexistente o de otro negocio) |

- **Fecha del pago.** Es la fecha en que el negocio pagó y no puede ser posterior al día de Lima (`PAGO_FECHA_FUTURA`). No decide qué período se cubre: siempre se cubre la `proximaFechaPago` vigente, aunque el pago llegue tarde o adelantado.
- **Pago repetido.** `pago_periodo_uk` (una suscripción no paga dos veces el mismo `periodo_cubierto`) es el último resguardo: como la suscripción está bloqueada, un segundo clic registra el período siguiente. Si alguna vez chocara, responde `409 CONFLICTO`.
- **Plan efectivo (RN-50).** Con un beneficio vigente, el negocio usa el plan del beneficio (videos por día, regeneraciones y modelos) para los videos que cree mientras dure; el monto y la fecha de pago no cambian (RN-53). Al terminar, vuelve solo a su plan (RN-52). Lo resuelve `ResolutorDePlanEfectivo` y lo muestran `NegocioResumen` y `NegocioDetalle.planEfectivo`.

### 3.7 Administración: notificaciones, planes, integraciones y costos (fase 7)

Origen: `facturacion/api/NotificacionController`, `facturacion/api/PlanController`, `admin/api/AdminIntegracionesController` (con `admin/aplicacion/ServicioIntegraciones`) y `costos/api/CostoController`. Cuerpos y respuestas en la sección 4.8.

**Notificaciones** (RF-09, RN-44, RN-45, RN-57, V34, V36)

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `GET` | `/api/admin/notificaciones` | ADMIN | — | `200` `BandejaVista {sinLeer, notificaciones}`: el total sin leer y las 50 más recientes, primero las no leídas y luego por fecha, de la más nueva a la más antigua | — |
| `POST` | `/api/admin/notificaciones/{id}/leida` | ADMIN | — | `204` | `NO_ENCONTRADO` |
| `POST` | `/api/admin/notificaciones/leidas` | ADMIN | — | `204`. Marca todas como leídas | — |

Las notificaciones no se crean por la API: las de pago (`PAGO_POR_VENCER`, `PAGO_HOY`, `PAGO_VENCIDO` y `SUGERENCIA_SUSPENSION` a los 7 días de vencido) las crea `TareaDiariaDePagos` (`@Scheduled`, 00:10 de Lima, y también al arrancar; se apaga con `autoreel.tareas.activas=false`), y las `INTEGRACION_CON_ERROR`, una falla de un proveedor de IA al escribir un guion o generar un clip (`AvisoDeIntegracionEnPanel`, una por modelo y por día). Ninguna se repite para el mismo tipo, negocio (o modelo) y período (restricción `notificacion_uk`, sección 3.18 de `docs/modelo-de-datos.md`). Un negocio con el pago vencido **no** se suspende solo (RN-45).

**Planes** (RF-10, RN-47)

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `GET` | `/api/admin/planes` | ADMIN | — | `200` `PlanResumen[]`, activos e inactivos, con cuántos negocios tiene cada uno | — |
| `POST` | `/api/admin/planes` | ADMIN | `SolicitudPlan` | `201` `PlanResumen` | `DATOS_INVALIDOS`, `MODELO_INVALIDO`, `MODELO_INACTIVO`, `PLAN_YA_EXISTE` |
| `PUT` | `/api/admin/planes/{id}` | ADMIN | `SolicitudPlan` (reemplaza todo) | `200` `PlanResumen`. Rige para los videos nuevos; los que ya existen conservan el plan y los modelos con los que nacieron (RN-54) | `NO_ENCONTRADO`, `DATOS_INVALIDOS`, `MODELO_INVALIDO`, `MODELO_INACTIVO`, `PLAN_YA_EXISTE` |

**Integraciones y credenciales** (RF-11, RN-48, RN-55, RN-56, V37, V38, ADR 0017 y 0018)

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `GET` | `/api/admin/integraciones` | ADMIN | — | `200` `Integraciones {modelos, credenciales, adaptadores}` | — |
| `POST` | `/api/admin/modelos-ia` | ADMIN | `SolicitudModelo` (con `tipo`) | `201` `ModeloVista`. El modelo **nace `INACTIVO`** y su `unidadCosto` sale del tipo (`TEXTO` → `POR_SOLICITUD`, `VIDEO` → `POR_SEGUNDO`). Solo con un adaptador que exista en el código (RN-56) | `DATOS_INVALIDOS`, `TIPO_REQUERIDO`, `ADAPTADOR_INEXISTENTE`, `MODELO_YA_EXISTE` |
| `PUT` | `/api/admin/modelos-ia/{id}` | ADMIN | `SolicitudModelo` (el `tipo` se ignora: no cambia) | `200` `ModeloVista`. No cambia el estado | `NO_ENCONTRADO`, `DATOS_INVALIDOS`, `ADAPTADOR_INEXISTENTE`, `MODELO_YA_EXISTE` |
| `POST` | `/api/admin/modelos-ia/{id}/probar` | ADMIN | — | `200` `ResultadoPrueba {exitosa, mensaje, fecha}`, también cuando la prueba falla. Consulta el modelo en el proveedor sin generar contenido ni registrar costo, y guarda el resultado en `ultimaPrueba`, `ultimaPruebaExitosa` y `ultimaPruebaMensaje` (V37) | `NO_ENCONTRADO` |
| `POST` | `/api/admin/modelos-ia/{id}/activar` | ADMIN | — | `200` `ModeloVista` con `estado: "ACTIVO"` | `NO_ENCONTRADO`, `ADAPTADOR_INEXISTENTE`, `SIN_CREDENCIAL` |
| `POST` | `/api/admin/modelos-ia/{id}/desactivar` | ADMIN | — | `200` `ModeloVista` con `estado: "INACTIVO"` | `NO_ENCONTRADO`, `MODELO_EN_USO` |
| `PUT` | `/api/admin/credenciales` | ADMIN | `SolicitudCredencial {nombre, valor}` | `200` `CredencialVista`, `Cache-Control: no-store`. Guarda o reemplaza el valor fuera de la base de datos; **la respuesta nunca lo incluye** (RN-55) | `DATOS_INVALIDOS`, `CREDENCIAL_INVALIDA`, `NO_ENCONTRADO` (ningún modelo usa ese nombre) |
| `DELETE` | `/api/admin/credenciales` | ADMIN | `?nombre=` (obligatorio) | `200` `CredencialVista`. Borra el valor pegado en el panel; vuelve a regir el de la configuración (`.env`), si existe | `SOLICITUD_INVALIDA` (sin `nombre`), `NO_ENCONTRADO` (ningún modelo usa ese nombre) |

- **Prueba de conexión.** Sin adaptador o sin credencial no llama al proveedor: responde `exitosa: false` con el motivo ("No hay credencial configurada para autoreel/gemini."). Con un error del proveedor, el mensaje empieza por "La credencial no funciona.", "El modelo está mal configurado." o "El proveedor no está disponible.". Con éxito, "La conexión funciona.".
- **Desactivar (RN-48).** No se desactiva un modelo que use un plan activo **o** un plan con negocios suscritos (`MODELO_EN_USO`, con los nombres de los planes): antes hay que asignar otro modelo a esos planes.
- **Credenciales (ADR 0018).** El valor se pega una vez y viaja solo del navegador al servidor. `SecretosDeConfiguracion` lo guarda en `credenciales/secretos-del-panel.json` (propiedad `autoreel.archivo-secretos-panel`, ignorado por git) y tiene prioridad sobre el `.env`. Un valor que empieza con `{` debe ser un JSON válido (la cuenta de servicio de Google Cloud); si no, `CREDENCIAL_INVALIDA`.

**Costos** (RF-07, V32)

| Método | Ruta | Rol | Cuerpo / parámetros | Respuesta correcta | Errores propios |
|---|---|---|---|---|---|
| `GET` | `/api/admin/costos` | ADMIN | `?mes=AAAA-MM` (opcional; por defecto, el mes en curso de Lima) | `200` `CostosDelMes {mes, total, negocios}`: el costo estimado de IA de cada negocio con algún costo en el mes, del más caro al más barato, y el total | `MES_INVALIDO` |

El mes es el calendario de Lima. El costo sale de `registro_costo` (RN-32): guiones (`GUION`) y clips (`CLIP`).

**Agregados a la sección 13 de la especificación** (secciones 3.6 y 3.7): la respuesta `PagoRegistrado`, con la nueva `proximaFechaPago`, y el efecto del `DELETE` de un beneficio (termina el vigente y borra el que todavía no empieza). Las rutas son las de la especificación.

---

## 4. Formas de los cuerpos

Todos los DTO son `record` de Java. Los instantes (`Instant`) viajan en ISO-8601 UTC; las fechas de negocio (`LocalDate`) como `yyyy-mm-dd`. Los campos sin valor viajan como `null`.

### 4.1 Autenticación y administración

**`Sesion`** (`auth.api.Sesion`)

```json
{ "id": 2, "correo": "dueno@pasteleria.pe", "rol": "NEGOCIO", "negocioId": 1,
  "nombreNegocio": "Pastelería Ejemplo", "debeCambiarContrasena": false, "onboardingCompleto": false,
  "administrador": null }
```

`negocioId` y `nombreNegocio` son `null` para el administrador. `onboardingCompleto` (fase 2) es el de su negocio y siempre `true` para el administrador.

`administrador` (fase 7) es `null` en una sesión normal. Cuando el administrador entró como un negocio (`POST /api/admin/negocios/{id}/entrar` y `GET /api/auth/yo` mientras dure), la `Sesion` es la del negocio (`Sesion.comoNegocio`): `rol: "NEGOCIO"`, `correo`, `negocioId`, `nombreNegocio` y `onboardingCompleto` del negocio, `debeCambiarContrasena: false`, `id` del administrador y `administrador` con su correo. El frontend usa ese campo para mostrar la franja "Volver al panel".

**`SolicitudCambioContrasena`**: `contrasenaActual` es obligatoria salvo en el primer cambio, cuando el usuario entró con la contraseña temporal; `nueva` y `confirmacion` son obligatorias.

**`SolicitudCrearNegocio`** (`admin.api`)

| Campo | Tipo | Validación |
|---|---|---|
| `nicho` | string | Obligatorio; debe existir en `RegistroDeNichos` (hoy solo `PASTELERIA`) |
| `nombre` | string | Obligatorio, máximo 120 caracteres |
| `correo` | string | Obligatorio, formato de correo, máximo 254; se guarda en minúsculas y es único |
| `planId` | número | Obligatorio; el plan debe existir y estar activo |
| `montoMensual` | decimal | Obligatorio, mayor que 0, hasta 8 enteros y 2 decimales |
| `fechaPrimerPago` | fecha | Obligatoria; queda como `proximaFechaPago` de la suscripción |

La suscripción toma como `fechaInicio` la fecha de creación (día de Lima). `Usuario`, `Negocio`, `PerfilNegocio` (vacío) y `Suscripcion` se crean en una sola transacción.

**`ContrasenaTemporal`**

```json
{ "negocioId": 1, "correo": "dueno@pasteleria.pe", "contrasenaTemporal": "<12 caracteres>",
  "enlace": "<APP_URL>/login", "vence": "2026-11-05T15:00:00Z" }
```

Se muestra una sola vez: la base de datos solo guarda su hash BCrypt (RN-02). Vence a las 48 horas.

**`NegocioResumen`**: `id`, `nombre`, `correo`, `nicho`, `plan`, `modeloVideo`, `conBeneficio`, `proximaFechaPago`, `estadoPago`, `videosDelMes`, `estado` (`ACTIVO` o `SUSPENDIDO`), `ultimoAcceso`. Desde la fase 5, `videosDelMes` es la suma de `consumo_diario.videos_generados` del mes en curso (calendario de Lima, RF-02): cuenta los storyboards aprobados y descuenta los devueltos por RN-27. Desde la fase 7, `plan` y `modeloVideo` (nombre del modelo de video) son los del plan que **rige hoy** (RN-50): el de un beneficio vigente, con `conBeneficio: true`, o el de la suscripción.

**`NegocioDetalle`**: `id`, `nombre`, `nicho`, `nombreNicho`, `estado`, `onboardingCompleto`, `fechaCreacion`, y seis objetos. El catálogo, los pagos y los beneficios se piden aparte.

- `usuario`: `correo`, `ultimoAcceso`, `debeCambiarContrasena`, `contrasenaTemporalVence`, `bloqueado`.
- `suscripcion`: `planId`, `plan`, `montoMensual`, `fechaInicio`, `proximaFechaPago`, `diaPago` (fase 7, el día del mes que se conserva al avanzar la fecha, RN-42), `diasAviso`, `estadoPago`. Es el plan contratado, aunque haya un beneficio vigente.
- `planEfectivo` (fase 7, RN-50): el plan que rige hoy, `{planId, plan, modeloTexto, modeloVideo, videosPorDia, regeneracionesPorVideo, beneficioHasta}`. `beneficioHasta` es el fin del beneficio que da ese plan, o `null` si es el de la suscripción.
- `costoDelMes` (fase 7, RF-07, V32): `{guion, clips, total, videos}`, el costo estimado de IA del mes de Lima en curso, en dólares; `videos` cuenta los videos con algún costo en el mes.
- `perfil` (fase 2, `ServicioPerfil.PerfilParaAdmin`): `perfil` (un `Perfil` completo, con las URLs firmadas del logo y la foto del local) y `respuestas`, la lista de `{pregunta, respuesta}` con el texto de la pregunta y de las opciones elegidas resuelto desde el paquete de nicho (las selecciones se unen con ", ").

```json
"perfil": {
  "perfil": { "nombre": "Pastelería Dulce Encanto", "nicho": "PASTELERIA", "...": "..." },
  "respuestas": [
    { "pregunta": "¿Cuál es tu especialidad?", "respuesta": "Tortas personalizadas, Postres individuales" },
    { "pregunta": "Número de WhatsApp", "respuesta": "+51987654321" }
  ]
}
```

- `videos` (fase 5, `NegocioDetalle.Videos`, RF-05): `delMes` (el mismo número que `videosDelMes` de la lista) y `recientes`, los **20** videos más recientes del negocio (por `fechaCreacion` y luego `id`), cada uno con `id`, `tipoVideo` (el **valor** del chat, por ejemplo `PROMOCION`, no la etiqueta), `estado`, `fechaCreacion`, desde la fase 7 `modeloVideo` (nombre del modelo de video con el que se creó, RN-54) y `costoEstimado` (`video.costo_estimado`, en dólares) y `motivoError` (o `null`). El campo `planosConRespaldo` de la fase 5 se quitó con la revisión del 2026-11-06 (ADR 0020): ya no hay respaldo sin IA para los clips.

```json
"planEfectivo": { "planId": 1, "plan": "Lite", "modeloTexto": "Gemini", "modeloVideo": "Gemini Omni Flash",
                  "videosPorDia": 1, "regeneracionesPorVideo": 3, "beneficioHasta": null },
"costoDelMes": { "guion": 0.010000, "clips": 0.840000, "total": 0.850000, "videos": 1 },
"videos": {
  "delMes": 3,
  "recientes": [
    { "id": 15, "tipoVideo": "PROMOCION", "estado": "LISTO", "fechaCreacion": "2026-11-04T15:01:10Z",
      "modeloVideo": "Gemini Omni Flash", "costoEstimado": 0.850000, "motivoError": null }
  ]
}
```

(Ejemplo ilustrativo.) `estadoPago` se calcula al leer con `CalculadoraEstadoPago` y el día de Lima (RN-43). El catálogo no viene en el detalle: se pide con `GET /api/admin/negocios/{id}/productos`; los pagos y los beneficios, con las rutas de la sección 3.6.

**`Nicho`**: `{ "id": "PASTELERIA", "nombre": "Pastelería" }`.

**`PlanResumen`** (`facturacion.api.PlanResumen`): ver la sección 4.8.

### 4.2 Nicho y archivos

**`ConfiguracionNicho`** (`nicho.api.NichoController.ConfiguracionNicho`)

```json
{
  "nicho": "PASTELERIA",
  "nombre": "Pastelería",
  "estilos": [ { "valor": "CASERO", "texto": "Casero" }, { "valor": "ELEGANTE", "texto": "Elegante" },
               { "valor": "DIVERTIDO", "texto": "Divertido" } ],
  "preguntas": [
    { "id": "canales", "paso": 2, "texto": "¿Cómo te piden?", "ayuda": "Marca al menos una opción.",
      "tipo": "SELECCION_MULTIPLE", "obligatoria": true, "maxLongitud": null,
      "opciones": [ { "valor": "WHATSAPP", "texto": "Por WhatsApp" }, { "valor": "DELIVERY", "texto": "Por apps de delivery" },
                    { "valor": "TIENDA", "texto": "En la tienda" } ],
      "obligatoriaSi": null, "maxOtro": 40 },
    { "id": "whatsapp", "paso": 2, "texto": "Número de WhatsApp", "ayuda": "Celular de 9 dígitos. Aparece en el cierre del video.",
      "tipo": "TELEFONO", "obligatoria": false, "maxLongitud": null, "opciones": [],
      "obligatoriaSi": { "pregunta": "canales", "contiene": "WHATSAPP" }, "maxOtro": null }
  ],
  "temporadas": [ { "id": "TODO_EL_ANIO", "nombre": "Todo el año" }, { "id": "VERANO", "nombre": "Verano" } ],
  "temporadasVigentes": [ "VERANO" ]
}
```

(Ejemplo recortado.) Las preguntas y temporadas salen de `resources/nichos/pasteleria/onboarding.json` y `temporadas.json`:

- `tipo`: `TEXTO`, `SELECCION_MULTIPLE` o `TELEFONO`. `paso`: 2 o 3 (el paso 1, la marca, es común a todos los nichos).
- Preguntas de Pastelería: `especialidad`, `publico`, `ciudad`, `distrito`, `canales`, `whatsapp`, `horario` (paso 2) y `distintivo`, `palabrasProhibidas` (paso 3, opcionales).
- `maxOtro` (revisión del 2026-11-06, `Pregunta.maxOtro`): en una `SELECCION_MULTIPLE`, la pregunta admite además un chip **"Otros"** escrito por el negocio, de hasta ese número de caracteres; `null` si no lo admite. En Pastelería lo tienen `especialidad`, `publico` y `canales`, con 40. El texto viaja como un elemento más de la lista en `PUT /api/perfil` (sección 4.3).
- Temporadas (el catálogo ya no las pide desde la revisión del 2026-11-06; se siguen exponiendo): `TODO_EL_ANIO`, `VERANO`, `INVIERNO`, `SAN_VALENTIN`, `DIA_DE_LA_MADRE`, `DIA_DEL_PADRE`, `FIESTAS_PATRIAS`, `HALLOWEEN_CANCION_CRIOLLA`, `NAVIDAD`. Solo se exponen `id` y `nombre`; la regla de fechas queda en el backend.
- `temporadasVigentes`: ids vigentes en el día de Lima, sin contar `TODO_EL_ANIO`.

**`SolicitudSubida`** (`almacenamiento.api.ArchivosController.SolicitudSubida`)

| Campo | Tipo | Validación |
|---|---|---|
| `proposito` | string | Obligatorio: `LOGO`, `FOTO_PRODUCTO` o `FOTO_LOCAL` |
| `tipoContenido` | string | Obligatorio. `LOGO`: solo `image/png`. Fotos: `image/jpeg`, `image/png` o `image/webp` |
| `tamano` | número | Obligatorio, en bytes; mayor que 0 y hasta 5 MB (`LOGO`, RN-10) o 10 MB (fotos, RN-11) |

**`SubidaFirmada`** (`ServicioDeArchivos.SubidaFirmada`)

```json
{ "clave": "subidas/negocios/1/foto_producto/3f6c2a0e-7b1d-4e8a-9c55-0d2f4b6a8e11.jpg",
  "url": "http://localhost:8333/autoreel-dev/subidas/negocios/1/foto_producto/...?X-Amz-Signature=...",
  "metodo": "PUT",
  "cabeceras": { "Content-Type": "image/jpeg" } }
```

- La `clave` es **temporal** (`subidas/negocios/{negocioId}/{logo|foto_producto|foto_local}/{uuid}.{png|jpg|webp}`) y es la que se envía en `PUT /api/perfil` o en los productos.
- La URL firma el `Content-Type` y el `Content-Length` exactos y vence en 10 minutos. El navegador no puede fijar `Content-Length`: lo calcula del cuerpo, que debe tener exactamente `tamano` bytes.
- Al usar la clave, el backend lee el archivo, verifica su contenido real (sección 5.2) y lo copia a la clave **definitiva** `negocios/{negocioId}/{carpeta}/{uuid}.{ext}` solo si no cambió desde que lo leyó (ETag); la temporal se borra cuando la transacción se confirma. Si la petición falla por otro motivo (por ejemplo, `PERFIL_INCOMPLETO`), la temporal sigue existiendo y se puede reenviar la misma clave. Las respuestas devuelven siempre la clave definitiva, que es la que se debe reenviar en ediciones posteriores para conservar el archivo.

**Imagen** (`Perfil.Imagen`): `{ "clave": "negocios/1/logo/....png", "url": "<URL GET firmada>" }`. La URL vence en una hora y el backend reutiliza la misma mientras le queden más de 15 minutos.

### 4.3 Perfil

**`Perfil`** (`negocio.api.Perfil`)

```json
{
  "nombre": "Pastelería Dulce Encanto",
  "nicho": "PASTELERIA",
  "logo": { "clave": "negocios/1/logo/....png", "url": "..." },
  "logoSinTransparencia": false,
  "colorPrimario": "#be123c",
  "colorSecundario": "#fde68a",
  "estilo": "CASERO",
  "fotoLocal": null,
  "respuestas": {
    "especialidad": ["TORTAS_PERSONALIZADAS", "POSTRES_INDIVIDUALES"],
    "publico": ["FAMILIAS", "CELEBRACIONES"],
    "ciudad": "Lima", "distrito": "Miraflores",
    "canales": ["WHATSAPP", "TIENDA"], "whatsapp": "+51987654321",
    "horario": "Lunes a sábado de 9 a. m. a 8 p. m."
  },
  "onboardingCompleto": true,
  "avisos": []
}
```

`avisos` solo trae algo en la respuesta del `PUT` que lo causó: `LOGO_SIN_TRANSPARENCIA` (el logo es PNG opaco; se acepta) y `FOTO_LOCAL_HORIZONTAL` (se recortará al formato vertical). `logoSinTransparencia` queda guardado mientras el logo no cambie.

**Cuerpo de `PUT /api/perfil`**: un objeto con cualquiera de estos campos. **Un campo ausente no cambia; uno en `null` se borra**; un campo desconocido responde `SOLICITUD_INVALIDA`.

| Campo | Tipo | Regla |
|---|---|---|
| `nombre` | string | Nombre comercial (se guarda en `Negocio`). Se recortan los espacios; obligatorio y de hasta 120 caracteres (`DATOS_INVALIDOS`) |
| `logo` | string \| null | Clave de una subida nueva con propósito `LOGO`, o la clave definitiva ya guardada (no cambia). Otra clave: `ARCHIVO_INVALIDO` |
| `colorPrimario`, `colorSecundario` | string \| null | `#RRGGBB`; se guardan en minúsculas (`COLOR_INVALIDO`) |
| `estilo` | string \| null | Uno de los `estilos` del paquete (`ESTILO_INVALIDO`) |
| `fotoLocal` | string \| null | Igual que `logo`, con propósito `FOTO_LOCAL` |
| `respuestas` | objeto | Se **fusiona** por id de pregunta: `null` borra esa respuesta. Texto para `TEXTO` y `TELEFONO`, lista de valores para `SELECCION_MULTIPLE`. Se normaliza: textos sin espacios a los lados y dentro de `maxLongitud`, selecciones sin repetidos, celular de 9 dígitos que empieza con 9 (acepta `+51`, `51`, espacios y guiones) guardado como `+519XXXXXXXX`; un texto vacío se quita. En una pregunta con `maxOtro`, un elemento de la lista que no es una opción es el texto de **"Otros"**: se le recortan los espacios (los repetidos quedan en uno), uno vacío se ignora y se guarda tal cual entre los valores, por ejemplo `["TORTAS_PERSONALIZADAS", "Postres veganos"]`. Solo uno por pregunta y de hasta `maxOtro` caracteres. Error: `RESPUESTA_INVALIDA` |

Si el onboarding ya estaba completo, el perfil resultante debe seguir completo (RF-17): si no, `PERFIL_INCOMPLETO` y no se guarda nada.

**Completo** (para `completar-onboarding` y para editar después): logo, los dos colores, estilo y todas las preguntas obligatorias respondidas, incluida `whatsapp` si `canales` contiene `WHATSAPP`. El primer faltante se informa en el mensaje de `PERFIL_INCOMPLETO` (por ejemplo, "Falta tu logo." o "Falta responder: Horario de atención").

### 4.4 Catálogo

**`SolicitudProducto`** (`catalogo.api.SolicitudProducto`)

```json
{ "nombre": "Torta Tres Leches de Fresa",
  "descripcion": "Bizcocho húmedo de tres leches, relleno de fresas frescas y cubierto con crema batida.",
  "fotos": { "ENTERO": "subidas/negocios/1/foto_producto/....jpg",
             "DETALLE": "negocios/1/foto_producto/....jpg" } }
```

| Campo | Regla |
|---|---|
| `nombre` | Se recortan los espacios; de 1 a 50 caracteres (`PRODUCTO_NOMBRE_INVALIDO`) |
| `descripcion` | Se recortan los espacios; de 1 a 200 caracteres (`PRODUCTO_DESCRIPCION_INVALIDA`). Es la única fuente de detalles del producto que usará la IA |
| `temporadas` | **Opcional** desde la revisión del 2026-11-06 (ADR 0013): ausente, `null` o vacía, el producto queda en `["TODO_EL_ANIO"]`, también en un `PUT` (reemplaza la guardada). Si viene, ids del paquete del nicho (`TEMPORADA_INVALIDA`); se quitan los repetidos. `PRODUCTO_SIN_TEMPORADA` ya no se responde |
| `fotos` | Mapa `ENTERO` / `DETALLE` / `PORCION` → clave. Por cada tipo: la misma clave guardada se conserva sin volver a verificarse; una clave de subida nueva (`subidas/…`, propósito `FOTO_PRODUCTO`) se verifica y se copia a su clave definitiva; un tipo ausente, `null` o vacío **se quita**. Cualquier otra clave: `ARCHIVO_INVALIDO`. Un tipo de foto desconocido: `SOLICITUD_INVALIDA` |

Un producto puede guardarse con menos de tres fotos: queda con `completo: false` y no podrá usarse en un video (RN-12, V11).

**`ProductoVista`** (`catalogo.api.ProductoVista`)

```json
{ "id": 7, "nombre": "Torta Tres Leches de Fresa", "descripcion": "...",
  "temporadas": ["VERANO"], "estado": "ACTIVO", "completo": false,
  "fotos": [
    { "tipo": "ENTERO", "clave": "negocios/1/foto_producto/....jpg", "url": "...", "ancho": 1080, "alto": 1920, "horizontal": false },
    { "tipo": "DETALLE", "clave": "negocios/1/foto_producto/....jpg", "url": "...", "ancho": 2560, "alto": 1440, "horizontal": true }
  ],
  "fechaCreacion": "2026-11-04T15:00:00Z",
  "avisos": ["FOTO_HORIZONTAL"] }
```

- `fotos` va ordenada por tipo (`ENTERO`, `DETALLE`, `PORCION`) y solo trae las que existen. `ancho` y `alto` son los de la foto tal como se ve (con la orientación EXIF aplicada); `horizontal = ancho > alto` (se recortará al formato vertical).
- `estado`: `ACTIVO` o `ARCHIVADO`. `completo`: tiene las tres fotos (RN-12).
- `avisos` solo en la respuesta del `POST` o `PUT` que lo causó: `FOTO_HORIZONTAL` (una sola vez aunque haya varias).

### 4.5 Chat (fase 3)

**`SolicitudConversacion`** (`chat.api.ChatController.SolicitudConversacion`): `{ "empezarDeNuevo": true }`. Opcional; sin cuerpo o con `false`, `POST /api/chat/conversaciones` devuelve la conversación en curso si existe.

**`SolicitudMensaje`** (`chat.api.SolicitudMensaje`): siempre lleva `paso` (el id del paso al que responde, tomado de `ConversacionVista.paso.id`) y **exactamente una** de estas cinco formas:

| Forma | Ejemplo | Paso que la admite | Qué hace |
|---|---|---|---|
| `texto` | `{"paso": "especial", "texto": "Bizcocho de chocolate"}` | `TEXTO` y, desde la revisión del 2026-11-06, `BOTONES` con `admiteTexto` | En un paso `TEXTO`: se guarda al instante como mensaje pendiente y se agrupa con los que lleguen en los 4 s siguientes (RN-17). Se recortan los espacios; vacío: `SOLICITUD_INVALIDA`; más de 500 caracteres en un solo mensaje: `RESPUESTA_MUY_LARGA`. La respuesta agrupada se evalúa al cerrarse (ver abajo). En el paso de botones `tipoVideo`: ver "Texto en el primer paso" |
| `opcion` | `{"paso": "tipoVideo", "opcion": "PROMOCION"}` | `BOTONES` | Se procesa al instante. El valor debe ser una de `paso.opciones` (`OPCION_INVALIDA`). El chat muestra la etiqueta ("Promoción") y `respuestas` guarda el valor |
| `productoId` | `{"paso": "producto", "productoId": 12}` | `PRODUCTO` | Producto del catálogo: del negocio, `ACTIVO`, en el catálogo y con sus tres fotos (V11); si no, `PRODUCTO_NO_DISPONIBLE` |
| `productoNuevo` | ver abajo | `PRODUCTO` | Crea el producto ("Subir uno nuevo hoy") y lo usa en el video (RN-14, V14) |
| `omitir` | `{"paso": "extra", "omitir": true}` | `TEXTO` con `omitible` | "No, gracias": descarta lo escrito y no agrupado, y guarda `null`. En un paso que no es omitible: `PASO_NO_ADMITE_RESPUESTA`. Si el paso es obligatorio en ese momento (precio en una Promoción), no se acepta: el chat muestra el mensaje fijo y repite la pregunta |

```json
{ "paso": "producto",
  "productoNuevo": {
    "nombre": "Torta de Lúcuma",
    "descripcion": "Bizcocho de vainilla con crema de lúcuma y pecanas.",
    "fotos": { "ENTERO": "subidas/negocios/1/foto_producto/....jpg",
               "DETALLE": "subidas/negocios/1/foto_producto/....jpg",
               "PORCION": "subidas/negocios/1/foto_producto/....jpg" },
    "guardarEnCatalogo": false } }
```

- `fotos`: las **tres** claves de subidas nuevas (sección 3.3, propósito `FOTO_PRODUCTO`); si falta alguna, `PRODUCTO_INCOMPLETO`. Se verifican y copian igual que en `POST /api/productos` (errores de la sección 5.2).
- `nombre` (1 a 50) y `descripcion` (1 a 200): las mismas reglas del catálogo (`PRODUCTO_NOMBRE_INVALIDO`, `PRODUCTO_DESCRIPCION_INVALIDA`).
- La temporada queda en `TODO_EL_ANIO` (el catálogo ya no pide temporadas).
- `guardarEnCatalogo: false` → el producto queda con `en_catalogo = false`: sirve para este video, pero no aparece en el catálogo, en el carrusel ni en el panel del administrador.

Validación de `POST .../mensajes`, en este orden: la conversación existe y es del negocio (`NO_ENCONTRADO`); está `EN_CURSO` (`CONVERSACION_CERRADA`); `paso` coincide con el paso actual (`PASO_CAMBIADO`); hay exactamente una forma (`SOLICITUD_INVALIDA`, "Envía una sola respuesta a la vez."); la forma corresponde al tipo del paso (`PASO_NO_ADMITE_RESPUESTA`).

**Texto en el primer paso** (revisión del 2026-11-06, `ServicioChat.escribirEnBotones`). El paso `tipoVideo` de `chat.json` tiene `opcionLibre: "PRODUCTO_ESTRELLA"`, y por eso la vista lo marca con `admiteTexto: true`. Si el negocio escribe en lugar de tocar un botón:

- El texto **no se agrupa**: se guarda al instante como un mensaje ya procesado y se evalúa en la misma petición con las reglas de abajo (pregunta, más de 500 caracteres, respuesta negativa, menos de 3 caracteres). Un rechazo responde `200` con el mensaje fijo y la misma pregunta.
- Si se acepta, `respuestas.tipoVideoLibre` guarda el texto, `respuestas.tipoVideo` queda en `PRODUCTO_ESTRELLA` (la forma del video) y el chat sigue con la pregunta del producto. El texto llega al guion como el tipo de video que pidió el negocio (`LectorDeDatosDelVideo`), y el `resumen` lo muestra en lugar de la etiqueta del botón.
- `video.tipo_video` sigue siendo `PRODUCTO_ESTRELLA`: "Mis videos" y la vista del video muestran "Producto estrella".

**Evaluación de la respuesta de texto agrupada** (al cerrarse el grupo, en `procesar` o en el siguiente `mensajes`): los textos pendientes se unen con un espacio, en orden, y:

1. Si contiene `?` o `¿`: mensaje fijo "Soy un asistente para reunir información, no puedo responder preguntas…" y se repite la pregunta (RN-18, V16).
2. Si pasa de 500 caracteres: pide resumirla y repite la pregunta.
3. Si es una respuesta negativa ("no", "nada", "sin precio", "no gracias"…; sin distinguir mayúsculas, tildes ni puntuación): en una pregunta opcional se guarda `null`; en una obligatoria se rechaza. En el precio de una Promoción, con el mensaje "Como es una promoción, necesito el precio o la promoción…" (RN-20, V17).
4. Si la pregunta es obligatoria y tiene menos de 3 caracteres: pide más detalle.
5. Si no, se guarda y el chat hace la siguiente pregunta. Tras la última (y tras revalidar que el producto sigue `ACTIVO` y completo), la conversación queda `COMPLETA` con el mensaje "¡Listo! Estoy preparando tu propuesta…". Si el producto ya no está disponible, el chat vuelve a la pregunta del producto.

Una respuesta rechazada **no es un error HTTP**: la petición responde `200` y la vista trae el mensaje fijo y la misma pregunta.

**`ConversacionVista`** (`chat.api.ConversacionVista`)

```json
{
  "id": 41,
  "estado": "EN_CURSO",
  "paso": { "id": "especial", "tipo": "TEXTO", "texto": "¿Qué lo hace especial hoy?", "opciones": [],
            "obligatoria": true, "omitible": false,
            "sugerencia": "Bizcocho húmedo de tres leches, relleno de fresas frescas y cubierto con crema batida.",
            "ejemplo": "Ej.: \"Bizcocho de vainilla con fresas frescas, recién hecho esta mañana\".",
            "admiteTexto": true },
  "mensajes": [
    { "id": 301, "autor": "SISTEMA", "texto": "¡Hola! ¿Qué video quieres crear hoy?", "fechaHora": "2026-11-04T15:00:00Z", "pendiente": false },
    { "id": 302, "autor": "USUARIO", "texto": "Promoción", "fechaHora": "2026-11-04T15:00:03Z", "pendiente": false },
    { "id": 303, "autor": "SISTEMA", "texto": "¿Qué producto mostramos?", "fechaHora": "2026-11-04T15:00:03Z", "pendiente": false },
    { "id": 304, "autor": "USUARIO", "texto": "Torta Tres Leches de Fresa", "fechaHora": "2026-11-04T15:00:09Z", "pendiente": false },
    { "id": 305, "autor": "SISTEMA", "texto": "¿Qué lo hace especial hoy?", "fechaHora": "2026-11-04T15:00:09Z", "pendiente": false },
    { "id": 306, "autor": "USUARIO", "texto": "Fresas recién llegadas", "fechaHora": "2026-11-04T15:00:20Z", "pendiente": true }
  ],
  "procesarEnMs": 2500,
  "respuestas": { "tipoVideo": "PROMOCION", "producto": 7 },
  "aviso": "Este chat está diseñado para recibir respuestas, no para recibir preguntas.",
  "maximoCaracteres": 500,
  "productos": [],
  "productosIncompletos": 0,
  "resumen": [],
  "videoId": null
}
```

| Campo | Contenido |
|---|---|
| `estado` | `EN_CURSO`, `COMPLETA` o `ABANDONADA` |
| `paso` | Pregunta que espera respuesta (`null` si la conversación terminó): `id` (se repite en `SolicitudMensaje.paso`), `tipo` (`BOTONES`, `PRODUCTO`, `TEXTO`), `texto`, `opciones` (`{valor, texto}`, solo en `BOTONES`), `obligatoria` (calculada con las respuestas ya dadas: el precio es obligatorio en una Promoción), `omitible` (se puede responder "No, gracias": el paso lo permite y no es obligatorio ahora), `sugerencia` (en `especial`, la descripción del producto elegido; si no, `null`) y, desde la revisión del 2026-11-06, `ejemplo` (ejemplo corto de respuesta de `chat.json`, que se muestra bajo la caja de texto; `null` si el paso no lo tiene, como `producto`) y `admiteTexto` (`true` en los pasos `TEXTO` y en un paso `BOTONES` con `opcionLibre`, hoy `tipoVideo`: la pantalla muestra la caja de texto) |
| `mensajes` | Toda la conversación en orden (`fechaHora`, `id`). `autor`: `SISTEMA` o `USUARIO`. `pendiente: true` = texto del usuario que todavía puede agruparse con el siguiente |
| `procesarEnMs` | Milisegundos que faltan para que se cierre la respuesta agrupada, calculados con el reloj del servidor; `0` si ya se cumplieron los 4 s; **`null`** si no hay mensajes pendientes. El frontend llama a `POST .../procesar` cuando llega a cero |
| `respuestas` | Respuestas reunidas, por id de paso: `{"tipoVideo": "PROMOCION", "producto": 12, "especial": "...", "precio": "S/ 65" \| null, "extra": "..." \| null}`. `producto` es el id del producto. Una respuesta negativa u omitida es `null`, nunca "no". Si el negocio escribió en el primer paso, además `"tipoVideoLibre": "..."` (y `tipoVideo` es `PRODUCTO_ESTRELLA`). Es la entrada del guion de la fase 4 (contrato en `docs/modelo-de-datos.md`, sección 3.9) |
| `aviso` | Aviso fijo del paquete de nicho (sección 7.3). Desde la revisión del 2026-11-06, el frontend lo abre con un botón "?" junto a la respuesta |
| `maximoCaracteres` | Límite de la respuesta agrupada (500), para el contador de la caja de texto |
| `productos` | Solo en el paso `PRODUCTO`: el carrusel, `{id, nombre, descripcion, foto}`, con la URL firmada de la foto `ENTERO`. Productos `ACTIVO`, en el catálogo y con sus tres fotos, del más reciente al más antiguo (sin orden por temporada). En otros pasos, `[]` |
| `productosIncompletos` | Solo en el paso `PRODUCTO`: cuántos productos activos del catálogo no aparecen por faltarles fotos |
| `resumen` | Solo con `estado: COMPLETA`: `{pregunta, respuesta}` por cada paso, con la etiqueta del botón (o el texto de `tipoVideoLibre`, si lo hay), el nombre del producto (aunque después se haya archivado) o el texto; "—" si quedó vacía |
| `videoId` | **Fase 4.** Solo con `estado: COMPLETA`: el id del video que nació al terminar la conversación, para abrir `/videos/{id}`. `null` mientras la conversación está en curso o si fue abandonada |

### 4.6 Videos (fases 4 y 5)

**`VideoResumen`** (`video.api.VideoResumen`), un elemento de `GET /api/videos`:

```json
{ "id": 15, "estado": "STORYBOARD", "tipoVideo": "Promoción", "producto": "Torta Tres Leches de Fresa",
  "foto": "<URL GET firmada de la foto ENTERO>", "fechaCreacion": "2026-11-04T15:01:10Z" }
```

- `tipoVideo` es la **etiqueta** del botón del chat ("Promoción"), no el valor (`PROMOCION`).
- `producto` y `foto` salen del producto elegido, aunque después se haya archivado. Si el producto ya no existe para el negocio, `producto` es "—" y `foto` es `null`.
- `estado`: uno de los de la sección 9.2 de la especificación. Hoy aparecen `GUION_PENDIENTE`, `STORYBOARD`, `GENERANDO`, `COMPONIENDO`, `LISTO`, `APROBADO`, `DESCARTADO` y `ERROR`; `PROGRAMADO` y `PUBLICADO` llegan en la fase 6.
- Mientras algún video está en `GUION_PENDIENTE`, `GENERANDO` o `COMPONIENDO`, la pantalla "Mis videos" vuelve a consultar cada 5 s. El aviso "Tu video está listo" del layout del negocio (`AvisoVideoListo`) usa esta misma lista cada 15 s mientras haya videos en `GENERANDO` o `COMPONIENDO` (no hay endpoint propio).

**`VideoVista`** (`video.api.VideoVista`), el video en todas sus etapas: storyboard (RF-21, RF-22), generación (RF-23), revisión (RF-24) y aprobado:

```json
{
  "id": 15,
  "estado": "STORYBOARD",
  "tipoVideo": "Promoción",
  "producto": { "id": 7, "nombre": "Torta Tres Leches de Fresa" },
  "plan": { "nombre": "Lite", "videosPorDia": 1, "regeneracionesPorVideo": 3 },
  "planos": [
    { "orden": 1, "nombre": "Gancho", "segundos": 3,
      "foto": { "tipo": "DETALLE", "url": "..." },
      "movimiento": { "id": "MACRO_DETALLE", "descripcion": "La cámara entra hasta un primer plano de la textura y la decoración" },
      "texto": "Fresas recién llegadas hoy", "maxPalabras": 6, "estado": null, "usoRespaldo": false },
    { "orden": 2, "nombre": "Producto", "segundos": 4, "foto": { "tipo": "ENTERO", "url": "..." },
      "movimiento": { "id": "ORBITA_FRONTAL", "descripcion": "La cámara gira alrededor del producto en un arco suave" },
      "texto": "Torta Tres Leches de Fresa", "maxPalabras": 5, "estado": null, "usoRespaldo": false },
    { "orden": 3, "nombre": "Relleno", "segundos": 4, "foto": { "tipo": "PORCION", "url": "..." },
      "movimiento": { "id": "PARALAJE_LATERAL", "descripcion": "La cámara se desplaza de lado mientras rodea el producto, con profundidad" },
      "texto": "Capas húmedas con crema batida", "maxPalabras": 7, "estado": null, "usoRespaldo": false }
  ],
  "cierre": { "orden": 4, "nombre": "Cierre", "segundos": 4, "precio": "S/ 65 la torta entera",
              "maximoCaracteresPrecio": 40, "frase": "Tu antojo te espera", "maxPalabrasFrase": 6,
              "llamado": "Pide por WhatsApp", "ubicacion": "Miraflores", "whatsapp": "+51987654321",
              "logo": "...", "colorPrimario": "#be123c", "colorSecundario": "#fde68a", "fotoLocal": null },
  "descripcion": "Hoy te presentamos nuestra Torta Tres Leches de Fresa...",
  "hashtags": ["#pasteleria", "#postres", "#tresleches", "#miraflores", "#dulceencanto"],
  "maximoPublicacion": 600,
  "fotosDelProducto": [ { "tipo": "ENTERO", "url": "..." }, { "tipo": "DETALLE", "url": "..." },
                        { "tipo": "PORCION", "url": "..." } ],
  "fechaCreacion": "2026-11-04T15:01:10Z",
  "inicioDelGuion": "2026-11-04T15:01:10Z",
  "generacion": {
    "videosHoy": { "usados": 0, "porDia": 1, "puedeAprobar": true },
    "urlVideo": null,
    "regeneracionesUsadas": 0,
    "iaDisponible": true,
    "avisoRevision": null,
    "motivoError": null,
    "puedeReintentar": false,
    "reintentoUsaVideoDelDia": false
  }
}
```

(Ejemplo ilustrativo, en `STORYBOARD`.) En `LISTO`, los planos traen `"estado": "LISTO"` (o `"RESPALDO"`, solo en videos anteriores a la revisión del 2026-11-06) y `generacion.urlVideo` trae el enlace del MP4 final.

| Campo | Contenido |
|---|---|
| `tipoVideo` | Etiqueta del tipo de video elegido en el chat |
| `plan` | Plan aplicado al video (RN-54): `nombre`, `videosPorDia` y, desde la fase 5, `regeneracionesPorVideo` (3 en Lite), con el que la pantalla calcula las regeneraciones restantes |
| `avisoDeRespaldo` | **Se quitó el 2026-11-12** (ADR 0021): ya no hay guion de respaldo. Hasta entonces traía "Preparamos una propuesta básica con tus respuestas…" si se había usado |
| `planos` | Planos 1 a 3 (Gancho, Producto, Relleno), con `segundos` y `maxPalabras` de la plantilla (6, 5 y 7 palabras). `foto.tipo` es `ENTERO`, `DETALLE` o `PORCION`, o `null` si el negocio subió una foto solo para este video. `movimiento.descripcion` es el movimiento en palabras simples (`movimientos.json`). Vacío mientras el guion está pendiente. **Fase 5:** `estado` del clip (`null` en el storyboard; después `PENDIENTE`, `GENERANDO` o `LISTO`, también durante `GENERANDO` para mostrar el avance) y `usoRespaldo`. Desde la revisión del 2026-11-06 no hay respaldo sin IA para los clips (ADR 0020): `usoRespaldo: true` y el estado `RESPALDO` (el plano animado con el acercamiento de FFmpeg) solo aparecen en videos anteriores; un plano nuevo sin clip deja el video en `ERROR` (sección 3.5) |
| `cierre` | Plano 4, sin clip (sección 7.4). Desde el 2026-11-12 sus textos los propone la IA: `precio` (solo si el negocio escribió un precio real, resumido con sus mismos números y moneda, hasta `maximoCaracteresPrecio` = 40; `null` si no hay, también si el negocio escribió una instrucción; antes era lo escrito en el chat, recortado), `frase` (gancho que va en grande cuando no hay precio; `null` en videos anteriores) y `maxPalabrasFrase` (6), `llamado` (el primero de `llamados` de la plantilla que coincide con sus canales, en el orden WhatsApp, delivery, tienda: "Pide por WhatsApp", "Pídelo por delivery" o "Te esperamos en {distrito}"), `ubicacion` (el distrito o, desde el 2026-11-12, un lugar más preciso que dijo el negocio), `whatsapp` (solo si vende por WhatsApp), `logo` y `fotoLocal` (URLs firmadas o `null`) y los dos colores. `null` mientras el guion está pendiente |
| `descripcion`, `hashtags` | Texto de la publicación y sus hashtags (entre 5 y 8, al menos uno del distrito, RN-24). En esta fase no se editan; la edición antes de publicar llega en la fase 6 |
| `maximoPublicacion` | 600: límite de la descripción **más** sus hashtags (RN-24) |
| `fotosDelProducto` | Las fotos que hoy tiene el producto, para la hoja "Cambiar foto" |
| `inicioDelGuion` | Desde el 2026-11-12. Desde cuándo se escribe el guion en curso: la creación del video o el último "Reintentar" de la etapa del guion (`video.guion_iniciado_en`, o `fechaCreacion` en videos anteriores). El frontend lo usa para el aviso "Está tardando más de lo normal" |
| `generacion` | **Fase 5** (`GeneracionVista`), presente en todos los estados. `videosHoy`: `{usados, porDia, puedeAprobar}`, los videos del día de Lima ya usados por el negocio (`consumo_diario`), los que permite el plan aplicado y si se puede aprobar otro (RN-26); con `puedeAprobar: false` el botón "Crear mi video" se desactiva. `urlVideo`: URL firmada del MP4 final (vence en 1 hora; si el reproductor falla, el frontend vuelve a pedir la vista), `null` hasta la primera composición. `regeneracionesUsadas` de este video (RN-28). `iaDisponible`: el modelo de video del video (`video.modelo_video_usado_id`) está activo, tiene adaptador y su credencial está configurada; con `false`, el frontend desactiva "Crear mi video", "Regenerar este plano" y el cambio de foto en la revisión (el backend responde `409 IA_NO_DISPONIBLE`). `avisoRevision`: "No pudimos aplicar tu último cambio; tu video anterior sigue disponible. Inténtalo de nuevo." si falló una recomposición, o "No pudimos crear el clip nuevo. Dejamos el anterior y no se descontó la regeneración." si falló una regeneración; si no, `null`. `motivoError`: solo en `ERROR` (por ejemplo, "No pudimos crear los clips con IA. Te devolvimos el video del día; inténtalo de nuevo en un rato.", "No pudimos armar el video. Te devolvimos el video del día." o, desde el 2026-11-12, "No pudimos escribir tu guion con IA. Inténtalo de nuevo en un rato."); si no, `null`. `puedeReintentar` (PR #12): si el video está en `ERROR` y se puede reintentar (falló al generarse o, desde el 2026-11-12, al escribir el guion, y no es un error definitivo). `reintentoUsaVideoDelDia` (desde el 2026-11-12): `true` solo si reintentar vuelve a consumir el video del día (falló al generarse); con `false` y `puedeReintentar`, el reintento vuelve a escribir el guion sin consumir |

**`SolicitudPlano`** (`video.api.SolicitudPlano`), cuerpo de `PUT /api/videos/{id}/planos/{orden}`:

| Forma | Ejemplo | Plano | Regla |
|---|---|---|---|
| `texto` | `{"texto": "Fresas recién llegadas hoy"}` | 1 a 3 | Se recortan los espacios. Vacío: `TEXTO_VACIO`. Más de 200 caracteres o más palabras que `maxPalabras`: `TEXTO_MUY_LARGO` (V21). Emojis, saltos de línea u otros caracteres de control: `CARACTER_NO_PERMITIDO`. Una frase prohibida del paquete o de las `palabrasProhibidas` del negocio: `PALABRA_PROHIBIDA`, con la frase en el mensaje |
| `foto.tipo` | `{"foto": {"tipo": "DETALLE"}}` | 1 a 3 | Usa la foto de ese tipo que hoy tiene el producto. Si ya no la tiene: `FOTO_NO_DISPONIBLE` |
| `foto.clave` | `{"foto": {"clave": "subidas/negocios/1/foto_producto/....jpg"}}` | 1 a 3 | Una subida nueva con propósito `FOTO_PRODUCTO` (sección 3.3). Se verifica y copia como en el catálogo (errores de la sección 5.2). Queda **solo en este plano**, con `foto.tipo: null`: no cambia el producto ni el catálogo |
| `precio` | `{"precio": "S/ 65"}` | 4 | Se recortan los espacios; hasta 40 caracteres (`PRECIO_MUY_LARGO`). `""` quita el precio (entonces el cierre muestra la frase). También pasa por `CARACTER_NO_PERMITIDO` y `PALABRA_PROHIBIDA` |
| `frase` | `{"frase": "Recién salida del horno"}` | 4 | Desde el 2026-11-12. Se recortan los espacios; hasta `maxPalabrasFrase` (6) palabras y 40 caracteres (`FRASE_MUY_LARGA`). `""` la quita. También pasa por `CARACTER_NO_PERMITIDO` y `PALABRA_PROHIBIDA`. Puede ir junto con `precio` |

- `texto` y `foto` pueden ir juntos. `foto` lleva **exactamente uno** de `tipo` o `clave`; con los dos o ninguno, `SOLICITUD_INVALIDA`.
- En los planos 1 a 3, un `precio` o un cuerpo sin `texto` ni `foto` responde `SOLICITUD_INVALIDA` ("Envía el texto o la foto nueva del plano."). En el plano 4, un cuerpo sin `precio` ni `frase`, o con `texto` o `foto`, responde `SOLICITUD_INVALIDA` ("En el cierre solo se pueden cambiar el precio y la frase."). En los planos 1 a 3, una `frase` también responde `SOLICITUD_INVALIDA`.
- Un `orden` que no existe en el video responde `404 NO_ENCONTRADO` ("Ese plano no existe.").
- Las palabras se cuentan como en el frontend (`Texto.contarPalabras` y `contarPalabras` de `api/videos.ts`): lo separado por espacios que tenga al menos una letra o un número ("S/ 65" son 2; "—" no cuenta).
- Los textos que escribe el negocio **no** pasan por la RN-22 (precios o promociones no mencionados): son suyos.
- **En `LISTO` (fase 5)** las reglas son las mismas; además, `texto`, `precio` o `frase` recomponen y `foto` regenera ese clip (sección 3.5), solo con la IA disponible.

**`Descarga`** (`VideoController.Descarga`), respuesta de `GET /api/videos/{id}/descarga`: `{ "url": "<URL GET firmada del MP4, con Content-Disposition: attachment>" }`. El nombre del archivo es `autoreel-` + el nombre del producto en minúsculas, sin tildes y con guiones + `-{id}.mp4` (por ejemplo, `autoreel-torta-tres-leches-de-fresa-15.mp4`); si el producto ya no existe, `autoreel-video-{id}.mp4`. El frontend navega a esa URL.

### 4.7 Efectos sobre las sesiones

| Operación | Efecto |
|---|---|
| `cambiar-contrasena` | Incrementa `version_sesion`: la cookie anterior deja de valer y se entrega una nueva (RN-05) |
| `reiniciar-contrasena` | Nueva contraseña temporal, incrementa `version_sesion`, reinicia los intentos fallidos y levanta el bloqueo (RN-05) |
| `suspender` | Negocio `SUSPENDIDO` e incrementa `version_sesion`: su siguiente petición responde 401 y no puede volver a iniciar sesión (RN-07) |
| `reactivar` | Negocio `ACTIVO`; el usuario debe volver a iniciar sesión |
| `logout` | Borra la cookie en el navegador; no cambia `version_sesion` |
| `completar-onboarding` | No cambia la cookie: el siguiente `GET /api/auth/yo` trae `onboardingCompleto: true` (el frontend actualiza la sesión en su caché) |
| `entrar` (fase 7) | Reemplaza la cookie del administrador por la sesión "como el negocio" (sección 3.1). No cambia ninguna `version_sesion`: la sesión propia del negocio, si tiene una abierta, sigue valiendo |
| `volver-al-panel` (fase 7) | Reemplaza la cookie por la sesión normal del administrador |
| `PUT .../plan`, pagos y beneficios (fase 7) | No tocan las sesiones. El plan nuevo o el del beneficio rige para el siguiente video que se cree (RN-49, RN-50) |

### 4.8 Facturación, integraciones y costos (fase 7)

**`SolicitudPago`** (`facturacion.api.SuscripcionDtos.SolicitudPago`), cuerpo de `POST /api/admin/negocios/{id}/pagos`:

| Campo | Tipo | Validación |
|---|---|---|
| `fechaPago` | fecha | Obligatoria; no posterior al día de Lima (`PAGO_FECHA_FUTURA`) |
| `monto` | decimal | Obligatorio, mayor que 0, hasta 8 enteros y 2 decimales. Puede ser distinto de `montoMensual` |
| `medio` | string | Obligatorio: `TRANSFERENCIA`, `YAPE`, `PLIN`, `EFECTIVO` u `OTRO` (`MedioPago`) |
| `nota` | string | Opcional, hasta 300 caracteres; se recortan los espacios y una vacía se guarda como `null` |

**`PagoRegistrado`**, respuesta del `POST`, y **`PagoVista`**, cada elemento del `GET`:

```json
{ "pago": { "id": 4, "fechaPago": "2026-11-06", "monto": 49.00, "medio": "YAPE",
            "periodoCubierto": "2026-11-05", "nota": "Pago de noviembre", "fechaRegistro": "2026-11-06T15:20:00Z" },
  "proximaFechaPago": "2026-12-05" }
```

`periodoCubierto` es la fecha de pago que salda (la `proximaFechaPago` antes del pago); `proximaFechaPago`, la nueva.

**`SolicitudCambioPlan`**, cuerpo de `PUT /api/admin/negocios/{id}/plan`: `planId` (obligatorio; el plan debe existir y estar activo, si no `PLAN_INVALIDO`) y `montoMensual` (obligatorio, mayor que 0, hasta 8 enteros y 2 decimales).

**`SolicitudBeneficio`**, cuerpo de `POST /api/admin/negocios/{id}/beneficios`:

| Campo | Tipo | Validación |
|---|---|---|
| `planId` | número | Obligatorio; plan activo (`PLAN_INVALIDO`) y distinto del de la suscripción (`BENEFICIO_MISMO_PLAN`) |
| `desde`, `hasta` | instante ISO-8601 | Obligatorios. `hasta` después de `desde` y todavía no pasado (`BENEFICIO_FECHAS_INVALIDAS`); como máximo 30 días (`BENEFICIO_MUY_LARGO`, RN-51); sin cruzarse con otro beneficio del negocio, vigente o programado (`BENEFICIO_CRUZADO`) |
| `motivo` | string | Obligatorio, hasta 300 caracteres |

**`BeneficioVista`**:

```json
{ "id": 2, "planId": 3, "plan": "Pro", "modeloVideo": "Veo 3.1 Lite",
  "desde": "2026-11-06T15:00:00Z", "hasta": "2026-11-07T15:00:00Z",
  "motivo": "Cortesía por la demora del jueves", "estado": "VIGENTE" }
```

`estado` se calcula al leer: `PROGRAMADO` (todavía no empieza), `VIGENTE` o `TERMINADO`. (El plan "Pro" del ejemplo no se siembra: lo crea el administrador.)

**`BandejaVista`** (`NotificacionController.BandejaVista`), respuesta de `GET /api/admin/notificaciones`:

```json
{ "sinLeer": 2,
  "notificaciones": [
    { "id": 9, "tipo": "PAGO_POR_VENCER", "negocioId": 1, "periodo": "2026-11-10",
      "mensaje": "El pago de Pastelería Dulce Encanto vence en 4 días.", "fecha": "2026-11-06T05:10:00Z", "leida": false },
    { "id": 8, "tipo": "INTEGRACION_CON_ERROR", "negocioId": null, "periodo": "2026-11-06",
      "mensaje": "Gemini Omni Flash: el proveedor no está disponible. …", "fecha": "2026-11-06T04:00:00Z", "leida": false }
  ] }
```

- `tipo`: `PAGO_POR_VENCER`, `PAGO_HOY`, `PAGO_VENCIDO`, `SUGERENCIA_SUSPENSION` o `INTEGRACION_CON_ERROR` (`TipoNotificacion`).
- `periodo`: la fecha de pago en las de pago y el día de Lima en las de integración. Las de integración no tienen `negocioId` (su modelo se guarda en la base de datos, pero no se expone).
- `mensaje`: hasta 300 caracteres, ya escrito para mostrar.

**`SolicitudPlan`** (`facturacion.api.SolicitudPlan`), cuerpo de `POST /api/admin/planes` y `PUT /api/admin/planes/{id}`:

| Campo | Tipo | Validación |
|---|---|---|
| `nombre` | string | Obligatorio, hasta 40 caracteres; único sin distinguir mayúsculas (`PLAN_YA_EXISTE`) |
| `descripcion` | string | Opcional, hasta 300 caracteres; vacía se guarda como `null` |
| `videosPorDia` | número | De 1 a 20 |
| `regeneracionesPorVideo` | número | De 0 a 20 |
| `modeloTextoId`, `modeloVideoId` | número | Obligatorios. Deben existir y ser del tipo correcto (`MODELO_INVALIDO`) y estar `ACTIVO` (`MODELO_INACTIVO`, RN-47) |
| `precioReferencial` | decimal | Opcional, mayor o igual que 0, hasta 8 enteros y 2 decimales |
| `activo` | boolean | Si falta, `false`. Solo un plan activo se puede asignar a un negocio o a un beneficio |

Las opciones de producción y `permitePersonas` no se editan: un plan nuevo toma las fijas del MVP (sección 8.6 de la especificación).

**`PlanResumen`** (`facturacion.api.PlanResumen`): `id`, `nombre`, `descripcion`, `videosPorDia`, `regeneracionesPorVideo`, `modeloTextoId` y `modeloTexto` (nombre), `modeloVideoId` y `modeloVideo` (nombre), `precioReferencial` (`null` en el plan Lite sembrado), `activo` y, desde la fase 7, `negocios` (cuántas suscripciones lo tienen; no cuenta los beneficios).

**`Integraciones`** (`admin.api.IntegracionDtos`), respuesta de `GET /api/admin/integraciones`:

```json
{
  "modelos": [
    { "id": 4, "nombre": "Gemini Omni Flash", "proveedor": "GOOGLE", "tipo": "VIDEO", "adaptador": "GEMINI_OMNI",
      "identificadorModelo": "gemini-omni-1.2-flash",
      "parametros": { "relacionAspecto": "9:16", "resolucion": "720p", "duracionClipSegundos": 4,
                      "tokensPorSegundo": 5792, "segundoPlano": false },
      "costoReferencial": 0.070000, "unidadCosto": "POR_SEGUNDO", "nombreSecreto": "autoreel/gemini",
      "estado": "ACTIVO", "ultimaPrueba": "2026-11-06T15:00:00Z", "ultimaPruebaExitosa": true,
      "ultimaPruebaMensaje": "La conexión funciona.", "credencialConfigurada": true, "adaptadorDisponible": true,
      "planes": ["Lite"] }
  ],
  "credenciales": [
    { "nombre": "autoreel/gemini", "origen": "PANEL", "descripcion": "Termina en …a1b2",
      "modelos": ["Gemini", "Gemini Omni Flash"] },
    { "nombre": "autoreel/google-cloud", "origen": "NINGUNO", "descripcion": null,
      "modelos": ["Veo 3.1 Lite", "Gemini (Google Cloud)"] }
  ],
  "adaptadores": { "TEXTO": ["GEMINI", "GEMINI_CLOUD"], "VIDEO": ["GEMINI_OMNI", "VEO"] }
}
```

(Ejemplo recortado: `modelos` trae los cuatro, ordenados por tipo e id.)

- `ModeloVista`: los campos de `modelo_ia` más `credencialConfigurada` (su secreto tiene valor; **nunca el valor**, RN-55), `adaptadorDisponible` (el adaptador existe en el código, RN-56) y `planes` (los planes que lo usan como modelo de texto o de video).
- `CredencialVista`: una por cada `nombreSecreto` en uso. `origen`: `PANEL` (pegada en Ajustes), `CONFIGURACION` (del `.env`) o `NINGUNO`. `descripcion`: una pista para reconocerla sin mostrarla, "Cuenta de servicio {client_email}" para un JSON de Google Cloud o "Termina en …" con los últimos 4 caracteres de una clave; `null` si no hay valor. `modelos`: los que la usan.
- `adaptadores`: los adaptadores que existen en el código por tipo, para elegir al crear un modelo.

**`SolicitudModelo`**, cuerpo de `POST /api/admin/modelos-ia` y `PUT /api/admin/modelos-ia/{id}`:

| Campo | Tipo | Validación |
|---|---|---|
| `nombre` | string | Obligatorio, hasta 80 caracteres |
| `proveedor` | string | Obligatorio, hasta 30 caracteres; se guarda en mayúsculas |
| `tipo` | string | `TEXTO` o `VIDEO`. Obligatorio al crear (`TIPO_REQUERIDO`); se ignora al editar |
| `adaptador` | string | Obligatorio; uno de `adaptadores` para ese tipo (`ADAPTADOR_INEXISTENTE`) |
| `identificadorModelo` | string | Obligatorio, hasta 120 caracteres. El par `adaptador` + `identificadorModelo` es único (`MODELO_YA_EXISTE`) |
| `parametros` | objeto | Opcional; sin él, `{}` |
| `costoReferencial` | decimal | Obligatorio, mayor o igual que 0, hasta 6 enteros y 6 decimales |
| `nombreSecreto` | string | Obligatorio, hasta 120 caracteres; solo minúsculas, números, `/`, `_`, `.` y `-` (por ejemplo `autoreel/gemini`). Es el nombre, nunca la credencial |

**`SolicitudCredencial`**, cuerpo de `PUT /api/admin/credenciales`: `nombre` (obligatorio; un `nombreSecreto` que use algún modelo) y `valor` (obligatorio, hasta 20 000 caracteres: la clave de API o el JSON completo de la cuenta de servicio). La respuesta es la `CredencialVista` actualizada, con `origen: "PANEL"`.

**`ResultadoPrueba`**: `{ "exitosa": false, "mensaje": "No hay credencial configurada para autoreel/gemini.", "fecha": "2026-11-06T15:00:00Z" }`.

**`CostosDelMes`** (`costos.aplicacion.ServicioCostos.CostosDelMes`), respuesta de `GET /api/admin/costos`:

```json
{ "mes": "2026-11",
  "total": { "guion": 0.020000, "clips": 1.680000, "total": 1.700000, "videos": 2 },
  "negocios": [
    { "negocioId": 1, "nombre": "Pastelería Dulce Encanto",
      "costo": { "guion": 0.020000, "clips": 1.680000, "total": 1.700000, "videos": 2 } }
  ] }
```

Los montos son en dólares (`registro_costo.costo_usd`). `videos` cuenta los videos con algún costo en el período. Un negocio sin costos en el mes no aparece.

---

## 5. Errores

Todas las respuestas de error tienen la forma `ErrorApi`:

```json
{ "codigo": "FOTO_RESOLUCION_INSUFICIENTE",
  "mensaje": "Tu foto mide 800 × 600 px. Necesitamos al menos 720 px en el lado más corto. Tómala con la cámara del celular, sin recortarla." }
```

`codigo` es estable y sirve para decidir en el frontend; `mensaje` está en español y listo para mostrar. Spring Boot no agrega detalles internos (`server.error.include-*` en `never`).

### 5.1 Códigos de las fases 1 y 2

**Seguridad** (`compartido/seguridad/RespuestasDeSeguridad`)

| Código | HTTP | Cuándo |
|---|---|---|
| `NO_AUTENTICADO` | 401 | No hay cookie de sesión, el token es inválido o venció, cambió la versión de sesión, el usuario está inactivo o el negocio fue suspendido |
| `ACCESO_DENEGADO` | 403 | La sesión no tiene el rol requerido (por ejemplo, un `NEGOCIO` en `/api/admin/**`, un `ADMIN` en `/api/perfil` o `/api/productos`, o quien tiene contraseña temporal fuera de `/api/auth/yo` y `cambiar-contrasena`). Desde la fase 7, también el administrador que entró como un negocio en `/api/admin/**`, y la sesión propia de un negocio en `POST /api/auth/volver-al-panel` |
| `CSRF_INVALIDO` | 403 | Falta la cabecera `X-XSRF-TOKEN` o no coincide con la cookie `XSRF-TOKEN` |

**Generales** (`compartido/error/ManejadorGlobalDeErrores` y `ExcepcionDeNegocio.noEncontrado`)

| Código | HTTP | Cuándo |
|---|---|---|
| `DATOS_INVALIDOS` | 400 | Falla una validación de Bean Validation (el mensaje es el del primer campo con error); también el nombre comercial vacío o de más de 120 caracteres en `PUT /api/perfil` |
| `SOLICITUD_INVALIDA` | 400 (u otro 4xx) | Cuerpo JSON ilegible, parámetro faltante o con tipo o valor incorrecto (por ejemplo, `?estado=` o `proposito` desconocidos, o un tipo de foto inexistente). En `PUT /api/perfil`: un campo desconocido, un campo que no es texto o `respuestas` que no es un objeto |
| `FORMATO_NO_ADMITIDO` | 415 / 406 | `Content-Type` o `Accept` que la ruta no admite |
| `NO_ENCONTRADO` | 404 | Ruta inexistente dentro de `/api/**` para una sesión con el rol de ese prefijo; negocio, cuenta, perfil, producto, conversación, video o plano que no existe; **producto, conversación o video de otro negocio** (V08). Las rutas fuera de `/api/**` y `/actuator/health` se deniegan antes (`denyAll`): 401 sin sesión, 403 con sesión |
| `METODO_NO_PERMITIDO` | 405 | Método HTTP no soportado por la ruta |
| `CONFLICTO` | 409 | Choque con una restricción única de la base de datos (último resguardo ante operaciones simultáneas) |
| `ERROR_INTERNO` | 500 | Error inesperado; se registra en el log |

**Autenticación** (`auth/aplicacion/ServicioAutenticacion` y `auth/dominio/ReglasDeContrasena`)

| Código | HTTP | Cuándo |
|---|---|---|
| `CREDENCIALES_INVALIDAS` | 401 | Correo inexistente, contraseña incorrecta o usuario inactivo (no se distingue cuál) |
| `CUENTA_BLOQUEADA` | 423 | 5 intentos fallidos; bloqueo de 15 minutos (RN-08). Un correo inexistente también se bloquea así, para no revelar qué correos están registrados |
| `NEGOCIO_SUSPENDIDO` | 403 | Contraseña correcta, pero el negocio está suspendido (RN-07) |
| `CONTRASENA_TEMPORAL_VENCIDA` | 401 | Pasaron 48 h desde que se emitió la contraseña temporal (RN-02), al iniciar sesión o al cambiarla |
| `CONTRASENA_ACTUAL_INCORRECTA` | 400 | Al cambiar la contraseña propia, la actual no coincide |
| `CONTRASENA_REPETIDA` | 400 | La nueva es igual a la actual o a la temporal |
| `CONTRASENA_MUY_CORTA` | 400 | Menos de 8 caracteres (RN-04) |
| `CONTRASENA_MUY_LARGA` | 400 | Más de 64 caracteres (límite de BCrypt) |
| `CONTRASENA_SIN_LETRA` | 400 | No tiene ninguna letra |
| `CONTRASENA_SIN_NUMERO` | 400 | No tiene ningún número |
| `CONTRASENAS_NO_COINCIDEN` | 400 | La confirmación no coincide |
| `ACCION_NO_PERMITIDA_COMO_ADMINISTRADOR` | 403 | **Fase 7.** `POST /api/auth/cambiar-contrasena` con la sesión de un administrador que entró como el negocio (RN-58): "Mientras ves el negocio como administrador no puedes cambiar su contraseña. Usa "Reiniciar contraseña" en el panel." (`AuthController`) |

**Administración** (`admin/aplicacion/ServicioGestionNegocios`)

| Código | HTTP | Cuándo |
|---|---|---|
| `NICHO_INVALIDO` | 400 | El nicho no existe en `RegistroDeNichos` |
| `PLAN_INVALIDO` | 400 | El plan no existe o no está activo |
| `CORREO_YA_REGISTRADO` | 409 | Ya existe una cuenta con ese correo (RN-01) |

### 5.2 Archivos (fase 2)

Los responden `POST /api/archivos/url-subida` (al firmar) y `PUT /api/perfil`, `POST /api/productos`, `PUT /api/productos/{id}` y, desde las fases 3 y 4, `POST /api/chat/conversaciones/{id}/mensajes` (`productoNuevo`) y `PUT /api/videos/{id}/planos/{orden}` (`foto.clave`) (al usar una clave de subida). Origen: `almacenamiento/aplicacion/ServicioDeArchivos`, `VerificadorDeImagenes`, y `ServicioPerfil` / `ServicioCatalogo` para la clave que no es una subida.

| Código | HTTP | Cuándo | Dónde |
|---|---|---|---|
| `LOGO_DEBE_SER_PNG` | 400 | El logo no es `image/png`: el tipo declarado al firmar, o el contenido real al verificar (RN-10) | Firma y verificación |
| `TIPO_NO_PERMITIDO` | 400 | Una foto con un tipo distinto de `image/jpeg`, `image/png` o `image/webp` (RN-11) | Firma |
| `ARCHIVO_VACIO` | 400 | `tamano` es 0 o negativo | Firma |
| `ARCHIVO_DEMASIADO_GRANDE` | 400 | Más de 5 MB (logo) o 10 MB (fotos); el mensaje dice el límite en MB. Al verificar, se detecta sin descargar el archivo | Firma y verificación |
| `ARCHIVO_INVALIDO` | 400 | La clave no es de este negocio y propósito, no es una subida (`subidas/…`) ni la clave ya guardada, o tiene otra forma (por ejemplo, con `..`); o el contenido no es una imagen legible, o su formato real no coincide con la extensión o no está admitido | Verificación |
| `ARCHIVO_NO_SUBIDO` | 400 | La clave de subida no existe en el almacenamiento (el `PUT` no se hizo o falló) | Verificación |
| `IMAGEN_ANIMADA` | 400 | WebP animado o PNG animado (APNG) | Verificación |
| `IMAGEN_DEMASIADO_GRANDE` | 400 | Lado mayor de más de 8000 px o más de 40 megapíxeles; en el logo, lado mayor de más de 4000 px. El mensaje trae las medidas | Verificación |
| `LOGO_MUY_PEQUENO` | 400 | El lado menor del logo mide menos de 200 px | Verificación |
| `FOTO_RESOLUCION_INSUFICIENTE` | 400 | El lado menor de una foto de producto o del local mide menos de **720 px** (RN-11, V12; 1080 px hasta la revisión del 2026-11-06). El mensaje trae las medidas reales | Verificación |
| `ARCHIVO_MODIFICADO` | 409 | El archivo cambió (o desapareció) entre la lectura y la copia a su clave definitiva | Verificación |

Las fotos horizontales y el logo opaco **no son errores**: se aceptan con los avisos `FOTO_HORIZONTAL`, `FOTO_LOCAL_HORIZONTAL` y `LOGO_SIN_TRANSPARENCIA`.

### 5.3 Perfil y nicho (fase 2)

Origen: `negocio/aplicacion/ServicioPerfil`, `nicho/dominio/ValidadorDeRespuestas` y `nicho/dominio/RegistroDeNichos`.

| Código | HTTP | Cuándo |
|---|---|---|
| `PERFIL_INCOMPLETO` | 400 | Al completar el onboarding, o al editar un perfil con el onboarding ya completo, falta el logo, algún color, el estilo o una respuesta obligatoria (incluida `whatsapp` si se eligió WhatsApp). El mensaje dice qué falta |
| `COLOR_INVALIDO` | 400 | Un color no tiene el formato `#RRGGBB` |
| `ESTILO_INVALIDO` | 400 | El estilo no está entre los del paquete del nicho |
| `RESPUESTA_INVALIDA` | 400 | Pregunta desconocida, texto donde se espera lista (o al revés), opción inexistente, texto más largo que `maxLongitud` o celular inválido ("Escribe un celular de 9 dígitos que empiece con 9.") |
| `NICHO_DESCONOCIDO` | 500 | El nicho del negocio no tiene un paquete registrado (no debería ocurrir: el nicho se valida al crear el negocio) |

### 5.4 Catálogo (fase 2)

Origen: `catalogo/aplicacion/ServicioCatalogo`.

| Código | HTTP | Cuándo |
|---|---|---|
| `PRODUCTO_NOMBRE_INVALIDO` | 400 | Nombre vacío o de más de 50 caracteres |
| `PRODUCTO_DESCRIPCION_INVALIDA` | 400 | Descripción vacía o de más de 200 caracteres |
| `TEMPORADA_INVALIDA` | 400 | Una temporada no existe en el paquete del nicho |
| `NO_ENCONTRADO` | 404 | El producto no existe, es de otro negocio o, desde la fase 3, se subió desde el chat sin guardarlo en el catálogo |

`PRODUCTO_SIN_TEMPORADA` ya no se responde: desde la revisión del 2026-11-06, un producto sin temporadas queda en `TODO_EL_ANIO`.

### 5.5 Chat (fase 3)

Origen: `chat/aplicacion/ServicioChat` y, para el producto nuevo, `catalogo/aplicacion/ServicioCatalogo.crearDesdeChat`. Todos tienen la forma `ErrorApi` con el mensaje en español.

| Código | HTTP | Cuándo | Ruta |
|---|---|---|---|
| `ONBOARDING_INCOMPLETO` | 409 | El negocio no completó su onboarding: no puede crear videos (RN-09, V10). "Completa el perfil de tu negocio para crear tu primer video." | `POST /conversaciones` |
| `CONVERSACION_CERRADA` | 409 | La conversación ya no está `EN_CURSO` (completa o abandonada), o se completó al cerrar la respuesta agrupada anterior justo antes de este texto. El frontend recarga sin mostrar error | `POST .../mensajes`, `POST .../procesar` |
| `PASO_CAMBIADO` | 409 | `paso` no coincide con el paso actual (o falta): la pantalla estaba desactualizada. No se guarda nada; el frontend recarga sin mostrar error | `POST .../mensajes` |
| `PASO_NO_ADMITE_RESPUESTA` | 409 | La forma de respuesta no corresponde al tipo del paso (por ejemplo, `texto` en el paso de producto o en un paso de botones sin `admiteTexto`, u `opcion` en el de producto), o `omitir` en un paso que no es omitible | `POST .../mensajes` |
| `SOLICITUD_INVALIDA` | 400 | Ninguna o más de una forma de respuesta ("Envía una sola respuesta a la vez."), un `texto` vacío o solo con espacios, un cuerpo ilegible o un tipo de foto desconocido en `productoNuevo.fotos` | `POST .../mensajes` |
| `RESPUESTA_MUY_LARGA` | 400 | Un solo mensaje de texto de más de 500 caracteres. (Si el texto **agrupado** pasa de 500, no es un error: el chat pide resumirlo) | `POST .../mensajes` |
| `OPCION_INVALIDA` | 400 | La `opcion` no está entre las del paso | `POST .../mensajes` |
| `PRODUCTO_NO_DISPONIBLE` | 400 | `productoId` inexistente, de otro negocio, archivado, sin sus tres fotos o fuera del catálogo (V11) | `POST .../mensajes` |
| `PRODUCTO_INCOMPLETO` | 400 | `productoNuevo` sin alguna de sus tres fotos (entero, detalle y porción) | `POST .../mensajes` |
| `PRODUCTO_NOMBRE_INVALIDO`, `PRODUCTO_DESCRIPCION_INVALIDA` | 400 | Igual que en el catálogo (sección 5.4) | `POST .../mensajes` (`productoNuevo`) |
| Errores de archivos (sección 5.2) | 400 / 409 | Al verificar y copiar las fotos de `productoNuevo` | `POST .../mensajes` (`productoNuevo`) |
| `NO_ENCONTRADO` | 404 | La conversación no existe o es de otro negocio | `POST .../mensajes`, `POST .../procesar` |
| `CONFLICTO` | 409 | Último resguardo: dos conversaciones `EN_CURSO` del mismo negocio chocan con el índice único de RN-19 (no debería ocurrir, porque `POST /conversaciones` bloquea la fila del negocio) | `POST /conversaciones` |

Las respuestas **rechazadas por el guion** (pregunta del usuario, respuesta muy corta o muy larga, precio obligatorio en una Promoción, producto que dejó de estar disponible al terminar) **no son errores HTTP**: responden `200` y la conversación trae el mensaje fijo y repite la pregunta (sección 4.5).

### 5.6 Videos (fase 4)

Origen: `video/aplicacion/ServicioVideos` y, para la foto subida, `almacenamiento/aplicacion/ServicioDeArchivos`. Todos tienen la forma `ErrorApi` con el mensaje en español.

| Código | HTTP | Cuándo | Ruta |
|---|---|---|---|
| `NO_ENCONTRADO` | 404 | El video no existe o es de otro negocio ("No encontramos ese video."), o el `orden` no es un plano del video ("Ese plano no existe."; en `regenerar`, también el plano 4) | Todas las de `/api/videos/{id}` |
| `VIDEO_NO_EDITABLE` | 409 | El video no está en el estado que la acción necesita ("Este video ya no se puede cambiar."): `PUT` y `descartar`, fuera de `STORYBOARD` y `LISTO` (guion pendiente, generándose, aprobado, descartado o en `ERROR`); `aprobar-storyboard`, fuera de `STORYBOARD`; `regenerar` y `aprobar`, fuera de `LISTO` | `PUT .../planos/{orden}`, `POST .../descartar`, `POST .../aprobar-storyboard`, `POST .../regenerar`, `POST .../aprobar` |
| `SOLICITUD_INVALIDA` | 400 | Planos 1 a 3: sin `texto` ni `foto`, o con `precio` o `frase`. Plano 4: sin `precio` ni `frase`, o con `texto` o `foto`. `foto` con `tipo` y `clave` juntos, o sin ninguno ("Elige una foto del producto o sube una nueva."). Un `tipo` de foto desconocido o un cuerpo ilegible | `PUT .../planos/{orden}` |
| `TEXTO_VACIO` | 400 | El texto queda vacío al recortar los espacios ("Escribe el texto de este plano.") | `PUT .../planos/{orden}` |
| `TEXTO_MUY_LARGO` | 400 | Más palabras que el máximo del plano (V21): "Este plano admite hasta 6 palabras, para que se lea en el video."; o más de 200 caracteres: "Este plano admite hasta 200 caracteres." | `PUT .../planos/{orden}` |
| `CARACTER_NO_PERMITIDO` | 400 | El texto o el precio tiene algo que las tipografías del video no pueden dibujar ("Quita los emojis, los saltos de línea y los símbolos especiales: no se pueden mostrar en el video."). Desde la fase 5 es una **lista blanca** (`Texto.tieneCaracteresNoDibujables`): solo se admite el latín de U+0020 a U+007E y de U+00A0 a U+00FF (incluye á, ñ, ¿, ¡) y ‘ ’ “ ” – — … € | `PUT .../planos/{orden}` |
| `PALABRA_PROHIBIDA` | 400 | El texto o el precio contiene una frase prohibida por el paquete de nicho (`vocabulario.json`) o por el negocio (`palabrasProhibidas`), sin distinguir mayúsculas ni tildes: `No podemos usar "…" en tus videos. Escríbelo de otra forma.` | `PUT .../planos/{orden}` |
| `PRECIO_MUY_LARGO` | 400 | El precio del cierre pasa de 40 caracteres ("El precio admite hasta 40 caracteres.") | `PUT .../planos/4` |
| `FRASE_MUY_LARGA` | 400 | Desde el 2026-11-12. La frase del cierre pasa de 6 palabras o de 40 caracteres ("La frase admite hasta 6 palabras y 40 caracteres, para que se lea en el video.") | `PUT .../planos/4` |
| `VIDEO_NO_REINTENTABLE` | 409 | PR #12. El video está en `ERROR` pero no se puede reintentar: por ejemplo, un error definitivo (el producto ya no existe o no tiene fotos) ("Este video no se puede reintentar. Crea otro desde el inicio.") | `POST .../reintentar` |
| `FOTO_NO_DISPONIBLE` | 400 | `foto.tipo` pide una foto que el producto ya no tiene ("El producto ya no tiene esa foto. Elige otra o sube una nueva.") | `PUT .../planos/{orden}` |
| Errores de archivos (sección 5.2) | 400 / 409 | Al verificar y copiar una foto subida (`foto.clave`) | `PUT .../planos/{orden}` |

Un guion que no pasa el validador, un proveedor que no responde o la falta de la clave **no son errores HTTP**. Hasta el 2026-11-12 el video pasaba a `STORYBOARD` con el guion de respaldo y `avisoDeRespaldo`. Desde entonces, el guion se reintenta solo durante 5 minutos y luego el video pasa a `ERROR` con `generacion.motivoError` y `puedeReintentar: true` (sección 3.5).

### 5.7 Generación y revisión (fase 5)

Origen: `video/aplicacion/ServicioVideos` y `ServicioConsumo`, y `video/dominio/Video.regenerarPlano`. Todos responden 409 con la forma `ErrorApi`.

| Código | HTTP | Cuándo | Ruta |
|---|---|---|---|
| `VIDEOS_DEL_DIA_AGOTADOS` | 409 | El negocio ya usó los videos del día de su plan (RN-26, V22): "Ya usaste tu video de hoy. Mañana tienes uno nuevo." (con `videosPorDia` mayor que 1: "Ya usaste tus N videos de hoy. Mañana tienes otros nuevos."). El storyboard queda igual y se puede aprobar al día siguiente | `POST .../aprobar-storyboard`; desde el PR #12, `POST .../reintentar` de un video que falló al generarse |
| `SIN_REGENERACIONES` | 409 | El video ya usó las regeneraciones de su plan (RN-28, V26): "Ya usaste las 3 regeneraciones de este video. Todavía puedes editar los textos." | `POST .../planos/{orden}/regenerar`, `PUT .../planos/{orden}` con `foto` en `LISTO` |
| `IA_NO_DISPONIBLE` | 409 | El modelo de video del video no se puede usar ahora (sin credencial, inactivo o sin adaptador): "Por ahora no podemos crear clips con IA. Inténtalo en un rato; mientras tanto puedes editar los textos." No se guarda ni se consume nada. El frontend ya desactiva esas acciones con `iaDisponible: false` | `POST .../planos/{orden}/regenerar`; desde la revisión del 2026-11-06, también `POST .../aprobar-storyboard` (antes de consumir el video del día) y `PUT .../planos/{orden}` con `foto` en `LISTO`; desde el PR #12, `POST .../reintentar` de un video que falló al generarse |
| `VIDEO_NO_DISPONIBLE` | 409 | El video todavía no tiene MP4 final o no está en `LISTO`, `APROBADO`, `PROGRAMADO` ni `PUBLICADO`: "Tu video todavía no está listo para descargar." | `GET .../descarga` |

Un clip que no se genera o un proveedor que falla durante la generación **no son errores HTTP**: desde la revisión del 2026-11-06 ya no hay acercamiento de FFmpeg como respaldo. En la primera generación el video pasa a `ERROR` con `generacion.motivoError` y se devuelve el video del día; en una regeneración el plano conserva su clip anterior y la vista trae `generacion.avisoRevision` (sección 3.5). Una composición que falla tampoco lo es: el video pasa a `ERROR` con `generacion.motivoError` (primera composición) o vuelve a `LISTO` con `generacion.avisoRevision` (recomposición).

### 5.8 Administración de la fase 7

Origen: `facturacion/aplicacion/ServicioSuscripciones`, `ServicioPlanes` y `ServicioNotificaciones`, `admin/aplicacion/ServicioIntegraciones` y `costos/api/CostoController`. Todos tienen la forma `ErrorApi` con el mensaje en español. Las validaciones de los cuerpos (campos obligatorios, longitudes, montos) responden `400 DATOS_INVALIDOS` con el mensaje del primer campo con error.

| Código | HTTP | Cuándo | Ruta |
|---|---|---|---|
| `NO_ENCONTRADO` | 404 | El negocio, el beneficio (o es de otro negocio), la notificación, el plan o el modelo no existen; o ningún modelo usa ese nombre de credencial ("Ningún modelo usa la credencial …") | Todas las de las secciones 3.6 y 3.7 con `{id}`, y las de `/credenciales` |
| `PAGO_FECHA_FUTURA` | 400 | "La fecha del pago no puede ser posterior a hoy." (día de Lima) | `POST .../pagos` |
| `PLAN_INVALIDO` | 400 | El plan no existe o no está activo | `PUT .../plan`, `POST .../beneficios` |
| `BENEFICIO_FECHAS_INVALIDAS` | 400 | "El fin del beneficio debe ser después del inicio." o "El beneficio ya habría terminado." | `POST .../beneficios` |
| `BENEFICIO_MUY_LARGO` | 400 | "Un beneficio puede durar como máximo 30 días." (RN-51) | `POST .../beneficios` |
| `BENEFICIO_MISMO_PLAN` | 400 | El plan del beneficio es el que el negocio ya tiene en su suscripción | `POST .../beneficios` |
| `BENEFICIO_CRUZADO` | 409 | "El negocio ya tiene un beneficio en esas fechas. Termina el anterior o elige otras fechas." (RN-51: un solo beneficio a la vez) | `POST .../beneficios` |
| `MODELO_INVALIDO` | 400 | El modelo elegido no existe o no es del tipo del campo ("El modelo de video debe ser un modelo de video.") | `POST`/`PUT /api/admin/planes` |
| `MODELO_INACTIVO` | 400 | El modelo elegido está `INACTIVO` (RN-47): "El modelo … está inactivo. Actívalo en Integraciones antes de asignarlo." | `POST`/`PUT /api/admin/planes` |
| `PLAN_YA_EXISTE` | 409 | Ya existe otro plan con ese nombre, sin distinguir mayúsculas | `POST`/`PUT /api/admin/planes` |
| `TIPO_REQUERIDO` | 400 | Al crear un modelo, falta `tipo` | `POST /api/admin/modelos-ia` |
| `ADAPTADOR_INEXISTENTE` | 400 | El adaptador no existe en el código para ese tipo (RN-56): "… Un proveedor nuevo requiere programar su adaptador." | `POST`/`PUT /api/admin/modelos-ia`, `POST .../activar` |
| `MODELO_YA_EXISTE` | 409 | Ya existe otro modelo con el mismo adaptador e identificador | `POST`/`PUT /api/admin/modelos-ia` |
| `SIN_CREDENCIAL` | 409 | "Configura la credencial … antes de activar el modelo." | `POST .../activar` |
| `MODELO_EN_USO` | 409 | El modelo lo usa un plan activo o un plan con negocios (RN-48): "El plan … usa este modelo. Asígnale otro modelo antes de desactivarlo." | `POST .../desactivar` |
| `CREDENCIAL_INVALIDA` | 400 | El valor empieza con `{` pero no es un JSON válido: "La credencial parece un JSON pero no es válido. Pega el archivo completo de la cuenta de servicio." | `PUT /api/admin/credenciales` |
| `MES_INVALIDO` | 400 | `?mes=` no tiene el formato `AAAA-MM` | `GET /api/admin/costos` |
| `CONFLICTO` | 409 | Último resguardo: un pago choca con `pago_periodo_uk` | `POST .../pagos` |

Una prueba de conexión que falla **no es un error HTTP**: `POST .../probar` responde `200` con `exitosa: false` y el motivo (sección 3.7).

### 5.9 Solo en el frontend

- Si una respuesta de error no trae un JSON legible, `cliente.ts` crea un `ErrorApi` con el código `ERROR` y un mensaje genérico.
- `api/archivos.ts` crea `ErrorApi(0, 'SUBIDA_FALLIDA', …)` cuando el `PUT` al almacenamiento falla dos veces (con una URL nueva en el reintento).
- `componentes/imagenes.ts` lanza `ErrorDeImagen` (sin código) al preparar la imagen en el navegador: HEIC, tipo no admitido, foto de menos de 720 px, logo que no es PNG, de más de 5 MB o de menos de 200 px, o foto normalizada de más de 10 MB.

Ninguno de estos es un código del backend.

---

## 6. Endpoints futuros

Según la sección 13 de la especificación y la hoja de ruta. Ninguno existe todavía.

| Endpoints | Fase |
|---|---|
| `GET /api/instagram/conectar`, `GET /api/instagram/callback`, `DELETE /api/instagram`, `POST /api/videos/{id}/publicar` | (futuro, fase 6) |

Los endpoints de videos de las fases 4 y 5 ya existen (sección 3.5), incluidos `aprobar-storyboard`, `regenerar`, `aprobar` y `descarga`, y los de administración de la fase 7 también (secciones 3.2, 3.6 y 3.7), con los costos de IA por negocio y por mes. La edición de la descripción y los hashtags antes de publicar llega con la publicación (fase 6).
