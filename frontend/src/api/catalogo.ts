import { pedir } from './cliente'

/** Foto 1 obligatoria; Foto 2 y Foto 3 opcionales, de cualquier ángulo (2026-11-25). La IA elige cuál usa en cada toma. */
export type TipoFoto = 'FOTO_1' | 'FOTO_2' | 'FOTO_3'
export type EstadoProducto = 'ACTIVO' | 'ARCHIVADO'

export const TIPOS_FOTO: TipoFoto[] = ['FOTO_1', 'FOTO_2', 'FOTO_3']

export const NOMBRE_FOTO: Record<TipoFoto, string> = {
  FOTO_1: 'Foto principal',
  FOTO_2: 'Foto 2 (opcional)',
  FOTO_3: 'Foto 3 (opcional)',
}

export interface FotoProducto {
  tipo: TipoFoto
  clave: string
  url: string
  ancho: number
  alto: number
  horizontal: boolean
}

export interface Producto {
  id: number
  nombre: string
  descripcion: string
  temporadas: string[]
  estado: EstadoProducto
  /** Tiene su foto principal y puede usarse en un video (RN-12). */
  completo: boolean
  fotos: FotoProducto[]
  fechaCreacion: string
  avisos: string[]
}

/** Cada foto lleva su clave: la guardada, una subida nueva o ninguna (el producto queda incompleto). */
export interface SolicitudProducto {
  nombre: string
  descripcion: string
  temporadas: string[]
  fotos: Partial<Record<TipoFoto, string>>
}

export const CLAVE_PRODUCTOS = ['productos']

export const listarProductos = (estado: EstadoProducto) => pedir<Producto[]>(`/api/productos?estado=${estado}`)

export const obtenerProducto = (id: number) => pedir<Producto>(`/api/productos/${id}`)

export const crearProducto = (solicitud: SolicitudProducto) => pedir<Producto>('/api/productos', 'POST', solicitud)

export const actualizarProducto = (id: number, solicitud: SolicitudProducto) =>
  pedir<Producto>(`/api/productos/${id}`, 'PUT', solicitud)

export const archivarProducto = (id: number) => pedir<Producto>(`/api/productos/${id}/archivar`, 'POST')

export const reactivarProducto = (id: number) => pedir<Producto>(`/api/productos/${id}/reactivar`, 'POST')

export const fotoDe = (producto: Producto, tipo: TipoFoto) => producto.fotos.find((foto) => foto.tipo === tipo)
