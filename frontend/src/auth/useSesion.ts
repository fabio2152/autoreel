import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { cerrarSesion, obtenerSesion, type Sesion } from '../api/auth'
import { ErrorApi } from '../api/cliente'

export const CLAVE_SESION = ['sesion']

/** Respuestas 401 del inicio de sesión que no significan "tu sesión terminó". */
const ERRORES_DE_CREDENCIALES = ['CREDENCIALES_INVALIDAS', 'CONTRASENA_TEMPORAL_VENCIDA']

export function useSesion() {
  return useQuery({ queryKey: CLAVE_SESION, queryFn: obtenerSesion, staleTime: 60_000 })
}

/** Ruta de inicio de cada usuario según su rol y si todavía debe crear su contraseña (RN-03, RN-06). */
export function rutaInicio(sesion: Sesion): string {
  if (sesion.debeCambiarContrasena) return '/crear-contrasena'
  return sesion.rol === 'ADMIN' ? '/admin' : '/'
}

/**
 * Reacción global a errores de la API. Un 401 significa que la sesión terminó en el servidor (venció, se reinició
 * la contraseña o se suspendió el negocio): se borra la sesión local y RutaProtegida lleva al inicio de sesión.
 * Un 403 puede significar que cambió el rol o el estado: se vuelve a consultar la sesión.
 */
export function reaccionarAErrorDeApi(clienteConsultas: QueryClient, error: unknown) {
  if (!(error instanceof ErrorApi)) return
  if (error.estado === 401 && !ERRORES_DE_CREDENCIALES.includes(error.codigo)) {
    clienteConsultas.removeQueries({ predicate: (consulta) => consulta.queryKey[0] !== CLAVE_SESION[0] })
    clienteConsultas.setQueryData(CLAVE_SESION, null)
  } else if (error.estado === 403 && error.codigo === 'ACCESO_DENEGADO') {
    clienteConsultas.invalidateQueries({ queryKey: CLAVE_SESION })
  }
}

export function useCerrarSesion() {
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  return useMutation({
    mutationFn: cerrarSesion,
    onSuccess: () => {
      clienteConsultas.clear()
      clienteConsultas.setQueryData(CLAVE_SESION, null)
      navegar('/login', { replace: true })
    },
    // Si el servidor no confirmó el cierre, la cookie puede seguir vigente: se vuelve a consultar la sesión real.
    onError: () => clienteConsultas.invalidateQueries({ queryKey: CLAVE_SESION }),
  })
}
