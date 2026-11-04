import type { ReactNode } from 'react'

type Tipo = 'error' | 'exito' | 'info'

const estilos: Record<Tipo, string> = {
  error: 'bg-red-50 text-red-800 ring-red-200',
  exito: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  info: 'bg-stone-100 text-stone-700 ring-stone-200',
}

export function Aviso({ tipo = 'info', children }: { tipo?: Tipo; children: ReactNode }) {
  return (
    <div role={tipo === 'error' ? 'alert' : 'status'} className={`rounded-lg px-3 py-2 text-sm ring-1 ring-inset ${estilos[tipo]}`}>
      {children}
    </div>
  )
}
