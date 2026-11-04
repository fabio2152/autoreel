import { pedir } from './cliente'

export interface Opcion {
  valor: string
  texto: string
}

export type TipoPregunta = 'TEXTO' | 'SELECCION_MULTIPLE' | 'TELEFONO'

/** Pregunta del onboarding definida por el paquete de nicho. */
export interface Pregunta {
  id: string
  paso: number
  texto: string
  ayuda: string | null
  tipo: TipoPregunta
  obligatoria: boolean
  maxLongitud: number | null
  opciones: Opcion[]
  obligatoriaSi: { pregunta: string; contiene: string } | null
}

export interface ConfiguracionNicho {
  nicho: string
  nombre: string
  estilos: Opcion[]
  preguntas: Pregunta[]
  temporadas: { id: string; nombre: string }[]
  temporadasVigentes: string[]
}

export type Respuestas = Record<string, string | string[]>

export interface Imagen {
  clave: string
  url: string
}

export interface Perfil {
  nombre: string
  nicho: string
  logo: Imagen | null
  logoSinTransparencia: boolean
  colorPrimario: string | null
  colorSecundario: string | null
  estilo: string | null
  fotoLocal: Imagen | null
  respuestas: Respuestas
  onboardingCompleto: boolean
  avisos: string[]
}

/** Cambios parciales: lo que no viene no cambia; null borra. En respuestas, null borra esa respuesta. */
export interface CambiosPerfil {
  nombre?: string
  logo?: string | null
  colorPrimario?: string | null
  colorSecundario?: string | null
  estilo?: string | null
  fotoLocal?: string | null
  respuestas?: Record<string, string | string[] | null>
}

export const CLAVE_PERFIL = ['perfil']
export const CLAVE_CONFIGURACION = ['nicho', 'configuracion']

export const obtenerConfiguracion = () => pedir<ConfiguracionNicho>('/api/nicho/configuracion')

export const obtenerPerfil = () => pedir<Perfil>('/api/perfil')

export const actualizarPerfil = (cambios: CambiosPerfil) => pedir<Perfil>('/api/perfil', 'PUT', cambios)

export const completarOnboarding = () => pedir<Perfil>('/api/perfil/completar-onboarding', 'POST')

/** Si la pregunta es obligatoria con las respuestas actuales (incluye las condicionales, como el WhatsApp). */
export function esObligatoria(pregunta: Pregunta, respuestas: Respuestas): boolean {
  if (pregunta.obligatoria) return true
  const condicion = pregunta.obligatoriaSi
  if (!condicion) return false
  const valor = respuestas[condicion.pregunta]
  return Array.isArray(valor) && valor.includes(condicion.contiene)
}

export function estaRespondida(valor: string | string[] | undefined): boolean {
  return Array.isArray(valor) ? valor.length > 0 : !!valor && valor.trim().length > 0
}
