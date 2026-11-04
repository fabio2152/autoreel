import { useState } from 'react'
import { Link } from 'react-router'
import type { ProductoCarrusel } from '../api/chat'
import { Boton } from '../componentes/Boton'

interface Props {
  productos: ProductoCarrusel[]
  incompletos: number
  deshabilitado: boolean
  onElegir: (producto: ProductoCarrusel) => void
  onSubirNuevo: () => void
}

/** RF-20: carrusel del catálogo (solo productos con sus tres fotos) o subir uno nuevo hoy. */
export function SelectorDeProducto({ productos, incompletos, deshabilitado, onElegir, onSubirNuevo }: Props) {
  const [elegido, setElegido] = useState<ProductoCarrusel | null>(null)

  return (
    <div className="space-y-3">
      {productos.length > 0 && (
        <ul className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-1" aria-label="Productos de tu catálogo">
          {productos.map((producto) => {
            const activo = elegido?.id === producto.id
            return (
              <li key={producto.id} className="w-32 shrink-0 snap-start">
                <button
                  type="button"
                  aria-pressed={activo}
                  disabled={deshabilitado}
                  onClick={() => setElegido(producto)}
                  className={`block w-full overflow-hidden rounded-xl bg-white text-left ring-2 transition ${
                    activo ? 'ring-marca-600' : 'ring-transparent ring-offset-0 hover:ring-stone-300'
                  }`}
                >
                  <div className="aspect-[4/5] bg-stone-100">
                    {producto.foto && <img src={producto.foto} alt="" className="size-full object-cover" />}
                  </div>
                  <p className="line-clamp-2 p-2 text-xs font-medium">{producto.nombre}</p>
                </button>
              </li>
            )
          })}
        </ul>
      )}
      {incompletos > 0 && (
        <p className="text-xs text-stone-500">
          {incompletos === 1
            ? '1 producto no aparece porque le faltan fotos.'
            : `${incompletos} productos no aparecen porque les faltan fotos.`}{' '}
          <Link to="/catalogo" className="font-semibold text-marca-700">
            Completar
          </Link>
        </p>
      )}
      <div className="flex flex-col gap-2 sm:flex-row">
        {productos.length > 0 && (
          <Boton
            className="flex-1"
            disabled={!elegido || deshabilitado}
            onClick={() => elegido && onElegir(elegido)}
          >
            {elegido ? `Usar ${elegido.nombre}` : 'Elige un producto'}
          </Boton>
        )}
        <Boton variante="secundario" className="flex-1" disabled={deshabilitado} onClick={onSubirNuevo}>
          Subir uno nuevo hoy
        </Boton>
      </div>
    </div>
  )
}
