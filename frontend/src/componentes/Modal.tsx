import { useEffect, useId, useRef, type ReactNode } from 'react'

interface Props {
  titulo: string
  abierto: boolean
  onCerrar: () => void
  children: ReactNode
}

/** Ventana modal accesible basada en <dialog>; en el celular ocupa el ancho de la pantalla. */
export function Modal({ titulo, abierto, onCerrar, children }: Props) {
  const dialogo = useRef<HTMLDialogElement>(null)
  const idTitulo = useId()

  useEffect(() => {
    const elemento = dialogo.current
    if (!elemento) return
    if (abierto && !elemento.open) elemento.showModal()
    if (!abierto && elemento.open) elemento.close()
  }, [abierto])

  return (
    <dialog
      ref={dialogo}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        onCerrar()
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-2xl bg-white p-0 shadow-xl backdrop:bg-stone-900/50"
    >
      {abierto && (
        <div className="p-5 sm:p-6">
          <div className="mb-4 flex items-start justify-between gap-4">
            <h2 id={idTitulo} className="text-lg font-semibold text-stone-900">
              {titulo}
            </h2>
            <button
              type="button"
              onClick={onCerrar}
              className="-m-2 rounded-lg p-2 text-stone-500 hover:bg-stone-100"
              aria-label="Cerrar"
            >
              <span aria-hidden className="block text-xl leading-none">×</span>
            </button>
          </div>
          {children}
        </div>
      )}
    </dialog>
  )
}
