import { pedir } from './cliente'

export type EstadoPago = 'AL_DIA' | 'POR_VENCER' | 'VENCE_HOY' | 'VENCIDO'
export type EstadoNegocio = 'ACTIVO' | 'SUSPENDIDO'

export interface NegocioResumen {
  id: number
  nombre: string
  correo: string | null
  nicho: string
  plan: string
  proximaFechaPago: string
  estadoPago: EstadoPago
  /** Vacío hasta la fase 5, cuando existan los videos. */
  videosDelMes: number | null
  estado: EstadoNegocio
  ultimoAcceso: string | null
}

export interface NegocioDetalle {
  id: number
  nombre: string
  nicho: string
  nombreNicho: string
  estado: EstadoNegocio
  onboardingCompleto: boolean
  fechaCreacion: string
  usuario: {
    correo: string
    ultimoAcceso: string | null
    debeCambiarContrasena: boolean
    contrasenaTemporalVence: string | null
    bloqueado: boolean
  }
  suscripcion: {
    planId: number
    plan: string
    montoMensual: number
    fechaInicio: string
    proximaFechaPago: string
    diasAviso: number
    estadoPago: EstadoPago
  }
}

export interface ContrasenaTemporal {
  negocioId: number
  correo: string
  contrasenaTemporal: string
  enlace: string
  vence: string
}

export interface Plan {
  id: number
  nombre: string
  descripcion: string | null
  videosPorDia: number
  regeneracionesPorVideo: number
  modeloTexto: string
  modeloVideo: string
  precioReferencial: number | null
  activo: boolean
}

export interface Nicho {
  id: string
  nombre: string
}

export interface SolicitudCrearNegocio {
  nicho: string
  nombre: string
  correo: string
  planId: number | null
  montoMensual: number | null
  fechaPrimerPago: string | null
}

export function listarNegocios(filtros: { busqueda?: string; estadoPago?: EstadoPago | '' }) {
  const parametros = new URLSearchParams()
  if (filtros.busqueda) parametros.set('busqueda', filtros.busqueda)
  if (filtros.estadoPago) parametros.set('estadoPago', filtros.estadoPago)
  const consulta = parametros.toString()
  return pedir<NegocioResumen[]>(`/api/admin/negocios${consulta ? `?${consulta}` : ''}`)
}

export const obtenerNegocio = (id: number) => pedir<NegocioDetalle>(`/api/admin/negocios/${id}`)

export const crearNegocio = (solicitud: SolicitudCrearNegocio) =>
  pedir<ContrasenaTemporal>('/api/admin/negocios', 'POST', solicitud)

export const reiniciarContrasena = (id: number) =>
  pedir<ContrasenaTemporal>(`/api/admin/negocios/${id}/reiniciar-contrasena`, 'POST')

export const suspenderNegocio = (id: number) => pedir<void>(`/api/admin/negocios/${id}/suspender`, 'POST')

export const reactivarNegocio = (id: number) => pedir<void>(`/api/admin/negocios/${id}/reactivar`, 'POST')

export const listarPlanes = () => pedir<Plan[]>('/api/admin/planes')

export const listarNichos = () => pedir<Nicho[]>('/api/admin/nichos')
