import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { CLAVE_PRODUCTOS, fotoDe, listarProductos, type EstadoProducto } from '../api/catalogo'
import { CLAVE_CONFIGURACION, obtenerConfiguracion } from '../api/negocio'
import { Aviso } from '../componentes/Aviso'
import { Cargando } from '../componentes/Cargando'
import { ImagenRemota } from '../componentes/ImagenRemota'

/** RF-18: catálogo del negocio, con los productos activos o archivados. */
export function PaginaCatalogo() {
  const [estado, setEstado] = useState<EstadoProducto>('ACTIVO')
  const clave = [...CLAVE_PRODUCTOS, estado]
  const productos = useQuery({
    queryKey: clave,
    queryFn: () => listarProductos(estado),
    // Las URLs de las fotos vencen en una hora: se renuevan antes.
    staleTime: 30 * 60_000,
    refetchInterval: 45 * 60_000,
    refetchOnWindowFocus: true,
  })
  const configuracion = useQuery({ queryKey: CLAVE_CONFIGURACION, queryFn: obtenerConfiguracion })
  const nombreTemporada = (id: string) => configuracion.data?.temporadas.find((t) => t.id === id)?.nombre ?? id

  return (
    <div className="space-y-5">
      <div className="flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Catálogo</h1>
          <p className="text-sm text-stone-600">Registra cada producto una vez y elígelo en tus videos.</p>
        </div>
        <Link
          to="/catalogo/nuevo"
          className="inline-flex min-h-11 shrink-0 items-center rounded-lg bg-marca-700 px-4 text-sm font-semibold text-white hover:bg-marca-800"
        >
          Nuevo
        </Link>
      </div>

      <div className="inline-flex rounded-lg bg-stone-100 p-1" role="tablist">
        {(['ACTIVO', 'ARCHIVADO'] as const).map((opcion) => (
          <button
            key={opcion}
            role="tab"
            aria-selected={estado === opcion}
            onClick={() => setEstado(opcion)}
            className={`min-h-9 rounded-md px-4 text-sm font-medium ${estado === opcion ? 'bg-white shadow-sm' : 'text-stone-600'}`}
          >
            {opcion === 'ACTIVO' ? 'Activos' : 'Archivados'}
          </button>
        ))}
      </div>

      {productos.isPending && <Cargando />}
      {productos.error && <Aviso tipo="error">{productos.error.message}</Aviso>}
      {productos.data?.length === 0 && (
        <div className="rounded-2xl border border-dashed border-stone-300 bg-white px-6 py-12 text-center">
          <p className="font-medium">
            {estado === 'ACTIVO' ? 'Todavía no tienes productos.' : 'No tienes productos archivados.'}
          </p>
          {estado === 'ACTIVO' && (
            <p className="mt-1 text-sm text-stone-600">Registra tu producto estrella con tres fotos.</p>
          )}
        </div>
      )}

      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        {productos.data?.map((producto) => {
          const portada = fotoDe(producto, 'ENTERO') ?? producto.fotos[0]
          return (
            <li key={producto.id}>
              <Link
                to={`/catalogo/${producto.id}`}
                className="block overflow-hidden rounded-xl bg-white ring-1 ring-stone-200 hover:ring-stone-300"
              >
                <div className="relative aspect-[4/5] bg-stone-100">
                  {portada && (
                    <ImagenRemota
                      consulta={clave}
                      src={portada.url}
                      alt=""
                      loading="lazy"
                      className="size-full object-cover"
                    />
                  )}
                  {!producto.completo && (
                    <span className="absolute top-2 left-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
                      Faltan fotos
                    </span>
                  )}
                </div>
                <div className="p-3">
                  <p className="line-clamp-2 text-sm font-medium">{producto.nombre}</p>
                  <p className="mt-1 truncate text-xs text-stone-500">
                    {producto.temporadas.map(nombreTemporada).join(' · ')}
                  </p>
                </div>
              </Link>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
