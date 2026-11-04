import type { QueryClient } from '@tanstack/react-query'
import { listarVideos, type VideoResumen } from '../api/videos'

export const CLAVE_AVISOS = ['videos', 'avisos']

const EN_PROCESO = 'autoreel.videosEnProceso'
const POR_AVISAR = 'autoreel.videosPorAvisar'

export interface Avisos {
  /** Videos que terminaron (LISTO o ERROR) desde que se vieron en proceso en esta pestaña. */
  terminados: VideoResumen[]
  hayEnProceso: boolean
}

function leer(clave: string): number[] {
  try {
    return JSON.parse(sessionStorage.getItem(clave) ?? '[]') as number[]
  } catch {
    return []
  }
}

function escribir(clave: string, ids: number[]) {
  try {
    sessionStorage.setItem(clave, JSON.stringify(ids))
  } catch {
    // Sin almacenamiento (modo privado): solo se pierde el aviso.
  }
}

const enProceso = (video: VideoResumen) => video.estado === 'GENERANDO' || video.estado === 'COMPONIENDO'

/** Compara con lo que se vio la vez anterior: un video que estaba en proceso y ya terminó queda por avisar. */
export async function revisar(): Promise<Avisos> {
  const videos = await listarVideos()
  const antes = leer(EN_PROCESO)
  const porAvisar = new Set(leer(POR_AVISAR))
  for (const video of videos) {
    if (video.estado === 'LISTO' || video.estado === 'ERROR') {
      if (antes.includes(video.id)) porAvisar.add(video.id)
    } else {
      // Ya no está terminado (por ejemplo, se está regenerando): se avisará otra vez cuando termine.
      porAvisar.delete(video.id)
    }
  }
  escribir(EN_PROCESO, videos.filter(enProceso).map((video) => video.id))
  escribir(POR_AVISAR, [...porAvisar])
  return {
    terminados: videos.filter((video) => porAvisar.has(video.id)),
    hayEnProceso: videos.some(enProceso),
  }
}

/** Quita el aviso de un video (al abrirlo o al cerrarlo). */
export function olvidarAviso(clienteConsultas: QueryClient, videoId: number) {
  escribir(POR_AVISAR, leer(POR_AVISAR).filter((id) => id !== videoId))
  clienteConsultas.setQueryData<Avisos>(CLAVE_AVISOS, (avisos) =>
    avisos ? { ...avisos, terminados: avisos.terminados.filter((video) => video.id !== videoId) } : avisos,
  )
}
