import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { cerrarSesion, obtenerSesion, type Sesion } from '../api/auth'

export const CLAVE_SESION = ['sesion']

export function useSesion() {
  return useQuery({ queryKey: CLAVE_SESION, queryFn: obtenerSesion, staleTime: 60_000 })
}

/** Ruta de inicio de cada usuario según su rol y si todavía debe crear su contraseña (RN-03, RN-06). */
export function rutaInicio(sesion: Sesion): string {
  if (sesion.debeCambiarContrasena) return '/crear-contrasena'
  return sesion.rol === 'ADMIN' ? '/admin' : '/'
}

export function useCerrarSesion() {
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  return useMutation({
    mutationFn: cerrarSesion,
    onSettled: () => {
      clienteConsultas.clear()
      clienteConsultas.setQueryData(CLAVE_SESION, null)
      navegar('/login', { replace: true })
    },
  })
}
