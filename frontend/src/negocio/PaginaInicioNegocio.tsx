import { useCerrarSesion, useSesion } from '../auth/useSesion'
import { Boton } from '../componentes/Boton'
import { Marca } from '../componentes/Marca'

/** Inicio provisional del negocio: en la fase 2 lo reemplaza el onboarding y en la fase 3 el chat. */
export function PaginaInicioNegocio() {
  const { data: sesion } = useSesion()
  const salir = useCerrarSesion()

  return (
    <div className="min-h-dvh">
      <header className="flex h-14 items-center justify-between border-b border-stone-200 bg-white px-4">
        <Marca />
        <Boton variante="texto" onClick={() => salir.mutate()} cargando={salir.isPending}>
          Salir
        </Boton>
      </header>
      <main className="mx-auto max-w-md px-4 py-12 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">¡Hola, {sesion?.nombreNegocio}!</h1>
        <p className="mt-3 text-stone-600">
          Tu cuenta está lista. Muy pronto podrás completar el perfil de tu negocio y crear tu primer Reel desde aquí.
        </p>
      </main>
    </div>
  )
}
