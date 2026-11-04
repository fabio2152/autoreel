/**
 * Cliente HTTP de la API. La sesión viaja en una cookie HttpOnly; las peticiones que modifican datos envían
 * el token CSRF que el backend deja en la cookie XSRF-TOKEN (ver ADR 0007).
 */

export class ErrorApi extends Error {
  readonly codigo: string
  readonly estado: number

  constructor(estado: number, codigo: string, mensaje: string) {
    super(mensaje)
    this.estado = estado
    this.codigo = codigo
  }
}

const METODOS_SEGUROS = ['GET', 'HEAD', 'OPTIONS']

function leerTokenCsrf(): string | null {
  const cookie = document.cookie.split('; ').find((parte) => parte.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)) : null
}

/** Cualquier respuesta del backend renueva la cookie XSRF-TOKEN; esta consulta es la más liviana. */
async function renovarTokenCsrf(): Promise<void> {
  await fetch('/api/auth/yo', { credentials: 'include' })
}

async function enviar(ruta: string, metodo: string, cuerpo: unknown): Promise<Response> {
  const cabeceras: Record<string, string> = { Accept: 'application/json' }
  if (cuerpo !== undefined) {
    cabeceras['Content-Type'] = 'application/json'
  }
  if (!METODOS_SEGUROS.includes(metodo)) {
    if (!leerTokenCsrf()) {
      await renovarTokenCsrf()
    }
    const token = leerTokenCsrf()
    if (token) {
      cabeceras['X-XSRF-TOKEN'] = token
    }
  }
  return fetch(ruta, {
    method: metodo,
    credentials: 'include',
    headers: cabeceras,
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  })
}

async function aError(respuesta: Response): Promise<ErrorApi> {
  try {
    const cuerpo = (await respuesta.json()) as { codigo?: string; mensaje?: string }
    return new ErrorApi(
      respuesta.status,
      cuerpo.codigo ?? 'ERROR',
      cuerpo.mensaje ?? 'Ocurrió un error inesperado. Inténtalo de nuevo.',
    )
  } catch {
    return new ErrorApi(respuesta.status, 'ERROR', 'No pudimos comunicarnos con el servidor. Inténtalo de nuevo.')
  }
}

export async function pedir<T>(ruta: string, metodo = 'GET', cuerpo?: unknown): Promise<T> {
  let respuesta = await enviar(ruta, metodo, cuerpo)
  if (!respuesta.ok) {
    let error = await aError(respuesta)
    if (error.codigo === 'CSRF_INVALIDO') {
      // El token pudo cambiar (por ejemplo, tras iniciar sesión): se renueva y se reintenta una vez.
      await renovarTokenCsrf()
      respuesta = await enviar(ruta, metodo, cuerpo)
      if (respuesta.ok) {
        return leerCuerpo<T>(respuesta)
      }
      error = await aError(respuesta)
    }
    throw error
  }
  return leerCuerpo<T>(respuesta)
}

async function leerCuerpo<T>(respuesta: Response): Promise<T> {
  if (respuesta.status === 204) {
    return undefined as T
  }
  return (await respuesta.json()) as T
}
