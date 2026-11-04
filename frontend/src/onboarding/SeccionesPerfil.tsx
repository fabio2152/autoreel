import { useState } from 'react'
import type { Opcion, Pregunta } from '../api/negocio'
import { Campo } from '../componentes/Campo'
import { CampoPregunta } from '../componentes/CampoPregunta'
import { Chips } from '../componentes/Chips'
import { coloresDelLogo } from '../componentes/imagenes'
import { SubidorDeImagen } from '../componentes/SubidorDeImagen'
import type { BorradorPerfil } from './formularioPerfil'

/** Cambios parciales, o una función que los calcula desde el borrador más reciente. */
export type CambioDeBorrador = Partial<BorradorPerfil> | ((actual: BorradorPerfil) => Partial<BorradorPerfil>)

interface PropsSeccion {
  borrador: BorradorPerfil
  onCambiar: (cambios: CambioDeBorrador) => void
  onSubiendo: (subiendo: boolean) => void
  /** Bloquea las subidas mientras se guarda, para que ninguna se pierda. */
  deshabilitado?: boolean
}

/** Paso 1 del onboarding y primera parte de Ajustes → Perfil: nombre, logo, colores y estilo. */
export function SeccionMarca({
  borrador,
  onCambiar,
  onSubiendo,
  estilos,
  deshabilitado,
}: PropsSeccion & { estilos: Opcion[] }) {
  const [sinColoresDelLogo, setSinColoresDelLogo] = useState(false)

  const tomarDelLogo = async () => {
    if (!borrador.logoArchivo) return
    const colores = await coloresDelLogo(borrador.logoArchivo)
    setSinColoresDelLogo(!colores)
    if (colores) onCambiar({ colorPrimario: colores[0], colorSecundario: colores[1] })
  }

  return (
    <div className="space-y-6">
      <Campo
        etiqueta="Nombre comercial"
        required
        maxLength={120}
        value={borrador.nombre}
        onChange={(e) => onCambiar({ nombre: e.target.value })}
      />
      <SubidorDeImagen
        proposito="LOGO"
        etiqueta="Logo"
        forma="cuadrada"
        actual={borrador.logoUrl}
        onSubiendo={onSubiendo}
        deshabilitado={deshabilitado}
        onSubida={(imagen) => onCambiar({ logo: imagen.clave, logoUrl: imagen.vistaPrevia, logoArchivo: imagen.archivo })}
        guia={<p>PNG con fondo transparente, de hasta 5 MB. Así se verá limpio sobre tus videos.</p>}
      />
      <fieldset>
        <legend className="mb-2 text-sm font-medium text-stone-700">Tus dos colores de marca</legend>
        <div className="flex flex-wrap items-center gap-4">
          <SelectorDeColor
            etiqueta="Principal"
            valor={borrador.colorPrimario}
            onCambiar={(color) => onCambiar({ colorPrimario: color })}
          />
          <SelectorDeColor
            etiqueta="Secundario"
            valor={borrador.colorSecundario}
            onCambiar={(color) => onCambiar({ colorSecundario: color })}
          />
          {borrador.logoArchivo && (
            <button type="button" onClick={tomarDelLogo} className="min-h-11 text-sm font-semibold text-marca-700">
              Tomar del logo
            </button>
          )}
        </div>
        {sinColoresDelLogo && (
          <p className="mt-2 text-xs text-stone-500">No encontramos colores claros en tu logo. Elígelos a mano.</p>
        )}
      </fieldset>
      <Chips
        unica
        etiqueta="Estilo de tus videos"
        ayuda="Define el tono de los textos, la tipografía y la música."
        opciones={estilos}
        seleccionados={borrador.estilo ? [borrador.estilo] : []}
        onCambiar={(actualizar) => onCambiar((actual) => ({ estilo: actualizar(actual.estilo ? [actual.estilo] : [])[0] ?? null }))}
      />
    </div>
  )
}

function SelectorDeColor({ etiqueta, valor, onCambiar }: { etiqueta: string; valor: string; onCambiar: (c: string) => void }) {
  return (
    <label className="flex items-center gap-2 text-sm text-stone-700">
      <input
        type="color"
        value={valor}
        onChange={(e) => onCambiar(e.target.value)}
        className="size-11 cursor-pointer rounded-lg border-0 bg-transparent p-0"
      />
      <span>
        {etiqueta}
        <span className="block font-mono text-xs text-stone-500 uppercase">{valor}</span>
      </span>
    </label>
  )
}

/** Preguntas del paquete de nicho de un paso. */
export function SeccionPreguntas({
  borrador,
  onCambiar,
  preguntas,
}: Omit<PropsSeccion, 'onSubiendo'> & { preguntas: Pregunta[] }) {
  return (
    <div className="space-y-6">
      {preguntas.map((pregunta) => (
        <CampoPregunta
          key={pregunta.id}
          pregunta={pregunta}
          respuestas={borrador.respuestas}
          onCambiar={(id, valor) =>
            onCambiar((actual) => {
              const anterior = actual.respuestas[id]
              const nuevo = typeof valor === 'function' ? valor(Array.isArray(anterior) ? anterior : []) : valor
              return { respuestas: { ...actual.respuestas, [id]: nuevo } }
            })
          }
        />
      ))}
    </div>
  )
}

/** Foto opcional de la vitrina o la fachada: fondo del plano de cierre. */
export function SeccionFotoLocal({ borrador, onCambiar, onSubiendo, deshabilitado }: PropsSeccion) {
  return (
    <SubidorDeImagen
      proposito="FOTO_LOCAL"
      etiqueta="Foto del local (opcional)"
      actual={borrador.fotoLocalUrl}
      onSubiendo={onSubiendo}
      deshabilitado={deshabilitado}
      onSubida={(imagen) => onCambiar({ fotoLocal: imagen.clave, fotoLocalUrl: imagen.vistaPrevia })}
      onQuitar={() => onCambiar({ fotoLocal: null, fotoLocalUrl: null })}
      guia={
        <p>
          Tu vitrina o fachada, en vertical. La usamos de fondo al final del video. Si no la subes, usaremos tus colores.
        </p>
      }
    />
  )
}
