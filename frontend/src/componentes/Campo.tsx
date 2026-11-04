import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'

const estiloControl =
  'block w-full min-h-11 rounded-lg border-0 bg-white px-3 py-2 text-base text-stone-900 ring-1 ring-inset ring-stone-300 placeholder:text-stone-400 focus:ring-2 focus:ring-inset focus:ring-marca-600 sm:text-sm'

interface PropsBase {
  etiqueta: string
  ayuda?: ReactNode
}

export function Campo({ etiqueta, ayuda, ...resto }: PropsBase & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-stone-700">
        {etiqueta}
      </label>
      <input id={id} {...resto} className={estiloControl} />
      {ayuda && <p className="mt-1 text-xs text-stone-500">{ayuda}</p>}
    </div>
  )
}

export function Selector({
  etiqueta,
  ayuda,
  children,
  ...resto
}: PropsBase & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId()
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-stone-700">
        {etiqueta}
      </label>
      <select id={id} {...resto} className={estiloControl}>
        {children}
      </select>
      {ayuda && <p className="mt-1 text-xs text-stone-500">{ayuda}</p>}
    </div>
  )
}
