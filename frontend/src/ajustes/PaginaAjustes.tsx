import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { cambiarContrasena, type Sesion } from '../api/auth'
import {
  actualizarPerfil,
  CLAVE_CONFIGURACION,
  CLAVE_PERFIL,
  obtenerConfiguracion,
  obtenerPerfil,
  type ConfiguracionNicho,
  type Perfil,
} from '../api/negocio'
import { CLAVE_SESION, useCerrarSesion } from '../auth/useSesion'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo } from '../componentes/Campo'
import { Cargando } from '../componentes/Cargando'
import {
  borradorDesde,
  cambiosDeMarca,
  cambiosDeRespuestas,
  faltanteDeMarca,
  faltanteDePreguntas,
} from '../onboarding/formularioPerfil'
import { SeccionFotoLocal, SeccionMarca, SeccionPreguntas, type CambioDeBorrador } from '../onboarding/SeccionesPerfil'

/** RF-17 y RF-15: el negocio edita su perfil y su contraseña. Instagram llega en la fase 6. */
export function PaginaAjustes() {
  const configuracion = useQuery({ queryKey: CLAVE_CONFIGURACION, queryFn: obtenerConfiguracion })
  const perfil = useQuery({
    queryKey: CLAVE_PERFIL,
    queryFn: obtenerPerfil,
    staleTime: 30 * 60_000,
    refetchOnWindowFocus: true,
  })
  const salir = useCerrarSesion()

  return (
    <div className="mx-auto max-w-xl space-y-8">
      <h1 className="text-2xl font-semibold tracking-tight">Ajustes</h1>
      {(configuracion.isPending || perfil.isPending) && <Cargando />}
      {(configuracion.error || perfil.error) && (
        <Aviso tipo="error">{(configuracion.error ?? perfil.error)?.message}</Aviso>
      )}
      {configuracion.data && perfil.data && (
        <FormularioPerfil configuracion={configuracion.data} perfil={perfil.data} />
      )}
      <Seccion titulo="Contraseña">
        <FormularioContrasena />
      </Seccion>
      <Seccion titulo="Instagram">
        <p className="text-sm text-stone-600">Podrás conectar tu cuenta profesional de Instagram muy pronto.</p>
      </Seccion>
      <Boton variante="secundario" className="w-full" onClick={() => salir.mutate()} cargando={salir.isPending}>
        Cerrar sesión
      </Boton>
    </div>
  )
}

function Seccion({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="space-y-4 rounded-2xl bg-white p-5 ring-1 ring-stone-200">
      <h2 className="text-lg font-semibold">{titulo}</h2>
      {children}
    </section>
  )
}

function FormularioPerfil({ configuracion, perfil }: { configuracion: ConfiguracionNicho; perfil: Perfil }) {
  const clienteConsultas = useQueryClient()
  const [borrador, setBorrador] = useState(() => borradorDesde(perfil))
  const [subidas, setSubidas] = useState(0)
  const subiendo = subidas > 0
  const alSubir = (activo: boolean) => setSubidas((n) => n + (activo ? 1 : -1))
  const [faltante, setFaltante] = useState<string | null>(null)
  const [guardado, setGuardado] = useState(false)

  const guardar = useMutation({
    mutationFn: () =>
      actualizarPerfil({
        ...cambiosDeMarca(borrador),
        ...cambiosDeRespuestas(borrador, configuracion.preguntas),
        fotoLocal: borrador.fotoLocal,
      }),
    onSuccess: (nuevo) => {
      clienteConsultas.setQueryData(CLAVE_PERFIL, nuevo)
      clienteConsultas.setQueryData<Sesion | null>(CLAVE_SESION, (actual) =>
        actual ? { ...actual, nombreNegocio: nuevo.nombre } : actual,
      )
      setBorrador(borradorDesde(nuevo))
      setGuardado(true)
    },
  })

  const cambiar = (cambios: CambioDeBorrador) => {
    setGuardado(false)
    setFaltante(null)
    setBorrador((actual) => ({ ...actual, ...(typeof cambios === 'function' ? cambios(actual) : cambios) }))
  }

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    const pendiente = faltanteDeMarca(borrador) ?? faltanteDePreguntas(borrador, configuracion.preguntas)
    if (pendiente) {
      setFaltante(pendiente)
      return
    }
    guardar.mutate()
  }

  return (
    <form onSubmit={enviar} className="space-y-8">
      <Seccion titulo="Tu marca">
        <SeccionMarca
          borrador={borrador}
          onCambiar={cambiar}
          onSubiendo={alSubir}
          estilos={configuracion.estilos}
          deshabilitado={guardar.isPending}
        />
        {perfil.logoSinTransparencia && (
          <Aviso tipo="info">Tu logo no tiene fondo transparente. Se verá mejor si subes una versión transparente.</Aviso>
        )}
      </Seccion>
      <Seccion titulo="Tu negocio">
        <SeccionPreguntas
          borrador={borrador}
          onCambiar={cambiar}
          preguntas={configuracion.preguntas.filter((p) => p.paso === 2)}
        />
      </Seccion>
      <Seccion titulo="Tu toque">
        <SeccionPreguntas
          borrador={borrador}
          onCambiar={cambiar}
          preguntas={configuracion.preguntas.filter((p) => p.paso === 3)}
        />
        <SeccionFotoLocal
          borrador={borrador}
          onCambiar={cambiar}
          onSubiendo={alSubir}
          deshabilitado={guardar.isPending}
        />
      </Seccion>
      <div className="space-y-2">
        {(faltante || guardar.error) && <Aviso tipo="error">{faltante ?? guardar.error?.message}</Aviso>}
        {guardado && <Aviso tipo="exito">Guardamos tu perfil.</Aviso>}
        <Boton type="submit" className="w-full" cargando={guardar.isPending} disabled={subiendo}>
          {subiendo ? 'Subiendo imagen…' : 'Guardar perfil'}
        </Boton>
      </div>
    </form>
  )
}

/** RF-15: cambio de contraseña con la actual. Al guardar, el backend cierra las otras sesiones. */
function FormularioContrasena() {
  const clienteConsultas = useQueryClient()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const cambiar = useMutation({
    mutationFn: () => cambiarContrasena({ contrasenaActual: actual, nueva, confirmacion }),
    onSuccess: (sesion) => {
      clienteConsultas.setQueryData(CLAVE_SESION, sesion)
      setActual('')
      setNueva('')
      setConfirmacion('')
    },
  })
  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    cambiar.mutate()
  }
  return (
    <form onSubmit={enviar} className="space-y-4">
      <Campo etiqueta="Contraseña actual" type="password" autoComplete="current-password" required value={actual} onChange={(e) => setActual(e.target.value)} />
      <Campo
        etiqueta="Contraseña nueva"
        type="password"
        autoComplete="new-password"
        required
        maxLength={64}
        value={nueva}
        onChange={(e) => setNueva(e.target.value)}
        ayuda="Al menos 8 caracteres, con letras y números."
      />
      <Campo etiqueta="Repite la contraseña nueva" type="password" autoComplete="new-password" required maxLength={64} value={confirmacion} onChange={(e) => setConfirmacion(e.target.value)} />
      {cambiar.error && <Aviso tipo="error">{cambiar.error.message}</Aviso>}
      {cambiar.isSuccess && <Aviso tipo="exito">Cambiamos tu contraseña.</Aviso>}
      <Boton type="submit" variante="secundario" cargando={cambiar.isPending}>
        Cambiar contraseña
      </Boton>
    </form>
  )
}
