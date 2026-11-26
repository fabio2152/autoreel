# ADR 0018: Las claves de IA se pegan en Ajustes, pero viven fuera de la base de datos

- **Estado:** Aceptada; concretada en AWS por el ADR 0027 (`SecretosDeAws`)
- **Fecha:** 2026-11-06
- **Modifica:** RN-55 de la especificación y, en parte, el ADR 0005.

## Contexto

La especificación (regla 6 y RN-55) decía que las credenciales nunca se escriben ni se muestran en el panel. Solo se leían de variables de entorno en desarrollo y de AWS Secrets Manager en producción.

Al revisar el panel, el usuario pidió un lugar en **Ajustes** donde configurar sus claves sin tocar el `.env`.

## Decisión

El administrador **pega o reemplaza** cada clave en **Ajustes → Integraciones y claves**. Se mantiene todo lo demás de RN-55:

- **Nunca en la base de datos ni en el código.** Los modelos siguen guardando solo el nombre del secreto (`autoreel/gemini`, `autoreel/google-cloud`).
- **Dónde se guarda.** Detrás del puerto `AlmacenDeSecretos`:
  - **Desarrollo:** un JSON fuera del repositorio (`SECRETOS_PANEL_ARCHIVO`, por defecto `credenciales/secretos-del-panel.json` en la raíz), ignorado por git. Se escribe en un archivo temporal y después se mueve, para que nunca quede a medias.
  - **AWS (fase 8):** se escribirá con `PutSecretValue` en Secrets Manager.
    - *Hecho en la fase 8 (2026-11-25, ADR 0027):* `SecretosDeAws` (perfil `aws`) guarda con `PutSecretValue`, o con `CreateSecret` si el secreto no existe. **Quitar la clave desde el panel borra el secreto sin recuperación** (`DeleteSecret` con `ForceDeleteWithoutRecovery`): en AWS no hay `.env` al que volver, y el origen solo es "guardada en Ajustes" o "sin configurar". La política IAM del rol solo permite escribir `autoreel/gemini*` y `autoreel/google-cloud*`, así que el panel **no puede reemplazar `autoreel/app`** (base de datos, JWT y administrador inicial).
- **Nunca se vuelve a mostrar.**
  - El panel solo da una pista para reconocer la clave: "Termina en …abcd" o el correo de la cuenta de servicio.
  - También indica su origen: guardada en Ajustes, tomada del `.env` o sin configurar.
  - La respuesta del guardado lleva `Cache-Control: no-store`.
  - El log registra solo el nombre del secreto.
- **Prioridad.** Gana la clave del panel; si se quita, vuelve a regir la del `.env`.
- **Solo se aceptan secretos que use algún modelo.** No se pueden crear nombres sueltos.
- **"Probar conexión" no gasta.** Usa una llamada sin costo:
  - **AI Studio:** la ficha del modelo (`GET /v1beta/models/{id}`).
  - **Gemini en Google Cloud:** `countTokens`.
  - **Veo:** pedir un token con la cuenta de servicio.

## Consecuencias

- El administrador cambia de clave sin reiniciar el backend ni editar archivos.
- En el servidor de AWS, el disco no debe guardar claves: la fase 8 debe implementar `AlmacenDeSecretos` con Secrets Manager antes de desplegar. *Hecho en la fase 8:* `SecretosDeAws` (ADR 0027).
- Quien tenga acceso al servidor de desarrollo puede leer el JSON, igual que hoy puede leer el `.env`.
