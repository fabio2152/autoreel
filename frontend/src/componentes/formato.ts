const ZONA_LIMA = 'America/Lima'

/** Fecha de negocio (yyyy-mm-dd) sin conversión de zona horaria: "15 ene. 2026". */
export function formatearFecha(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-').map(Number)
  return new Date(anio, mes - 1, dia).toLocaleDateString('es-PE', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/** Instante del servidor mostrado en hora de Lima. */
export function formatearFechaHora(instante: string | null): string {
  if (!instante) return '—'
  return new Date(instante).toLocaleString('es-PE', {
    timeZone: ZONA_LIMA,
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatearMonto(monto: number): string {
  return `US$ ${monto.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
