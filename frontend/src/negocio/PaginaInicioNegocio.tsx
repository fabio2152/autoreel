import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { CLAVE_PRODUCTOS, listarProductos } from '../api/catalogo'
import { useSesion } from '../auth/useSesion'

/** Inicio provisional del negocio: en la fase 3 lo reemplaza el chat del video del día. */
export function PaginaInicioNegocio() {
  const { data: sesion } = useSesion()
  const productos = useQuery({ queryKey: [...CLAVE_PRODUCTOS, 'ACTIVO'], queryFn: () => listarProductos('ACTIVO') })
  const listos = productos.data?.filter((producto) => producto.completo).length ?? 0

  return (
    <div className="mx-auto max-w-md py-6 text-center">
      <h1 className="text-2xl font-semibold tracking-tight">¡Hola, {sesion?.nombreNegocio}!</h1>
      <p className="mt-3 text-stone-600">
        Muy pronto crearás aquí tu Reel del día, respondiendo unas pocas preguntas.
      </p>
      {productos.data && (
        <div className="mt-8 rounded-2xl bg-white p-5 ring-1 ring-stone-200">
          <p className="text-4xl font-semibold text-marca-700">{listos}</p>
          <p className="text-sm text-stone-600">
            {listos === 1 ? 'producto listo para tus videos' : 'productos listos para tus videos'}
          </p>
          <Link to="/catalogo" className="mt-4 inline-block text-sm font-semibold text-marca-700">
            {listos === 0 ? 'Registra tu primer producto' : 'Ver mi catálogo'} →
          </Link>
        </div>
      )}
    </div>
  )
}
