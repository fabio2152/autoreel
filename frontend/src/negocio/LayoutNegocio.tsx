import { NavLink, Outlet } from 'react-router'
import { Marca } from '../componentes/Marca'
import { AvisoVideoListo } from '../videos/AvisoVideoListo'

const SECCIONES = [
  { ruta: '/', texto: 'Inicio', icono: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z' },
  { ruta: '/videos', texto: 'Mis videos', icono: 'M4 6h11a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1Zm12 4 5-3v10l-5-3' },
  { ruta: '/catalogo', texto: 'Catálogo', icono: 'M4 5h7v7H4zM13 5h7v7h-7zM4 14h7v6H4zM13 14h7v6h-7z' },
  {
    ruta: '/ajustes',
    texto: 'Ajustes',
    icono: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm7.4-3a7.4 7.4 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7 7 0 0 0-2-1.2L14.5 3h-5l-.4 2.6a7 7 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a7.4 7.4 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a7 7 0 0 0 2 1.2l.4 2.6h5l.4-2.6a7 7 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.1-.4.1-.8.1-1.2Z',
  },
]

/** Navegación del negocio: barra inferior en el celular (RNF-03) y superior en pantallas grandes. */
export function LayoutNegocio() {
  const estilo = ({ isActive }: { isActive: boolean }) =>
    `flex flex-1 flex-col items-center justify-center gap-0.5 text-xs font-medium md:flex-none md:flex-row md:gap-2 md:rounded-md md:px-3 md:py-2 md:text-sm ${
      isActive ? 'text-marca-700 md:bg-marca-50' : 'text-stone-500 hover:text-stone-900'
    }`

  const enlaces = SECCIONES.map((seccion) => (
    <NavLink key={seccion.ruta} to={seccion.ruta} end={seccion.ruta === '/'} className={estilo}>
      <svg viewBox="0 0 24 24" className="size-6 md:size-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
        <path d={seccion.icono} strokeLinejoin="round" />
      </svg>
      {seccion.texto}
    </NavLink>
  ))

  return (
    <div className="min-h-dvh pb-16 md:pb-0">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Marca />
          <nav aria-label="Secciones" className="hidden gap-1 md:flex">
            {enlaces}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <AvisoVideoListo />
        <Outlet />
      </main>
      {/* Fuera de la cabecera: su desenfoque haría que "fixed" se ubique dentro de ella y no al pie. */}
      <nav
        aria-label="Secciones"
        className="fixed inset-x-0 bottom-0 z-20 flex h-16 border-t border-stone-200 bg-white pb-[env(safe-area-inset-bottom)] md:hidden"
      >
        {enlaces}
      </nav>
    </div>
  )
}
