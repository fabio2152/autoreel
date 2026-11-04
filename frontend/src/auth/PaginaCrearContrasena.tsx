import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { cambiarContrasena } from '../api/auth'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo } from '../componentes/Campo'
import { Cargando } from '../componentes/Cargando'
import { Marca } from '../componentes/Marca'
import { CLAVE_SESION, rutaInicio, useCerrarSesion, useSesion } from './useSesion'

/** RN-03 y RN-04: quien entra con la contraseña temporal solo puede crear su contraseña propia. */
export function PaginaCrearContrasena() {
  const { data: sesion, isPending } = useSesion()
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  const salir = useCerrarSesion()
  const [nueva, setNueva] = useState('')
  const [confirmacion, setConfirmacion] = useState('')

  const guardar = useMutation({
    mutationFn: () => cambiarContrasena({ nueva, confirmacion }),
    onSuccess: (nuevaSesion) => {
      clienteConsultas.setQueryData(CLAVE_SESION, nuevaSesion)
      navegar(rutaInicio(nuevaSesion), { replace: true })
    },
  })

  if (isPending) return <Cargando />
  if (!sesion) return <Navigate to="/login" replace />
  if (!sesion.debeCambiarContrasena) return <Navigate to={rutaInicio(sesion)} replace />

  const tieneLongitud = nueva.length >= 8
  const tieneLetra = /\p{L}/u.test(nueva)
  const tieneNumero = /\d/.test(nueva)
  const coinciden = nueva.length > 0 && nueva === confirmacion

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    guardar.mutate()
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <Marca className="text-xl" />
        </div>
        <form onSubmit={enviar} className="space-y-4 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-stone-200">
          <div>
            <h1 className="text-lg font-semibold">Crea tu contraseña</h1>
            <p className="mt-1 text-sm text-stone-600">
              Entraste con una contraseña temporal. Elige una propia para {sesion.correo}.
            </p>
          </div>
          <Campo
            etiqueta="Contraseña nueva"
            type="password"
            autoComplete="new-password"
            required
            maxLength={64}
            value={nueva}
            onChange={(e) => setNueva(e.target.value)}
          />
          <Campo
            etiqueta="Repite la contraseña"
            type="password"
            autoComplete="new-password"
            required
            maxLength={64}
            value={confirmacion}
            onChange={(e) => setConfirmacion(e.target.value)}
          />
          <ul className="space-y-1 text-sm" aria-label="Requisitos de la contraseña">
            <Requisito cumplido={tieneLongitud}>Al menos 8 caracteres</Requisito>
            <Requisito cumplido={tieneLetra}>Al menos una letra</Requisito>
            <Requisito cumplido={tieneNumero}>Al menos un número</Requisito>
            <Requisito cumplido={coinciden}>Las dos contraseñas coinciden</Requisito>
          </ul>
          {guardar.error && <Aviso tipo="error">{guardar.error.message}</Aviso>}
          <Boton type="submit" className="w-full" cargando={guardar.isPending}>
            Guardar contraseña
          </Boton>
          <Boton type="button" variante="texto" className="w-full" onClick={() => salir.mutate()}>
            Salir
          </Boton>
        </form>
      </div>
    </main>
  )
}

function Requisito({ cumplido, children }: { cumplido: boolean; children: string }) {
  return (
    <li className={`flex items-center gap-2 ${cumplido ? 'text-emerald-700' : 'text-stone-500'}`}>
      <span aria-hidden>{cumplido ? '✓' : '○'}</span>
      {children}
      <span className="sr-only">{cumplido ? '(cumplido)' : '(pendiente)'}</span>
    </li>
  )
}
