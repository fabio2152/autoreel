import { useState } from 'react'
import { NOMBRE_FOTO, TIPOS_FOTO, type TipoFoto } from '../api/catalogo'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo } from '../componentes/Campo'
import { Modal } from '../componentes/Modal'
import { SubidorDeImagen } from '../componentes/SubidorDeImagen'
import { GuiaDeFoto } from '../catalogo/GuiaDeFoto'

export interface ProductoNuevo {
  nombre: string
  descripcion: string
  fotos: Partial<Record<TipoFoto, string>>
  guardarEnCatalogo: boolean
}

interface Props {
  abierto: boolean
  enviando: boolean
  error: string | null
  onCerrar: () => void
  onUsar: (producto: ProductoNuevo) => void
}

/**
 * "Subir uno nuevo hoy" (sección 7.3): nombre, descripción y las tres fotos. Al final, el usuario decide de forma
 * explícita si lo guarda en su catálogo o lo usa solo en este video (RN-14).
 */
export function HojaProductoNuevo({ abierto, enviando, error, onCerrar, onUsar }: Props) {
  const [nombre, setNombre] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [fotos, setFotos] = useState<Partial<Record<TipoFoto, string>>>({})
  const [subiendo, setSubiendo] = useState(0)
  const [faltante, setFaltante] = useState<string | null>(null)

  const usar = (guardarEnCatalogo: boolean) => {
    if (!nombre.trim() || !descripcion.trim()) {
      setFaltante('Escribe el nombre y la descripción del producto.')
      return
    }
    if (TIPOS_FOTO.some((tipo) => !fotos[tipo])) {
      setFaltante('Sube las tres fotos: entero, detalle y porción.')
      return
    }
    setFaltante(null)
    onUsar({ nombre, descripcion, fotos, guardarEnCatalogo })
  }

  const ocupado = enviando || subiendo > 0

  return (
    <Modal titulo="Subir un producto nuevo" abierto={abierto} onCerrar={onCerrar}>
      <div className="space-y-5">
        <Campo etiqueta="Nombre" maxLength={50} value={nombre} onChange={(e) => setNombre(e.target.value)} />
        <div>
          <label htmlFor="descripcion-nuevo" className="mb-1 block text-sm font-medium text-stone-700">
            Descripción
          </label>
          <textarea
            id="descripcion-nuevo"
            rows={2}
            maxLength={200}
            value={descripcion}
            onChange={(e) => setDescripcion(e.target.value)}
            placeholder="Sabor, relleno, ingredientes destacados…"
            className="block w-full rounded-lg border-0 bg-white px-3 py-2 text-base ring-1 ring-inset ring-stone-300 focus:ring-2 focus:ring-marca-600 sm:text-sm"
          />
        </div>
        {TIPOS_FOTO.map((tipo) => (
          <SubidorDeImagen
            key={tipo}
            proposito="FOTO_PRODUCTO"
            etiqueta={NOMBRE_FOTO[tipo]}
            guia={<GuiaDeFoto tipo={tipo} />}
            deshabilitado={enviando}
            onSubiendo={(activo) => setSubiendo((n) => n + (activo ? 1 : -1))}
            onSubida={(imagen) => setFotos((actual) => ({ ...actual, [tipo]: imagen.clave }))}
          />
        ))}
        {(faltante || error) && <Aviso tipo="error">{faltante ?? error}</Aviso>}
        <div className="space-y-2 border-t border-stone-200 pt-4">
          <p className="text-sm font-medium text-stone-700">¿Lo guardo en tu catálogo?</p>
          <Boton className="w-full" onClick={() => usar(true)} disabled={ocupado} cargando={enviando}>
            Sí, usarlo y guardarlo en mi catálogo
          </Boton>
          <Boton variante="secundario" className="w-full" onClick={() => usar(false)} disabled={ocupado}>
            No, usarlo solo para este video
          </Boton>
          <Boton variante="texto" className="w-full" onClick={onCerrar} disabled={enviando}>
            Cancelar
          </Boton>
        </div>
      </div>
    </Modal>
  )
}
