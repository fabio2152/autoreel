import { ErrorApi, pedir } from './cliente'

export type Rol = 'ADMIN' | 'NEGOCIO'

export interface Sesion {
  id: number
  correo: string
  rol: Rol
  negocioId: number | null
  nombreNegocio: string | null
  debeCambiarContrasena: boolean
  /** RF-16: el negocio pasa por el onboarding antes de usar el resto. Siempre true para el administrador. */
  onboardingCompleto: boolean
  /** Correo del administrador que entró como este negocio; null en una sesión normal. */
  administrador: string | null
}

/** Devuelve la sesión actual, o null si no hay una activa. */
export async function obtenerSesion(): Promise<Sesion | null> {
  try {
    return await pedir<Sesion>('/api/auth/yo')
  } catch (error) {
    if (error instanceof ErrorApi && error.estado === 401) {
      return null
    }
    throw error
  }
}

export function iniciarSesion(correo: string, contrasena: string): Promise<Sesion> {
  return pedir<Sesion>('/api/auth/login', 'POST', { correo, contrasena })
}

export function cerrarSesion(): Promise<void> {
  return pedir<void>('/api/auth/logout', 'POST')
}

export function cambiarContrasena(datos: {
  contrasenaActual?: string
  nueva: string
  confirmacion: string
}): Promise<Sesion> {
  return pedir<Sesion>('/api/auth/cambiar-contrasena', 'POST', datos)
}

/** El administrador que entró como un negocio vuelve a su panel. */
export function volverAlPanel(): Promise<Sesion> {
  return pedir<Sesion>('/api/auth/volver-al-panel', 'POST')
}
