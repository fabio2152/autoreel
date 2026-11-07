import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  marcarNotificacionLeida,
  marcarNotificacionesLeidas,
  obtenerNotificaciones,
  type Notificacion,
} from '../api/admin'
import { formatearFechaHora } from '../componentes/formato'

const CLAVE = ['notificaciones']

const ESTILO_TIPO: Record<Notificacion['tipo'], string> = {
  PAGO_POR_VENCER: 'bg-amber-400',
  PAGO_HOY: 'bg-orange-500',
  PAGO_VENCIDO: 'bg-red-600',
  SUGERENCIA_SUSPENSION: 'bg-red-800',
  INTEGRACION_CON_ERROR: 'bg-violet-600',
}

const ES_DE_PAGO: Notificacion['tipo'][] = ['PAGO_POR_VENCER', 'PAGO_HOY', 'PAGO_VENCIDO']

/** RF-09: campana con las notificaciones sin leer; las de pago llevan al botón "Registrar pago". */
export function CampanaNotificaciones() {
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  const [abierta, setAbierta] = useState(false)
  const contenedor = useRef<HTMLDivElement>(null)
  const bandeja = useQuery({ queryKey: CLAVE, queryFn: obtenerNotificaciones, refetchInterval: 60_000 })

  const actualizar = () => clienteConsultas.invalidateQueries({ queryKey: CLAVE })
  const leida = useMutation({ mutationFn: marcarNotificacionLeida, onSuccess: actualizar })
  const todas = useMutation({ mutationFn: marcarNotificacionesLeidas, onSuccess: actualizar })

  useEffect(() => {
    if (!abierta) return
    const cerrar = (evento: MouseEvent) => {
      if (contenedor.current && !contenedor.current.contains(evento.target as Node)) setAbierta(false)
    }
    const conTecla = (evento: KeyboardEvent) => evento.key === 'Escape' && setAbierta(false)
    document.addEventListener('mousedown', cerrar)
    document.addEventListener('keydown', conTecla)
    return () => {
      document.removeEventListener('mousedown', cerrar)
      document.removeEventListener('keydown', conTecla)
    }
  }, [abierta])

  const sinLeer = bandeja.data?.sinLeer ?? 0
  const irA = (notificacion: Notificacion, accion?: 'registrarPago') => {
    if (!notificacion.leida) leida.mutate(notificacion.id)
    setAbierta(false)
    if (notificacion.tipo === 'INTEGRACION_CON_ERROR') navegar('/admin/ajustes?seccion=integraciones')
    else if (notificacion.negocioId)
      navegar(`/admin/negocios/${notificacion.negocioId}${accion ? `?${accion}=1` : ''}`)
  }

  return (
    <div className="relative" ref={contenedor}>
      <button
        type="button"
        onClick={() => setAbierta((actual) => !actual)}
        aria-expanded={abierta}
        aria-label={sinLeer > 0 ? `Notificaciones, ${sinLeer} sin leer` : 'Notificaciones'}
        className="relative rounded-lg p-2 text-stone-600 hover:bg-stone-100 hover:text-stone-900"
      >
        <svg viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
          <path d="M6 9a6 6 0 1 1 12 0c0 5 2 6.5 2 6.5H4S6 14 6 9Zm4 10a2 2 0 0 0 4 0" strokeLinejoin="round" />
        </svg>
        {sinLeer > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex min-w-5 items-center justify-center rounded-full bg-red-600 px-1 text-xs font-semibold text-white">
            {sinLeer > 99 ? '99+' : sinLeer}
          </span>
        )}
      </button>
      {abierta && (
        <div className="fixed inset-x-4 top-16 z-30 max-h-[70dvh] overflow-y-auto rounded-xl bg-white shadow-xl ring-1 ring-stone-200 sm:absolute sm:inset-x-auto sm:top-12 sm:right-0 sm:w-96">
          <div className="flex items-center justify-between border-b border-stone-100 px-4 py-3">
            <p className="font-semibold">Notificaciones</p>
            {sinLeer > 0 && (
              <button
                type="button"
                onClick={() => todas.mutate()}
                className="text-sm font-medium text-marca-700 hover:underline"
              >
                Marcar todas como leídas
              </button>
            )}
          </div>
          {bandeja.data?.notificaciones.length === 0 && (
            <p className="px-4 py-6 text-center text-sm text-stone-500">No tienes notificaciones.</p>
          )}
          <ul className="divide-y divide-stone-100">
            {bandeja.data?.notificaciones.map((notificacion) => (
              <li key={notificacion.id} className={`px-4 py-3 ${notificacion.leida ? 'opacity-60' : ''}`}>
                <div className="flex gap-3">
                  <span className={`mt-1.5 size-2 shrink-0 rounded-full ${ESTILO_TIPO[notificacion.tipo]}`} aria-hidden />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-stone-800">{notificacion.mensaje}</p>
                    <p className="mt-0.5 text-xs text-stone-500">{formatearFechaHora(notificacion.fecha)}</p>
                    <div className="mt-2 flex flex-wrap gap-3 text-sm font-medium">
                      {ES_DE_PAGO.includes(notificacion.tipo) && notificacion.negocioId && !notificacion.leida && (
                        <button type="button" onClick={() => irA(notificacion, 'registrarPago')} className="text-marca-700 hover:underline">
                          Registrar pago
                        </button>
                      )}
                      <button type="button" onClick={() => irA(notificacion)} className="text-stone-600 hover:underline">
                        {notificacion.tipo === 'INTEGRACION_CON_ERROR' ? 'Ver integraciones' : 'Ver negocio'}
                      </button>
                      {!notificacion.leida && (
                        <button type="button" onClick={() => leida.mutate(notificacion.id)} className="text-stone-500 hover:underline">
                          Marcar leída
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
