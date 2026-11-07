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

/** Costo de IA: puede ser de centavos, así que muestra hasta 4 decimales. */
export function formatearCosto(costo: number): string {
  return `US$ ${costo.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`
}

/** Hoy en Lima, como fecha de negocio (yyyy-mm-dd). */
export function hoyEnLima(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: ZONA_LIMA })
}

/** Mes (yyyy-mm) como "noviembre de 2026". */
export function formatearMes(mes: string): string {
  const [anio, numero] = mes.split('-').map(Number)
  return new Date(anio, numero - 1, 1).toLocaleDateString('es-PE', { month: 'long', year: 'numeric' })
}

/** Valor para un <input type="datetime-local"> en la hora del navegador. */
export function aEntradaLocal(fecha: Date): string {
  const local = new Date(fecha.getTime() - fecha.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}
