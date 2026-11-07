# ADR 0019: El administrador puede entrar como un negocio

- **Estado:** Aceptada
- **Fecha:** 2026-11-06

## Contexto

Al revisar el panel, el usuario pidió poder **ver AutoReel tal como lo ve un cliente** para darle soporte, y volver al panel cuando termine. No está en la especificación.

## Decisión

- **Botón "Entrar como el negocio"** en el detalle del negocio (`POST /api/admin/negocios/{id}/entrar`).
  - Emite una nueva cookie de sesión con el token del administrador: su id y su versión de sesión, más el claim `neg` con el negocio.
  - El administrador no conoce ni usa la contraseña del negocio.
- **Cómo se trata cada petición.** `FiltroJwtCookie` acepta el claim `neg` solo si el usuario es ADMIN y el negocio existe.
  - Da únicamente el rol NEGOCIO, así que la API del panel responde 403 mientras dure.
  - `UsuarioAutenticado.comoAdministrador()` lo distingue.
- **Acceso completo** (decisión del usuario): ve y hace todo lo que hace el negocio, incluso crear videos, que gastan y cuentan como consumo del negocio.
  - **Excepción:** no puede cambiar la contraseña del negocio (403 `ACCION_NO_PERMITIDA_COMO_ADMINISTRADOR`). Para eso está "Reiniciar contraseña".
- **Aviso fijo.** Una franja arriba dice "Estás viendo AutoReel como …", con el botón **"Volver al panel"** (`POST /api/auth/volver-al-panel`), que devuelve la sesión normal del administrador.
- **Negocio suspendido.** También se puede entrar como un negocio suspendido, para revisar su cuenta antes de reactivarlo.
- **Registro.** El log anota cada entrada: el id del administrador y el negocio. El último acceso del negocio no cambia.

## Consecuencias

- Si el administrador cambia su contraseña, también se cierran sus sesiones dentro de un negocio, porque comparten la versión de sesión.
- Lo que el administrador haga dentro del negocio no se distingue en los datos de lo que hace el negocio; solo queda en el log. Si hiciera falta auditoría, sería una tabla aparte.
