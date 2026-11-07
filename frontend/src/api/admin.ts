import type { Producto } from './catalogo'
import type { Sesion } from './auth'
import { pedir } from './cliente'
import type { Perfil } from './negocio'

export type EstadoPago = 'AL_DIA' | 'POR_VENCER' | 'VENCE_HOY' | 'VENCIDO'
export type EstadoNegocio = 'ACTIVO' | 'SUSPENDIDO'

export interface NegocioResumen {
  id: number
  nombre: string
  correo: string | null
  nicho: string
  /** Plan que rige hoy (el de un beneficio vigente, si lo tiene). */
  plan: string
  modeloVideo: string
  conBeneficio: boolean
  proximaFechaPago: string
  estadoPago: EstadoPago
  /** Videos generados en el mes de Lima en curso. */
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
    diaPago: number
    diasAviso: number
    estadoPago: EstadoPago
  }
  /** Plan que rige hoy: el de la suscripción o el de un beneficio vigente (RN-50). */
  planEfectivo: {
    planId: number
    plan: string
    modeloTexto: string
    modeloVideo: string
    videosPorDia: number
    regeneracionesPorVideo: number
    beneficioHasta: string | null
  }
  /** Costo estimado de IA del mes en curso, en dólares. */
  costoDelMes: Costo
  perfil: {
    perfil: Perfil
    /** Respuestas del onboarding con el texto de la pregunta y de las opciones. */
    respuestas: { pregunta: string; respuesta: string }[]
  }
  videos: {
    /** Videos generados en el mes de Lima en curso. */
    delMes: number
    recientes: {
      id: number
      tipoVideo: string
      estado: string
      fechaCreacion: string
      modeloVideo: string
      costoEstimado: number
      planosConRespaldo: number
      motivoError: string | null
    }[]
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
  modeloTextoId: number
  modeloTexto: string
  modeloVideoId: number
  modeloVideo: string
  precioReferencial: number | null
  activo: boolean
  /** Negocios con este plan en su suscripción. */
  negocios: number
}

export interface SolicitudPlan {
  nombre: string
  descripcion: string | null
  videosPorDia: number
  regeneracionesPorVideo: number
  modeloTextoId: number | null
  modeloVideoId: number | null
  precioReferencial: number | null
  activo: boolean
}

export interface Costo {
  guion: number
  clips: number
  total: number
  /** Videos con algún costo en el período. */
  videos: number
}

export interface CostosDelMes {
  mes: string
  total: Costo
  negocios: { negocioId: number; nombre: string; costo: Costo }[]
}

export type MedioPago = 'TRANSFERENCIA' | 'YAPE' | 'PLIN' | 'EFECTIVO' | 'OTRO'

export interface Pago {
  id: number
  fechaPago: string
  monto: number
  medio: MedioPago
  periodoCubierto: string
  nota: string | null
  fechaRegistro: string
}

export interface Beneficio {
  id: number
  planId: number
  plan: string
  modeloVideo: string
  desde: string
  hasta: string
  motivo: string
  estado: 'PROGRAMADO' | 'VIGENTE' | 'TERMINADO'
}

export type TipoNotificacion =
  | 'PAGO_POR_VENCER'
  | 'PAGO_HOY'
  | 'PAGO_VENCIDO'
  | 'SUGERENCIA_SUSPENSION'
  | 'INTEGRACION_CON_ERROR'

export interface Notificacion {
  id: number
  tipo: TipoNotificacion
  negocioId: number | null
  periodo: string
  mensaje: string
  fecha: string
  leida: boolean
}

export type TipoModelo = 'TEXTO' | 'VIDEO'

export interface ModeloIA {
  id: number
  nombre: string
  proveedor: string
  tipo: TipoModelo
  adaptador: string
  identificadorModelo: string
  parametros: Record<string, unknown>
  costoReferencial: number
  unidadCosto: 'POR_SEGUNDO' | 'POR_SOLICITUD'
  nombreSecreto: string
  estado: 'ACTIVO' | 'INACTIVO'
  ultimaPrueba: string | null
  ultimaPruebaExitosa: boolean | null
  ultimaPruebaMensaje: string | null
  credencialConfigurada: boolean
  adaptadorDisponible: boolean
  planes: string[]
}

export interface Credencial {
  nombre: string
  /** PANEL: pegada en Ajustes. CONFIGURACION: viene del .env. NINGUNO: falta. */
  origen: 'PANEL' | 'CONFIGURACION' | 'NINGUNO'
  descripcion: string | null
  modelos: string[]
}

export interface Integraciones {
  modelos: ModeloIA[]
  credenciales: Credencial[]
  adaptadores: Record<TipoModelo, string[]>
}

