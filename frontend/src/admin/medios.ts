import type { MedioPago } from '../api/admin'

export const MEDIOS: { valor: MedioPago; texto: string }[] = [
  { valor: 'TRANSFERENCIA', texto: 'Transferencia' },
  { valor: 'YAPE', texto: 'Yape' },
  { valor: 'PLIN', texto: 'Plin' },
  { valor: 'EFECTIVO', texto: 'Efectivo' },
  { valor: 'OTRO', texto: 'Otro' },
]

export const TEXTO_MEDIO = Object.fromEntries(MEDIOS.map((medio) => [medio.valor, medio.texto])) as Record<
  MedioPago,
  string
>
