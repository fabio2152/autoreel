import { Link, NavLink, Outlet } from 'react-router'
import { useCerrarSesion, useSesion } from '../auth/useSesion'
import { Boton } from '../componentes/Boton'
import { Marca } from '../componentes/Marca'
import { CampanaNotificaciones } from './CampanaNotificaciones'

const SECCIONES = [
  { ruta: '/admin', texto: 'Negocios', fin: true },
  { ruta: '/admin/costos', texto: 'Costos', fin: false },
  { ruta: '/admin/ajustes', texto: 'Ajustes', fin: false },
]

export function LayoutAdmin() {
  const { data: sesion } = useSesion()
  const salir = useCerrarSesion()

  const enlaces = SECCIONES.map((seccion) => (
    <NavLink
      key={seccion.ruta}
      to={seccion.ruta}
      end={seccion.fin}
      className={({ isActive }) =>
        `rounded-md px-3 py-2 text-sm font-medium whitespace-nowrap ${isActive ? 'bg-marca-50 text-marca-700' : 'text-stone-600 hover:text-stone-900'}`
      }
    >
      {seccion.texto}
    </NavLink>
  ))

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-20 border-b border-stone-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-6">
            <Link to="/admin" aria-label="Inicio del panel">
              <Marca />
            </Link>
            <nav aria-label="Secciones del panel" className="hidden gap-1 sm:flex">
              {enlaces}
            </nav>
          </div>
          <div className="flex items-center gap-2">
            <span className="hidden text-sm text-stone-500 md:inline">{sesion?.correo}</span>
            <CampanaNotificaciones />
            <Boton variante="texto" onClick={() => salir.mutate()} cargando={salir.isPending}>
              Salir
            </Boton>
          </div>
        </div>
        <nav aria-label="Secciones del panel" className="flex gap-1 overflow-x-auto px-3 pb-2 sm:hidden">
          {enlaces}
        </nav>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  )
}
