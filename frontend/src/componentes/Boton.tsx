import type { ButtonHTMLAttributes } from 'react'

type Variante = 'primario' | 'secundario' | 'peligro' | 'texto'

const estilos: Record<Variante, string> = {
  primario: 'bg-marca-700 text-white hover:bg-marca-800 focus-visible:outline-marca-700',
  secundario: 'bg-white text-stone-800 ring-1 ring-inset ring-stone-300 hover:bg-stone-50 focus-visible:outline-stone-500',
  peligro: 'bg-white text-red-700 ring-1 ring-inset ring-red-300 hover:bg-red-50 focus-visible:outline-red-600',
  texto: 'text-stone-700 hover:bg-stone-100 focus-visible:outline-stone-500',
}

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: Variante
  cargando?: boolean
}

export function Boton({ variante = 'primario', cargando = false, className = '', disabled, children, ...resto }: Props) {
  return (
    <button
      {...resto}
      disabled={disabled || cargando}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-60 ${estilos[variante]} ${className}`}
    >
      {cargando && (
        <span className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden />
      )}
      {children}
    </button>
  )
}
