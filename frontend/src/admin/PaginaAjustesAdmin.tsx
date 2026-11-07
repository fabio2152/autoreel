import { useSearchParams } from 'react-router'
import { SeccionIntegraciones } from './SeccionIntegraciones'
import { SeccionPlanes } from './SeccionPlanes'

const SECCIONES = [
  { id: 'integraciones', texto: 'Integraciones y claves' },
  { id: 'planes', texto: 'Planes' },
] as const

type IdSeccion = (typeof SECCIONES)[number]['id']

/** Ajustes del panel (sección 14): integraciones de IA con sus claves (RF-11) y planes (RF-10). */
export function PaginaAjustesAdmin() {
  const [parametros, setParametros] = useSearchParams()
  const seccion: IdSeccion = parametros.get('seccion') === 'planes' ? 'planes' : 'integraciones'

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Ajustes</h1>
        <p className="text-sm text-stone-600">Proveedores de IA, sus claves y los planes que ofreces.</p>
      </div>
      <div role="tablist" aria-label="Secciones de ajustes" className="flex gap-1 border-b border-stone-200">
        {SECCIONES.map((opcion) => (
          <button
            key={opcion.id}
            type="button"
            role="tab"
            aria-selected={seccion === opcion.id}
            onClick={() => setParametros({ seccion: opcion.id }, { replace: true })}
            className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
              seccion === opcion.id
                ? 'border-marca-700 text-marca-700'
                : 'border-transparent text-stone-600 hover:text-stone-900'
            }`}
          >
            {opcion.texto}
          </button>
        ))}
      </div>
      {seccion === 'integraciones' ? <SeccionIntegraciones /> : <SeccionPlanes />}
    </div>
  )
}
