import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'

export function ErrorDeSesion({ mensaje, onReintentar }: { mensaje: string; onReintentar: () => void }) {
  return (
    <main className="mx-auto flex min-h-dvh max-w-sm flex-col justify-center gap-4 px-4">
      <Aviso tipo="error">{mensaje}</Aviso>
      <Boton onClick={onReintentar}>Reintentar</Boton>
    </main>
  )
}
