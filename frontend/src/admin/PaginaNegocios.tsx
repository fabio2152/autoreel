import { useQuery } from '@tanstack/react-query'
import { useDeferredValue, useState } from 'react'
import { Link } from 'react-router'
import { listarNegocios, type ContrasenaTemporal, type EstadoPago } from '../api/admin'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Cargando } from '../componentes/Cargando'
import { EtiquetaEstadoPago } from '../componentes/EtiquetaEstadoPago'
import { formatearFecha, formatearFechaHora } from '../componentes/formato'
import { ModalContrasenaTemporal } from './ModalContrasenaTemporal'
import { ModalCrearNegocio } from './ModalCrearNegocio'

const FILTROS: { valor: EstadoPago | ''; texto: string }[] = [
  { valor: '', texto: 'Todos los estados de pago' },
  { valor: 'AL_DIA', texto: 'Al día' },
  { valor: 'POR_VENCER', texto: 'Por vencer' },
  { valor: 'VENCE_HOY', texto: 'Vence hoy' },
  { valor: 'VENCIDO', texto: 'Vencido' },
]

/** RF-02: lista de negocios con búsqueda por nombre o correo y filtro por estado de pago. */
export function PaginaNegocios() {
  const [busqueda, setBusqueda] = useState('')
  const [estadoPago, setEstadoPago] = useState<EstadoPago | ''>('')
  const [creando, setCreando] = useState(false)
  const [credenciales, setCredenciales] = useState<ContrasenaTemporal | null>(null)
  const busquedaDiferida = useDeferredValue(busqueda.trim())

  const negocios = useQuery({
    queryKey: ['negocios', busquedaDiferida, estadoPago],
    queryFn: () => listarNegocios({ busqueda: busquedaDiferida, estadoPago }),
    placeholderData: (anterior) => anterior,
  })

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Negocios</h1>
          <p className="text-sm text-stone-600">Clientes de AutoReel, ordenados por su próxima fecha de pago.</p>
        </div>
        <Boton onClick={() => setCreando(true)}>Crear negocio</Boton>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row">
        <input
          type="search"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre o correo"
          aria-label="Buscar por nombre o correo"
          className="min-h-11 flex-1 rounded-lg border-0 bg-white px-3 text-base ring-1 ring-inset ring-stone-300 focus:ring-2 focus:ring-marca-600 sm:text-sm"
        />
        <select
          value={estadoPago}
          onChange={(e) => setEstadoPago(e.target.value as EstadoPago | '')}
          aria-label="Filtrar por estado de pago"
          className="min-h-11 rounded-lg border-0 bg-white px-3 text-base ring-1 ring-inset ring-stone-300 focus:ring-2 focus:ring-marca-600 sm:text-sm"
        >
          {FILTROS.map((filtro) => (
            <option key={filtro.valor} value={filtro.valor}>
              {filtro.texto}
            </option>
          ))}
        </select>
      </div>

      {negocios.isPending && <Cargando />}
      {negocios.error && <Aviso tipo="error">{negocios.error.message}</Aviso>}
      {negocios.data && negocios.data.length === 0 && (
        <div className="rounded-2xl border border-dashed border-stone-300 bg-white px-6 py-12 text-center">
          <p className="font-medium">
            {busquedaDiferida || estadoPago ? 'Ningún negocio coincide con la búsqueda.' : 'Todavía no hay negocios.'}
          </p>
          {!busquedaDiferida && !estadoPago && (
            <p className="mt-1 text-sm text-stone-600">Crea el primero para entregarle su acceso.</p>
          )}
        </div>
      )}

      {negocios.data && negocios.data.length > 0 && (
        <>
          {/* Celular: tarjetas */}
          <ul className="space-y-3 md:hidden">
            {negocios.data.map((negocio) => (
              <li key={negocio.id}>
                <Link
                  to={`/admin/negocios/${negocio.id}`}
                  className="block rounded-xl bg-white p-4 ring-1 ring-stone-200 hover:ring-stone-300"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-medium">{negocio.nombre}</p>
                      <p className="truncate text-sm text-stone-500">{negocio.correo}</p>
                    </div>
                    <EtiquetaEstadoPago estado={negocio.estadoPago} />
                  </div>
                  <p className="mt-2 text-sm text-stone-600">
                    Pago: {formatearFecha(negocio.proximaFechaPago)} · Plan {negocio.plan}
                    {negocio.conBeneficio && ' (beneficio)'} · {negocio.modeloVideo}
                    {negocio.estado === 'SUSPENDIDO' && <span className="ml-2 font-medium text-red-700">Suspendido</span>}
                  </p>
                </Link>
              </li>
            ))}
          </ul>

          {/* Escritorio: tabla */}
          <div className="hidden overflow-x-auto rounded-xl bg-white ring-1 ring-stone-200 md:block">
            <table className="min-w-full divide-y divide-stone-200 text-sm">
              <thead className="bg-stone-50 text-left text-xs font-semibold tracking-wide text-stone-500 uppercase">
                <tr>
                  <th className="px-4 py-3">Negocio</th>
                  <th className="px-4 py-3">Nicho</th>
                  <th className="px-4 py-3">Plan</th>
                  <th className="px-4 py-3">Próximo pago</th>
                  <th className="px-4 py-3">Videos del mes</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3">Último acceso</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {negocios.data.map((negocio) => (
                  <tr key={negocio.id} className="hover:bg-stone-50">
                    <td className="px-4 py-3">
                      <Link to={`/admin/negocios/${negocio.id}`} className="font-medium text-marca-700 hover:underline">
                        {negocio.nombre}
                      </Link>
                      <p className="text-stone-500">{negocio.correo}</p>
                    </td>
                    <td className="px-4 py-3 text-stone-700">{negocio.nicho === 'PASTELERIA' ? 'Pastelería' : negocio.nicho}</td>
                    <td className="px-4 py-3 text-stone-700">
                      {negocio.plan}
                      {negocio.conBeneficio && <span className="ml-1 text-xs text-emerald-700">(beneficio)</span>}
                      <p className="text-xs text-stone-500">{negocio.modeloVideo}</p>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-col items-start gap-1">
                        <span>{formatearFecha(negocio.proximaFechaPago)}</span>
                        <EtiquetaEstadoPago estado={negocio.estadoPago} />
                      </div>
                    </td>
                    <td className="px-4 py-3 text-stone-500">
                      {negocio.videosDelMes ?? '—'}
                    </td>
                    <td className="px-4 py-3">
                      {negocio.estado === 'ACTIVO' ? (
                        <span className="text-emerald-700">Activo</span>
                      ) : (
                        <span className="font-medium text-red-700">Suspendido</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-stone-600">{formatearFechaHora(negocio.ultimoAcceso)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <ModalCrearNegocio
        abierto={creando}
        onCerrar={() => setCreando(false)}
        onCreado={(nuevas) => {
          setCreando(false)
          setCredenciales(nuevas)
        }}
      />
      <ModalContrasenaTemporal
        titulo="Negocio creado"
        credenciales={credenciales}
        onCerrar={() => setCredenciales(null)}
      />
    </div>
  )
}
