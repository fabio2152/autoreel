import { useId, useRef, useState, type ReactNode } from 'react'
import { subirArchivo, type PropositoArchivo } from '../api/archivos'
import { Aviso } from './Aviso'
import { ErrorDeImagen, normalizarFoto, revisarLogo } from './imagenes'

export interface ImagenSubida {
  clave: string
  /** Vista previa local del archivo subido. */
  vistaPrevia: string
  archivo: Blob
  horizontal: boolean
}

interface Props {
  proposito: PropositoArchivo
  etiqueta: string
  /** URL de la imagen guardada, si ya existe. */
  actual?: string | null
  /** Ejemplo e indicaciones que se muestran al pedir la foto. */
  guia?: ReactNode
  onSubida: (imagen: ImagenSubida) => void
  onQuitar?: () => void
  onSubiendo?: (subiendo: boolean) => void
  /** Proporción de la vista previa: vertical para fotos, cuadrada para el logo. */
  forma?: 'vertical' | 'cuadrada'
  /** Bloquea el subidor (por ejemplo, mientras se guarda el formulario). */
  deshabilitado?: boolean
}

/**
 * Elige una imagen, la prepara en el navegador y la sube directo al almacenamiento. Devuelve la clave temporal,
 * que el perfil o el producto envían al guardar.
 */
export function SubidorDeImagen({
  proposito,
  etiqueta,
  actual,
  guia,
  onSubida,
  onQuitar,
  onSubiendo,
  forma = 'vertical',
  deshabilitado = false,
}: Props) {
  const id = useId()
  const entrada = useRef<HTMLInputElement>(null)
  const [vistaPrevia, setVistaPrevia] = useState<string | null>(null)
  const [progreso, setProgreso] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [horizontal, setHorizontal] = useState(false)

  // La vista previa (object URL) no se revoca aquí: quien recibe la subida puede seguir mostrándola
  // (por ejemplo, al volver a un paso anterior del asistente). El navegador la libera al cerrar la página.
  const ocupado = progreso !== null || deshabilitado

  const elegir = async (archivo: File | undefined) => {
    if (!archivo || ocupado) return
    setError(null)
    setProgreso(0)
    onSubiendo?.(true)
    try {
      const preparado =
        proposito === 'LOGO'
          ? { foto: await revisarLogo(archivo), horizontal: false }
          : await normalizarFoto(archivo)
      const clave = await subirArchivo(proposito, preparado.foto, setProgreso)
      const url = URL.createObjectURL(preparado.foto)
      setVistaPrevia(url)
      setHorizontal(preparado.horizontal)
      onSubida({ clave, vistaPrevia: url, archivo: preparado.foto, horizontal: preparado.horizontal })
    } catch (e) {
      setError(e instanceof ErrorDeImagen || e instanceof Error ? e.message : 'No pudimos subir la imagen.')
    } finally {
      setProgreso(null)
      onSubiendo?.(false)
      if (entrada.current) entrada.current.value = ''
    }
  }

  const mostrada = vistaPrevia ?? actual ?? null
  const proporcion = forma === 'cuadrada' ? 'aspect-square' : 'aspect-[9/16]'
  const fondo = proposito === 'LOGO' ? 'bg-[conic-gradient(#e7e5e4_25%,#fff_0_50%,#e7e5e4_0_75%,#fff_0)] bg-[length:16px_16px]' : 'bg-stone-100'

  return (
    <div className="space-y-2">
      <label htmlFor={id} className="block text-sm font-medium text-stone-700">
        {etiqueta}
      </label>
      <div className="flex gap-3">
        <button
          type="button"
          onClick={() => entrada.current?.click()}
          disabled={ocupado}
          className={`relative w-28 shrink-0 overflow-hidden rounded-xl ring-1 ring-stone-300 ${proporcion} ${fondo} hover:ring-marca-600 disabled:opacity-70`}
          aria-describedby={`${id}-guia`}
        >
          {mostrada ? (
            <img src={mostrada} alt={etiqueta} className={`size-full ${proposito === 'LOGO' ? 'object-contain p-2' : 'object-cover'}`} />
          ) : (
            <span className="flex size-full flex-col items-center justify-center gap-1 text-xs font-medium text-stone-500">
              <span aria-hidden className="text-2xl leading-none">+</span>
              Elegir
            </span>
          )}
          {progreso !== null && (
            <span className="absolute inset-0 flex items-center justify-center bg-white/80 text-sm font-semibold text-marca-700">
              {progreso}%
            </span>
          )}
        </button>
        <div id={`${id}-guia`} className="min-w-0 flex-1 text-xs text-stone-600">
          {guia}
          <div className="mt-2 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={ocupado}
              onClick={() => entrada.current?.click()}
              className="font-semibold text-marca-700 disabled:opacity-50"
            >
              {mostrada ? 'Cambiar' : 'Subir'}
            </button>
            {mostrada && onQuitar && (
              <button
                type="button"
                disabled={ocupado}
                onClick={() => {
                  setVistaPrevia(null)
                  onQuitar()
                }}
                className="font-semibold text-stone-500 disabled:opacity-50"
              >
                Quitar
              </button>
            )}
          </div>
        </div>
      </div>
      <input
        id={id}
        ref={entrada}
        type="file"
        accept={proposito === 'LOGO' ? 'image/png' : 'image/jpeg,image/png,image/webp'}
        className="sr-only"
        disabled={ocupado}
        onChange={(evento) => elegir(evento.target.files?.[0])}
      />
      {horizontal && <Aviso tipo="info">La foto es horizontal: la recortaremos al formato vertical del Reel.</Aviso>}
      {error && <Aviso tipo="error">{error}</Aviso>}
    </div>
  )
}
