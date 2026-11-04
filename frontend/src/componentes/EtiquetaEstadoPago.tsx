import type { EstadoPago } from '../api/admin'

/** Colores de la sección 14: verde al día, amarillo por vencer, naranja hoy, rojo vencido. */
const estados: Record<EstadoPago, { texto: string; estilo: string }> = {
  AL_DIA: { texto: 'Al día', estilo: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' },
  POR_VENCER: { texto: 'Por vencer', estilo: 'bg-yellow-50 text-yellow-800 ring-yellow-600/30' },
  VENCE_HOY: { texto: 'Vence hoy', estilo: 'bg-orange-50 text-orange-700 ring-orange-600/30' },
  VENCIDO: { texto: 'Vencido', estilo: 'bg-red-50 text-red-700 ring-red-600/20' },
}

export function EtiquetaEstadoPago({ estado }: { estado: EstadoPago }) {
  const { texto, estilo } = estados[estado]
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${estilo}`}>
      {texto}
    </span>
  )
}
