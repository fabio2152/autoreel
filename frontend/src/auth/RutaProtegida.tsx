import { Navigate, Outlet } from 'react-router'
import type { Rol } from '../api/auth'
import { Cargando } from '../componentes/Cargando'
import { ErrorDeSesion } from './ErrorDeSesion'
import { rutaInicio, useSesion } from './useSesion'

/**
 * Solo deja pasar a usuarios del rol indicado y con su contraseña propia ya creada. El backend vuelve a
 * verificar los permisos en cada petición; esto solo evita mostrar pantallas a quien no corresponde.
 */
export function RutaProtegida({ rol }: { rol: Rol }) {
  const { data: sesion, isPending, error, refetch } = useSesion()

  if (isPending) return <Cargando />
  // Un fallo de red o del servidor no es "sin sesión": se ofrece reintentar en lugar de mandar al login.
  if (error) return <ErrorDeSesion mensaje={error.message} onReintentar={() => refetch()} />
  if (!sesion) return <Navigate to="/login" replace />
  if (sesion.debeCambiarContrasena || sesion.rol !== rol) return <Navigate to={rutaInicio(sesion)} replace />
  return <Outlet />
}
