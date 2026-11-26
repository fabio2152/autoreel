import { pedir } from './cliente'

/** Una sola foto por producto (2026-11-25): la IA trabaja mejor con una sola, y cada toma del video empieza en ella. */
export type TipoFoto = 'FOTO_1'
export type EstadoProducto = 'ACTIVO' | 'ARCHIVADO'

export const NOMBRE_FOTO = 'Foto del producto'

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
  /** Tiene su foto y puede usarse en un video (RN-12). */
  completo: boolean
  fotos: FotoProducto[]
  fechaCreacion: string
  avisos: string[]
}

/** La foto va en FOTO_1 con su clave: la guardada, una subida nueva o ninguna (el producto queda incompleto). */
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

export const fotoDe = (producto: Producto) => producto.fotos.find((foto) => foto.tipo === 'FOTO_1')
