import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { CLAVE_VIDEOS, listarVideos, type EstadoVideo } from '../api/videos'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Cargando } from '../componentes/Cargando'
import { formatearFechaHora } from '../componentes/formato'
import { ImagenRemota } from '../componentes/ImagenRemota'

const ESTADOS: Record<EstadoVideo, { texto: string; estilo: string }> = {
  GUION_PENDIENTE: { texto: 'Preparando propuesta', estilo: 'bg-amber-50 text-amber-800' },
  STORYBOARD: { texto: 'Por revisar', estilo: 'bg-marca-50 text-marca-700' },
  GENERANDO: { texto: 'Creando video', estilo: 'bg-amber-50 text-amber-800' },
  COMPONIENDO: { texto: 'Creando video', estilo: 'bg-amber-50 text-amber-800' },
  LISTO: { texto: 'Listo', estilo: 'bg-emerald-50 text-emerald-800' },
  APROBADO: { texto: 'Aprobado', estilo: 'bg-emerald-50 text-emerald-800' },
  PROGRAMADO: { texto: 'Programado', estilo: 'bg-sky-50 text-sky-800' },
  PUBLICADO: { texto: 'Publicado', estilo: 'bg-emerald-50 text-emerald-800' },
  ERROR: { texto: 'Con problemas', estilo: 'bg-red-50 text-red-800' },
  DESCARTADO: { texto: 'Descartado', estilo: 'bg-stone-100 text-stone-600' },
}

/** Los videos del negocio, los más recientes primero. Base del historial (RF-26). */
export function PaginaMisVideos() {
  const videos = useQuery({
    queryKey: CLAVE_VIDEOS,
    queryFn: listarVideos,
    // Mientras alguna propuesta se está preparando, se vuelve a consultar para mostrar cuándo está lista.
    refetchInterval: (query) =>
      query.state.data?.some((video) => video.estado === 'GUION_PENDIENTE') ? 5000 : false,
  })

  if (videos.isPending) return <Cargando />
  if (videos.isError) {
    return (
      <div className="mx-auto max-w-md space-y-3">
        <Aviso tipo="error">{videos.error.message}</Aviso>
        <Boton variante="secundario" onClick={() => videos.refetch()}>
          Reintentar
        </Boton>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <h1 className="text-xl font-semibold text-stone-900">Mis videos</h1>
      {videos.data.length === 0 ? (
        <div className="rounded-2xl bg-white p-6 text-center ring-1 ring-stone-200">
          <p className="text-sm text-stone-600">Todavía no tienes videos.</p>
          <Link to="/" className="mt-3 inline-block text-sm font-semibold text-marca-700">
            Crear mi primer video
          </Link>
        </div>
      ) : (
        <ul className="space-y-3">
          {videos.data.map((video) => {
            const estado = ESTADOS[video.estado]
            return (
              <li key={video.id}>
                <Link
                  to={`/videos/${video.id}`}
                  className="flex items-center gap-3 rounded-2xl bg-white p-3 ring-1 ring-stone-200 hover:ring-marca-600"
                >
                  {video.foto ? (
                    <ImagenRemota
                      consulta={CLAVE_VIDEOS}
                      src={video.foto}
                      alt=""
                      className="aspect-[9/16] w-14 shrink-0 rounded-lg bg-stone-100 object-cover"
                    />
                  ) : (
                    <span className="aspect-[9/16] w-14 shrink-0 rounded-lg bg-stone-100" aria-hidden />
                  )}
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-semibold text-stone-900">{video.producto}</span>
                    <span className="block text-sm text-stone-500">{video.tipoVideo}</span>
                    <span className="block text-xs text-stone-400">{formatearFechaHora(video.fechaCreacion)}</span>
                  </span>
                  <span className={`shrink-0 rounded-full px-2 py-1 text-xs font-medium ${estado.estilo}`}>
                    {estado.texto}
                  </span>
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
