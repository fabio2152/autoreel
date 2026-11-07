import type { TipoFoto } from './catalogo'
import { pedir } from './cliente'
import type { Opcion } from './negocio'

export type TipoPaso = 'BOTONES' | 'PRODUCTO' | 'TEXTO'
export type EstadoConversacion = 'EN_CURSO' | 'COMPLETA' | 'ABANDONADA'

export interface PasoChat {
  id: string
  tipo: TipoPaso
  texto: string
  opciones: Opcion[]
  obligatoria: boolean
  /** Se puede responder "No, gracias". */
  omitible: boolean
  /** Punto de partida que se ofrece (la descripción del producto). */
  sugerencia: string | null
  /** Ejemplo corto de respuesta, bajo la caja de texto. */
  ejemplo: string | null
  /** Se puede responder escribiendo (en la primera pregunta, además de los botones). */
  admiteTexto: boolean
}

export interface MensajeChat {
  id: number
  autor: 'SISTEMA' | 'USUARIO'
  texto: string
  fechaHora: string
  /** Texto que todavía puede agruparse con el siguiente (RN-17). */
  pendiente: boolean
}

export interface ProductoCarrusel {
  id: number
  nombre: string
  descripcion: string
  foto: string | null
}

export interface Conversacion {
  id: number
  estado: EstadoConversacion
  paso: PasoChat | null
  mensajes: MensajeChat[]
  /** Milisegundos hasta cerrar la respuesta agrupada; null si no hay mensajes pendientes. */
  procesarEnMs: number | null
  respuestas: Record<string, unknown>
  aviso: string
  maximoCaracteres: number
  productos: ProductoCarrusel[]
  productosIncompletos: number
  resumen: { pregunta: string; respuesta: string }[]
  /** El video que nació al terminar la conversación; null mientras está en curso. */
  videoId: number | null
}

/** Exactamente una forma de respuesta, junto con el paso al que responde. */
export type Respuesta =
  | { paso: string; texto: string }
  | { paso: string; opcion: string }
  | { paso: string; productoId: number }
  | {
      paso: string
      productoNuevo: {
        nombre: string
        descripcion: string
        fotos: Partial<Record<TipoFoto, string>>
        guardarEnCatalogo: boolean
      }
    }
  | { paso: string; omitir: true }

export const CLAVE_CHAT = ['chat', 'actual']

/** La conversación en curso o la que se completó hoy; null si no hay ninguna. */
export async function obtenerConversacion(): Promise<Conversacion | null> {
  return (await pedir<Conversacion | undefined>('/api/chat/conversaciones/actual')) ?? null
}

export const iniciarConversacion = (empezarDeNuevo: boolean) =>
  pedir<Conversacion>('/api/chat/conversaciones', 'POST', { empezarDeNuevo })

export const responder = (id: number, respuesta: Respuesta) =>
  pedir<Conversacion>(`/api/chat/conversaciones/${id}/mensajes`, 'POST', respuesta)

export const procesar = (id: number) => pedir<Conversacion>(`/api/chat/conversaciones/${id}/procesar`, 'POST')
