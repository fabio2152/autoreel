import { Link, NavLink, Outlet } from 'react-router'
import { useCerrarSesion, useSesion } from '../auth/useSesion'
import { Boton } from '../componentes/Boton'
import { Marca } from '../componentes/Marca'

export function LayoutAdmin() {
  const { data: sesion } = useSesion()
  const salir = useCerrarSesion()

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-6">
            <Link to="/admin" aria-label="Inicio del panel">
              <Marca />
            </Link>
            <nav className="hidden sm:block">
              <NavLink
                to="/admin"
                end
                className={({ isActive }) =>
                  `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-marca-50 text-marca-700' : 'text-stone-600 hover:text-stone-900'}`
                }
              >
                Negocios
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-stone-500 md:inline">{sesion?.correo}</span>
            <Boton variante="texto" onClick={() => salir.mutate()} cargando={salir.isPending}>
              Salir
            </Boton>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  )
}
