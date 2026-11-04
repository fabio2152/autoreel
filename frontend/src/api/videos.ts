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
  plan: { nombre: string; videosPorDia: number }
  avisoDeRespaldo: string | null
  /** Vacío mientras el guion está pendiente. */
  planos: PlanoVista[]
  cierre: CierreVista | null
  descripcion: string | null
  hashtags: string[]
  maximoPublicacion: number
  fotosDelProducto: FotoVista[]
  fechaCreacion: string
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

/** Igual que Texto.contarPalabras del backend: lo separado por espacios con al menos una letra o un número. */
export function contarPalabras(texto: string): number {
  const limpio = texto.trim()
  if (!limpio) return 0
  return limpio.split(/\s+/).filter((palabra) => /[\p{L}\p{N}]/u.test(palabra)).length
}