export interface SolicitudModelo {
  nombre: string
  proveedor: string
  tipo: TipoModelo
  adaptador: string
  identificadorModelo: string
  parametros: Record<string, unknown>
  costoReferencial: number | null
  nombreSecreto: string
}

export interface ResultadoPrueba {
  exitosa: boolean
  mensaje: string
  fecha: string
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

/** Catálogo completo del negocio, incluidos los archivados. */
export const listarProductosDeNegocio = (id: number) => pedir<Producto[]>(`/api/admin/negocios/${id}/productos`)

export const crearNegocio = (solicitud: SolicitudCrearNegocio) =>
  pedir<ContrasenaTemporal>('/api/admin/negocios', 'POST', solicitud)

export const reiniciarContrasena = (id: number) =>
  pedir<ContrasenaTemporal>(`/api/admin/negocios/${id}/reiniciar-contrasena`, 'POST')

export const suspenderNegocio = (id: number) => pedir<void>(`/api/admin/negocios/${id}/suspender`, 'POST')

export const reactivarNegocio = (id: number) => pedir<void>(`/api/admin/negocios/${id}/reactivar`, 'POST')

export const listarPlanes = () => pedir<Plan[]>('/api/admin/planes')

export const listarNichos = () => pedir<Nicho[]>('/api/admin/nichos')

export const crearPlan = (solicitud: SolicitudPlan) => pedir<Plan>('/api/admin/planes', 'POST', solicitud)

export const actualizarPlan = (id: number, solicitud: SolicitudPlan) =>
  pedir<Plan>(`/api/admin/planes/${id}`, 'PUT', solicitud)

export const cambiarPlanDeNegocio = (id: number, datos: { planId: number; montoMensual: number }) =>
  pedir<void>(`/api/admin/negocios/${id}/plan`, 'PUT', datos)

export const listarPagos = (id: number) => pedir<Pago[]>(`/api/admin/negocios/${id}/pagos`)

export const registrarPago = (
  id: number,
  datos: { fechaPago: string; monto: number | null; medio: MedioPago | null; nota: string },
) => pedir<{ pago: Pago; proximaFechaPago: string }>(`/api/admin/negocios/${id}/pagos`, 'POST', datos)

export const listarBeneficios = (id: number) => pedir<Beneficio[]>(`/api/admin/negocios/${id}/beneficios`)

export const otorgarBeneficio = (
  id: number,
  datos: { planId: number | null; desde: string; hasta: string; motivo: string },
) => pedir<Beneficio>(`/api/admin/negocios/${id}/beneficios`, 'POST', datos)

export const terminarBeneficio = (id: number, beneficioId: number) =>
  pedir<void>(`/api/admin/negocios/${id}/beneficios/${beneficioId}`, 'DELETE')

export const entrarComoNegocio = (id: number) => pedir<Sesion>(`/api/admin/negocios/${id}/entrar`, 'POST')

export const obtenerNotificaciones = () =>
  pedir<{ sinLeer: number; notificaciones: Notificacion[] }>('/api/admin/notificaciones')

export const marcarNotificacionLeida = (id: number) => pedir<void>(`/api/admin/notificaciones/${id}/leida`, 'POST')

export const marcarNotificacionesLeidas = () => pedir<void>('/api/admin/notificaciones/leidas', 'POST')

export const obtenerCostos = (mes: string) => pedir<CostosDelMes>(`/api/admin/costos?mes=${encodeURIComponent(mes)}`)

export const obtenerIntegraciones = () => pedir<Integraciones>('/api/admin/integraciones')

export const crearModelo = (solicitud: SolicitudModelo) => pedir<ModeloIA>('/api/admin/modelos-ia', 'POST', solicitud)

export const actualizarModelo = (id: number, solicitud: SolicitudModelo) =>
  pedir<ModeloIA>(`/api/admin/modelos-ia/${id}`, 'PUT', solicitud)

export const probarModelo = (id: number) => pedir<ResultadoPrueba>(`/api/admin/modelos-ia/${id}/probar`, 'POST')

export const activarModelo = (id: number) => pedir<ModeloIA>(`/api/admin/modelos-ia/${id}/activar`, 'POST')

export const desactivarModelo = (id: number) => pedir<ModeloIA>(`/api/admin/modelos-ia/${id}/desactivar`, 'POST')

export const guardarCredencial = (nombre: string, valor: string) =>
  pedir<Credencial>('/api/admin/credenciales', 'PUT', { nombre, valor })

export const borrarCredencial = (nombre: string) =>
  pedir<Credencial>(`/api/admin/credenciales?nombre=${encodeURIComponent(nombre)}`, 'DELETE')
