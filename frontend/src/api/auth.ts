import { ErrorApi, pedir } from './cliente'

export type Rol = 'ADMIN' | 'NEGOCIO'

export interface Sesion {
  id: number
  correo: string
  rol: Rol
  negocioId: number | null
  nombreNegocio: string | null
  debeCambiarContrasena: boolean
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
