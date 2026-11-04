import {
  esObligatoria,
  estaRespondida,
  type CambiosPerfil,
  type Perfil,
  type Pregunta,
  type Respuestas,
} from '../api/negocio'

/** Lo que el usuario está editando del perfil, antes de guardarlo. */
export interface BorradorPerfil {
  nombre: string
  /** Clave guardada o clave de una subida nueva. */
  logo: string | null
  logoUrl: string | null
  /** Archivo del logo recién elegido, para "tomar los colores del logo" sin salir del navegador. */
  logoArchivo: Blob | null
  colorPrimario: string
  colorSecundario: string
  estilo: string | null
  fotoLocal: string | null
  fotoLocalUrl: string | null
  respuestas: Respuestas
}

export function borradorDesde(perfil: Perfil): BorradorPerfil {
  return {
    nombre: perfil.nombre,
    logo: perfil.logo?.clave ?? null,
    logoUrl: perfil.logo?.url ?? null,
    logoArchivo: null,
    colorPrimario: perfil.colorPrimario ?? '#be123c',
    colorSecundario: perfil.colorSecundario ?? '#fde68a',
    estilo: perfil.estilo,
    fotoLocal: perfil.fotoLocal?.clave ?? null,
    fotoLocalUrl: perfil.fotoLocal?.url ?? null,
    respuestas: perfil.respuestas,
  }
}

/** Cambios de la marca (paso 1 del asistente). */
export function cambiosDeMarca(borrador: BorradorPerfil): CambiosPerfil {
  return {
    nombre: borrador.nombre,
    logo: borrador.logo,
    colorPrimario: borrador.colorPrimario,
    colorSecundario: borrador.colorSecundario,
    estilo: borrador.estilo,
  }
}

/** Respuestas de un grupo de preguntas; las vacías se envían como null para borrarlas. */
export function cambiosDeRespuestas(borrador: BorradorPerfil, preguntas: Pregunta[]): CambiosPerfil {
  const respuestas: Record<string, string | string[] | null> = {}
  for (const pregunta of preguntas) {
    const valor = borrador.respuestas[pregunta.id]
    respuestas[pregunta.id] = estaRespondida(valor) ? valor : null
  }
  return { respuestas }
}

/** Primer faltante obligatorio de la marca, para avisarlo antes de enviar. */
export function faltanteDeMarca(borrador: BorradorPerfil): string | null {
  if (!borrador.nombre.trim()) return 'Escribe el nombre de tu negocio.'
  if (!borrador.logo) return 'Sube tu logo.'
  if (!borrador.estilo) return 'Elige el estilo de tus videos.'
  return null
}

export function faltanteDePreguntas(borrador: BorradorPerfil, preguntas: Pregunta[]): string | null {
  const pregunta = preguntas.find(
    (p) => esObligatoria(p, borrador.respuestas) && !estaRespondida(borrador.respuestas[p.id]),
  )
  return pregunta ? `Falta responder: ${pregunta.texto}` : null
}
