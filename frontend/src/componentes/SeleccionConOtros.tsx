import { useId, useState } from 'react'
import type { Pregunta } from '../api/negocio'
import { Chips } from './Chips'

interface Props {
  pregunta: Pregunta
  etiqueta: string
  seleccionados: string[]
  onCambiar: (actualizar: (anteriores: string[]) => string[]) => void
}

/**
 * Selección múltiple con un chip "Otros" que abre un campo corto para escribir la opción del negocio (decisión del
 * 2026-11-06). El texto se guarda entre los valores elegidos; así se aprende qué opciones agregar más adelante.
 */
export function SeleccionConOtros({ pregunta, etiqueta, seleccionados, onCambiar }: Props) {
  const id = useId()
  const valores = pregunta.opciones.map((opcion) => opcion.valor)
  const otro = seleccionados.find((valor) => !valores.includes(valor)) ?? ''
  const [abierto, setAbierto] = useState(otro !== '')
  const maximo = pregunta.maxOtro ?? 40

  const cambiarOtro = (texto: string) =>
    onCambiar((anteriores) => {
      const sinOtro = anteriores.filter((valor) => valores.includes(valor))
      return texto.trim() ? [...sinOtro, texto] : sinOtro
    })

  const alternar = () => {
    if (abierto) cambiarOtro('')
    setAbierto(!abierto)
  }

  return (
    <Chips
      etiqueta={etiqueta}
      ayuda={pregunta.ayuda}
      opciones={pregunta.opciones}
      seleccionados={seleccionados}
      onCambiar={onCambiar}
      extra={
        <button
          type="button"
          role="checkbox"
          aria-checked={abierto}
          onClick={alternar}
          className={`min-h-11 rounded-full px-4 py-2 text-sm font-medium ring-1 ring-inset transition ${
            abierto ? 'bg-marca-700 text-white ring-marca-700' : 'bg-white text-stone-700 ring-stone-300 hover:bg-stone-50'
          }`}
        >
          Otros
        </button>
      }
      debajo={
        abierto && (
          <div className="mt-2">
            <label htmlFor={id} className="sr-only">
              {`Otra opción para: ${pregunta.texto}`}
            </label>
            <input
              id={id}
              autoFocus
              maxLength={maximo}
              value={otro}
              onChange={(e) => cambiarOtro(e.target.value)}
              placeholder="Escríbelo en pocas palabras"
              className="block min-h-11 w-full rounded-lg border-0 bg-white px-3 py-2 text-base text-stone-900 ring-1 ring-inset ring-stone-300 placeholder:text-stone-400 focus:ring-2 focus:ring-inset focus:ring-marca-600 sm:text-sm"
            />
            <p className="mt-1 flex justify-between text-xs text-stone-500">
              <span>Una característica, no una oración.</span>
              <span className="tabular-nums">
                {otro.length}/{maximo}
              </span>
            </p>
          </div>
        )
      }
    />
  )
}
