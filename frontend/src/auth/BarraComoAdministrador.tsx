import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { volverAlPanel, type Sesion } from '../api/auth'
import { CLAVE_SESION } from './useSesion'

/**
 * Aviso fijo mientras el administrador ve AutoReel como un negocio (decisión del 2026-11-06): todo lo que haga cuenta
 * como del negocio, así que siempre está a la vista y permite volver al panel.
 */
export function BarraComoAdministrador({ sesion }: { sesion: Sesion }) {
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  const volver = useMutation({
    mutationFn: volverAlPanel,
    onSuccess: (nueva) => {
      clienteConsultas.clear()
      clienteConsultas.setQueryData(CLAVE_SESION, nueva)
      navegar(`/admin/negocios/${sesion.negocioId}`, { replace: true })
    },
  })

  return (
    // Franja fija arriba (ALTO_BARRA): abajo están el chat y la navegación del celular. RutaProtegida deja el espacio.
    <div role="status" className="fixed inset-x-0 top-0 z-50 h-10 bg-amber-300 text-amber-950 shadow">
      <div className="mx-auto flex h-full max-w-5xl items-center justify-between gap-3 px-4 text-sm">
        <p className="min-w-0 truncate" title={volver.error?.message}>
          <span className="hidden sm:inline">Estás viendo AutoReel como </span>
          <span className="sm:hidden">Como </span>
          <strong>{sesion.nombreNegocio}</strong>
          <span className="hidden md:inline"> · lo que hagas aquí cuenta como del negocio</span>
          {volver.error && <span className="font-semibold"> · {volver.error.message}</span>}
        </p>
        <button
          type="button"
          onClick={() => volver.mutate()}
          disabled={volver.isPending}
          className="shrink-0 rounded-md bg-amber-950 px-3 py-1 font-semibold text-amber-50 hover:bg-amber-900 disabled:opacity-60"
        >
          {volver.isPending ? 'Volviendo…' : 'Volver al panel'}
        </button>
      </div>
    </div>
  )
}
