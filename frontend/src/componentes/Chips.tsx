import type { Opcion } from '../api/negocio'

interface Props {
  etiqueta: string
  ayuda?: string | null
  opciones: Opcion[]
  seleccionados: string[]
  /** Recibe cómo actualizar la selección, para aplicarlo sobre el estado más reciente (toques seguidos). */
  onCambiar: (actualizar: (anteriores: string[]) => string[]) => void
  /** Selección única: al tocar una opción se reemplaza la anterior. */
  unica?: boolean
}

/** Botones de selección grandes y fáciles de tocar en el celular. */
export function Chips({ etiqueta, ayuda, opciones, seleccionados, onCambiar, unica = false }: Props) {
  const alternar = (valor: string) =>
    onCambiar((anteriores) =>
      unica ? [valor] : anteriores.includes(valor) ? anteriores.filter((v) => v !== valor) : [...anteriores, valor],
    )
  return (
    <fieldset>
      <legend className="mb-1 text-sm font-medium text-stone-700">{etiqueta}</legend>
      {ayuda && <p className="mb-2 text-xs text-stone-500">{ayuda}</p>}
      <div className="flex flex-wrap gap-2">
        {opciones.map((opcion) => {
          const activa = seleccionados.includes(opcion.valor)
          return (
            <button
              key={opcion.valor}
              type="button"
              role={unica ? 'radio' : 'checkbox'}
              aria-checked={activa}
              onClick={() => alternar(opcion.valor)}
              className={`min-h-11 rounded-full px-4 py-2 text-sm font-medium ring-1 ring-inset transition ${
                activa ? 'bg-marca-700 text-white ring-marca-700' : 'bg-white text-stone-700 ring-stone-300 hover:bg-stone-50'
              }`}
            >
              {opcion.texto}
            </button>
          )
        })}
      </div>
    </fieldset>
  )
}
