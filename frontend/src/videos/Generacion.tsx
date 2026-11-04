import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { claveVideo, type EstadoPlano, type Video } from '../api/videos'

const MENSAJES = [
  'Estamos dando vida a tus fotos…',
  'Cada momento se prepara con su movimiento…',
  'Ya casi: le estamos poniendo música…',
  'Tu video de 15 segundos está en camino…',
]

const ESTADOS_DEL_PLANO: Record<EstadoPlano, string> = {
  PENDIENTE: 'En espera',
  GENERANDO: 'Creando el clip…',
  LISTO: 'Listo',
  RESPALDO: 'Listo',
}

/** RF-23: mientras se generan los clips y se compone el video. El usuario puede salir y volver. */
export function PantallaGenerando({ video }: { video: Video }) {
  const [mensaje, setMensaje] = useState(0)
  useEffect(() => {
    const temporizador = window.setInterval(() => setMensaje((actual) => (actual + 1) % MENSAJES.length), 4000)
    return () => window.clearInterval(temporizador)
  }, [])
  const componiendo = video.estado === 'COMPONIENDO'

  return (
    <div className="mx-auto max-w-md space-y-6 py-6 text-center">
      <div>
        <span
          className="mx-auto block size-12 animate-spin rounded-full border-4 border-marca-600 border-t-transparent"
          aria-hidden
        />
        <h1 className="mt-4 text-xl font-semibold text-stone-900" aria-live="polite">
          {componiendo ? 'Armando tu video' : 'Creando tu video'}
        </h1>
        <p className="mt-2 text-sm text-stone-600">
          {componiendo ? 'Le estamos poniendo los textos, tu logo y la música…' : MENSAJES[mensaje]}
        </p>
      </div>

      <ol className="space-y-2 text-left">
        {video.planos.map((plano) => (
          <li key={plano.orden} className="flex items-center justify-between rounded-xl bg-white px-4 py-3 ring-1 ring-stone-200">
            <span className="text-sm font-medium text-stone-800">
              {plano.orden}. {plano.nombre}
            </span>
            <span className="text-xs text-stone-500">
              {plano.estado ? ESTADOS_DEL_PLANO[plano.estado] : 'En espera'}
            </span>
          </li>
        ))}
        <li className="flex items-center justify-between rounded-xl bg-white px-4 py-3 ring-1 ring-stone-200">
          <span className="text-sm font-medium text-stone-800">4. Cierre con tu marca</span>
          <span className="text-xs text-stone-500">{componiendo ? 'Armando…' : 'En espera'}</span>
        </li>
      </ol>

      <p className="text-sm text-stone-600">
        Puede tardar unos minutos. Puedes salir: te avisaremos y tu video te esperará en{' '}
        <Link to="/videos" className="font-semibold text-marca-700">
          Mis videos
        </Link>
        .
      </p>
    </div>
  )
}

/** El video final, vertical. Si su enlace temporal venció, se pide la vista otra vez para tener uno nuevo. */
export function Reproductor({ video }: { video: Video }) {
  const clienteConsultas = useQueryClient()
  const [reintentado, setReintentado] = useState(false)
  if (!video.generacion.urlVideo) return null
  return (
    <video
      key={video.generacion.urlVideo}
      src={video.generacion.urlVideo}
      controls
      playsInline
      preload="metadata"
      className="mx-auto aspect-[9/16] max-h-[70vh] w-full max-w-xs rounded-2xl bg-black"
      onError={() => {
        if (!reintentado) {
          setReintentado(true)
          clienteConsultas.invalidateQueries({ queryKey: claveVideo(video.id) })
        }
      }}
    >
      Tu navegador no puede reproducir el video. Descárgalo para verlo.
    </video>
  )
}
