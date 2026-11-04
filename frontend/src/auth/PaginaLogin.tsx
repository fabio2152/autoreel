import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { iniciarSesion } from '../api/auth'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo } from '../componentes/Campo'
import { Cargando } from '../componentes/Cargando'
import { Marca } from '../componentes/Marca'
import { ErrorDeSesion } from './ErrorDeSesion'
import { CLAVE_SESION, rutaInicio, useSesion } from './useSesion'

export function PaginaLogin() {
  const { data: sesion, isPending, error, refetch } = useSesion()
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')

  const entrar = useMutation({
    mutationFn: () => iniciarSesion(correo, contrasena),
    onSuccess: (nuevaSesion) => {
      clienteConsultas.setQueryData(CLAVE_SESION, nuevaSesion)
      navegar(rutaInicio(nuevaSesion), { replace: true })
    },
  })

  if (isPending) return <Cargando />
  if (error) return <ErrorDeSesion mensaje={error.message} onReintentar={() => refetch()} />
  if (sesion) return <Navigate to={rutaInicio(sesion)} replace />

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    entrar.mutate()
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <Marca className="text-xl" />
          <p className="mt-3 text-sm text-stone-600">Tu Reel del día, hecho y publicado en dos minutos.</p>
        </div>
        <form onSubmit={enviar} className="space-y-4 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-stone-200">
          <h1 className="text-lg font-semibold">Inicia sesión</h1>
          <Campo
            etiqueta="Correo"
            type="email"
            autoComplete="email"
            required
            value={correo}
            onChange={(e) => setCorreo(e.target.value)}
          />
          <Campo
            etiqueta="Contraseña"
            type="password"
            autoComplete="current-password"
            required
            value={contrasena}
            onChange={(e) => setContrasena(e.target.value)}
          />
          {entrar.error && <Aviso tipo="error">{entrar.error.message}</Aviso>}
          <Boton type="submit" className="w-full" cargando={entrar.isPending}>
            Entrar
          </Boton>
        </form>
        <p className="mt-6 text-center text-xs text-stone-500">
          ¿Olvidaste tu contraseña? Pide una nueva al administrador de AutoReel.
        </p>
      </div>
    </main>
  )
}
