import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { obtenerCostos } from '../api/admin'
import { Aviso } from '../componentes/Aviso'
import { Cargando } from '../componentes/Cargando'
import { formatearCosto, formatearMes, hoyEnLima } from '../componentes/formato'

/** RF-07: costo estimado de IA por negocio y por mes (calendario de Lima). */
export function PaginaCostos() {
  const [mes, setMes] = useState(() => hoyEnLima().slice(0, 7))
  const costos = useQuery({
    queryKey: ['costos', mes],
    queryFn: () => obtenerCostos(mes),
    placeholderData: (anterior) => anterior,
  })

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Costos de IA</h1>
          <p className="text-sm text-stone-600">
            Lo que estimamos que cobran los proveedores por los guiones y los clips de cada negocio.
          </p>
        </div>
        <label className="text-sm font-medium text-stone-700">
          Mes
          <input
            type="month"
            value={mes}
            max={hoyEnLima().slice(0, 7)}
            onChange={(e) => e.target.value && setMes(e.target.value)}
            className="mt-1 block min-h-11 rounded-lg border-0 bg-white px-3 text-base ring-1 ring-inset ring-stone-300 focus:ring-2 focus:ring-marca-600 sm:text-sm"
          />
        </label>
      </div>

      {costos.isPending && <Cargando />}
      {costos.error && <Aviso tipo="error">{costos.error.message}</Aviso>}
      {costos.data && (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Cifra nombre={`Total de ${formatearMes(costos.data.mes)}`} valor={formatearCosto(costos.data.total.total)} />
            <Cifra nombre="Clips de video" valor={formatearCosto(costos.data.total.clips)} />
            <Cifra nombre="Guiones" valor={formatearCosto(costos.data.total.guion)} />
            <Cifra nombre="Videos con costo" valor={String(costos.data.total.videos)} />
          </div>

          {costos.data.negocios.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-stone-300 bg-white px-6 py-12 text-center text-sm text-stone-600">
              No hubo costos de IA en {formatearMes(costos.data.mes)}.
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl bg-white ring-1 ring-stone-200">
              <table className="min-w-full divide-y divide-stone-200 text-sm">
                <thead className="bg-stone-50 text-left text-xs font-semibold tracking-wide text-stone-500 uppercase">
                  <tr>
                    <th className="px-4 py-3">Negocio</th>
                    <th className="px-4 py-3 text-right">Videos</th>
                    <th className="px-4 py-3 text-right">Clips</th>
                    <th className="px-4 py-3 text-right">Guiones</th>
                    <th className="px-4 py-3 text-right">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {costos.data.negocios.map((fila) => (
                    <tr key={fila.negocioId} className="hover:bg-stone-50">
                      <td className="px-4 py-3">
                        <Link to={`/admin/negocios/${fila.negocioId}`} className="font-medium text-marca-700 hover:underline">
                          {fila.nombre}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-right">{fila.costo.videos}</td>
                      <td className="px-4 py-3 text-right">{formatearCosto(fila.costo.clips)}</td>
                      <td className="px-4 py-3 text-right">{formatearCosto(fila.costo.guion)}</td>
                      <td className="px-4 py-3 text-right font-semibold">{formatearCosto(fila.costo.total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="text-xs text-stone-500">
            Son estimaciones con el costo de referencia de cada modelo (Ajustes → Integraciones); la factura real del
            proveedor puede variar un poco.
          </p>
        </>
      )}
    </div>
  )
}

function Cifra({ nombre, valor }: { nombre: string; valor: string }) {
  return (
    <div className="rounded-xl bg-white px-4 py-3 ring-1 ring-stone-200">
      <p className="text-xs text-stone-500">{nombre}</p>
      <p className="text-lg font-semibold text-stone-900">{valor}</p>
    </div>
  )
}
