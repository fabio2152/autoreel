import type { TipoFoto } from './catalogo'
import { pedir } from './cliente'

export type EstadoVideo =
  | 'GUION_PENDIENTE'
  | 'STORYBOARD'
  | 'GENERANDO'
  | 'COMPONIENDO'
  | 'LISTO'
  | 'APROBADO'
  | 'PROGRAMADO'
  | 'PUBLICADO'
  | 'ERROR'
  | 'DESCARTADO'

/** Estado del clip de un plano; null en el storyboard. */
export type EstadoPlano = 'PENDIENTE' | 'GENERANDO' | 'LISTO' | 'RESPALDO'

/** Estados en los que el sistema trabaja solo: la pantalla consulta hasta que terminan. */
export const EN_PROCESO: EstadoVideo[] = ['GUION_PENDIENTE', 'GENERANDO', 'COMPONIENDO']

export interface FotoVista {
  /** null si el usuario subió una foto que no es del producto. */
  tipo: TipoFoto | null
  url: string
}

export interface PlanoVista {
  orden: number
  nombre: string
  segundos: number
  foto: FotoVista
  movimiento: { id: string; descripcion: string }
  texto: string
  maxPalabras: number
  estado: EstadoPlano | null
  /** El plano usa el acercamiento hecho por el sistema en lugar del clip de IA. */
  usoRespaldo: boolean
}

export interface CierreVista {
  orden: number
  nombre: string
  segundos: number
  precio: string | null
  maximoCaracteresPrecio: number
  llamado: string | null
  ubicacion: string
  whatsapp: string | null
  logo: string | null
  colorPrimario: string | null
  colorSecundario: string | null
  fotoLocal: string | null
}

export interface Video {
  id: number
  estado: EstadoVideo
  tipoVideo: string
  producto: { id: number; nombre: string }
  plan: { nombre: string; videosPorDia: number; regeneracionesPorVideo: number }
  avisoDeRespaldo: string | null
  /** Vacío mientras el guion está pendiente. */
  planos: PlanoVista[]
  cierre: CierreVista | null
  descripcion: string | null
  hashtags: string[]
  maximoPublicacion: number
  fotosDelProducto: FotoVista[]
  fechaCreacion: string
  generacion: {
    /** Videos aprobados hoy (día de Lima) y si se puede aprobar otro (RN-26). */
    videosHoy: { usados: number; porDia: number; puedeAprobar: boolean }
    /** El MP4 final; null hasta que esté listo. */
    urlVideo: string | null
    regeneracionesUsadas: number
    /** Si se pueden crear clips nuevos con IA ahora; si no, no se ofrece regenerar. */
    iaDisponible: boolean
    avisoRevision: string | null
    motivoError: string | null
  }
}

export interface VideoResumen {
  id: number
  estado: EstadoVideo
  tipoVideo: string
  producto: string
  foto: string | null
  fechaCreacion: string
}

export type CambioDePlano =
  | { texto: string }
  | { foto: { tipo: TipoFoto } | { clave: string } }
  | { precio: string }

export const CLAVE_VIDEOS = ['videos']
export const claveVideo = (id: number) => ['videos', id]

export const listarVideos = () => pedir<VideoResumen[]>('/api/videos')

export const obtenerVideo = (id: number) => pedir<Video>(`/api/videos/${id}`)

export const editarPlano = (id: number, orden: number, cambio: CambioDePlano) =>
  pedir<Video>(`/api/videos/${id}/planos/${orden}`, 'PUT', cambio)

export const descartarVideo = (id: number) => pedir<Video>(`/api/videos/${id}/descartar`, 'POST')

/** "Crear mi video": consume el video del día y empieza la generación. */
export const aprobarStoryboard = (id: number) => pedir<Video>(`/api/videos/${id}/aprobar-storyboard`, 'POST')

export const regenerarPlano = (id: number, orden: number) =>
  pedir<Video>(`/api/videos/${id}/planos/${orden}/regenerar`, 'POST')

export const aprobarVideo = (id: number) => pedir<Video>(`/api/videos/${id}/aprobar`, 'POST')

/** Enlace temporal para descargar el MP4. */
export const enlaceDeDescarga = async (id: number) => (await pedir<{ url: string }>(`/api/videos/${id}/descarga`)).url

/** Igual que Texto.contarPalabras del backend: lo separado por espacios con al menos una letra o un número. */
export function contarPalabras(texto: string): number {
  const limpio = texto.trim()
  if (!limpio) return 0
  return limpio.split(/\s+/).filter((palabra) => /[\p{L}\p{N}]/u.test(palabra)).length
}
