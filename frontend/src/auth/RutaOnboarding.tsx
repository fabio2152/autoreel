import { Navigate, Outlet } from 'react-router'
import { useSesion } from './useSesion'

/** RF-16: mientras el onboarding no esté completo, el negocio solo ve el asistente. */
export function RutaOnboarding() {
  const { data: sesion } = useSesion()
  if (sesion && !sesion.onboardingCompleto) return <Navigate to="/bienvenida" replace />
  return <Outlet />
}
