import { esObligatoria, type Pregunta, type Respuestas } from '../api/negocio'
import { Campo } from './Campo'
import { Chips } from './Chips'
import { SeleccionConOtros } from './SeleccionConOtros'

interface Props {
  pregunta: Pregunta
  respuestas: Respuestas
  onCambiar: (id: string, valor: string | ((anteriores: string[]) => string[])) => void
}

/**
 * Muestra una pregunta del paquete de nicho según su tipo. Así el onboarding de un nicho nuevo no requiere
 * pantallas nuevas.
 */
export function CampoPregunta({ pregunta, respuestas, onCambiar }: Props) {
  const obligatoria = esObligatoria(pregunta, respuestas)
  const etiqueta = obligatoria ? pregunta.texto : `${pregunta.texto} (opcional)`
  const valor = respuestas[pregunta.id]

  if (pregunta.tipo === 'SELECCION_MULTIPLE' && pregunta.maxOtro) {
    return (
      <SeleccionConOtros
        pregunta={pregunta}
        etiqueta={etiqueta}
        seleccionados={Array.isArray(valor) ? valor : []}
        onCambiar={(actualizar) => onCambiar(pregunta.id, actualizar)}
      />
    )
  }

  if (pregunta.tipo === 'SELECCION_MULTIPLE') {
    return (
      <Chips
        etiqueta={etiqueta}
        ayuda={pregunta.ayuda}
        opciones={pregunta.opciones}
        seleccionados={Array.isArray(valor) ? valor : []}
        onCambiar={(actualizar) => onCambiar(pregunta.id, actualizar)}
      />
    )
  }

  if (pregunta.tipo === 'TELEFONO') {
    const numero = typeof valor === 'string' ? valor.replace(/^\+51/, '') : ''
    return (
      <div>
        <label htmlFor={`pregunta-${pregunta.id}`} className="mb-1 block text-sm font-medium text-stone-700">
          {etiqueta}
        </label>
        <div className="flex items-stretch overflow-hidden rounded-lg ring-1 ring-inset ring-stone-300 focus-within:ring-2 focus-within:ring-marca-600">
          <span className="flex items-center bg-stone-100 px-3 text-sm text-stone-600">+51</span>
          <input
            id={`pregunta-${pregunta.id}`}
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            placeholder="987 654 321"
            value={numero}
            onChange={(e) => onCambiar(pregunta.id, e.target.value)}
            className="min-h-11 w-full border-0 bg-white px-3 text-base focus:ring-0 sm:text-sm"
          />
        </div>
        {pregunta.ayuda && <p className="mt-1 text-xs text-stone-500">{pregunta.ayuda}</p>}
      </div>
    )
  }

  const texto = typeof valor === 'string' ? valor : ''
  return (
    <Campo
      etiqueta={etiqueta}
      ayuda={
        <>
          {pregunta.ayuda}
          {pregunta.maxLongitud && (
            <span className="float-right tabular-nums">
              {texto.length}/{pregunta.maxLongitud}
            </span>
          )}
        </>
      }
      maxLength={pregunta.maxLongitud ?? undefined}
      value={texto}
      onChange={(e) => onCambiar(pregunta.id, e.target.value)}
    />
  )
}
