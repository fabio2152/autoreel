import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, useLocation } from 'react-router'
import { CLAVE_AVISOS, olvidarAviso, revisar } from './avisos'

/**
 * RF-23: "avisa cuando está listo". Mientras haya un video en proceso, consulta cada 15 segundos; cuando termina,
 * muestra el aviso en cualquier pantalla del negocio, con el enlace al video.
 */
export function AvisoVideoListo() {
  const clienteConsultas = useQueryClient()
  const { pathname } = useLocation()
  const avisos = useQuery({
    queryKey: CLAVE_AVISOS,
    queryFn: revisar,
    refetchInterval: (query) => (query.state.data?.hayEnProceso ? 15_000 : false),
  })
  const abierto = /^\/videos\/(\d+)$/.exec(pathname)?.[1]

  // Abrir el video cuenta como haber visto el aviso.
  useEffect(() => {
    if (abierto) olvidarAviso(clienteConsultas, Number(abierto))
  }, [abierto, clienteConsultas, avisos.data])

  const video = avisos.data?.terminados.find((terminado) => String(terminado.id) !== abierto)
  if (!video) return null
  const listo = video.estado === 'LISTO'
  return (
    <div
      role="status"
      className="mx-auto mb-4 flex max-w-5xl items-center gap-3 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-900 ring-1 ring-emerald-200"
    >
      <p className="min-w-0 flex-1">
        {listo ? `Tu video de ${video.producto} está listo.` : `No pudimos terminar tu video de ${video.producto}.`}
      </p>
      <Link to={`/videos/${video.id}`} className="font-semibold text-emerald-800 underline">
        {listo ? 'Verlo' : 'Ver detalle'}
      </Link>
      <button
        type="button"
        onClick={() => olvidarAviso(clienteConsultas, video.id)}
        className="-m-2 rounded-lg p-2 text-emerald-800 hover:bg-emerald-100"
        aria-label="Cerrar el aviso"
      >
        <span aria-hidden>×</span>
      </button>
    </div>
  )
}
