import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { cambiarContrasena, solicitarReinicioContrasena, type Sesion } from '../api/auth'
import {
  actualizarPerfil,
  CLAVE_CONFIGURACION,
  CLAVE_PERFIL,
  obtenerConfiguracion,
  obtenerPerfil,
  type ConfiguracionNicho,
  type Perfil,
} from '../api/negocio'
import { CLAVE_SESION, useCerrarSesion, useSesion } from '../auth/useSesion'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo } from '../componentes/Campo'
import { Cargando } from '../componentes/Cargando'
import { Modal } from '../componentes/Modal'
import {
  borradorDesde,
  cambiosDeMarca,
  cambiosDeRespuestas,
  faltanteDeMarca,
  faltanteDePreguntas,
} from '../onboarding/formularioPerfil'
import { SeccionFotoLocal, SeccionMarca, SeccionPreguntas, type CambioDeBorrador } from '../onboarding/SeccionesPerfil'

/** RF-17 y RF-15: el negocio edita su perfil y su contraseña, o pide una temporal si la olvidó. */
export function PaginaAjustes() {
  const configuracion = useQuery({ queryKey: CLAVE_CONFIGURACION, queryFn: obtenerConfiguracion })
  const perfil = useQuery({
    queryKey: CLAVE_PERFIL,
    queryFn: obtenerPerfil,
    staleTime: 30 * 60_000,
    refetchOnWindowFocus: true,
  })
  const salir = useCerrarSesion()
  const { data: sesion } = useSesion()

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
        {sesion?.administrador ? (
          <p className="text-sm text-stone-600">
            Como administrador no puedes cambiar la contraseña del negocio. Si la olvidó, usa "Reiniciar contraseña" en
            el panel.
          </p>
        ) : (
          <>
            <FormularioContrasena />
            <OlvideMiContrasena correo={sesion?.correo ?? ''} />
          </>
        )}
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

const estiloTexto =
  'block w-full rounded-lg border-0 bg-white px-3 py-2 text-base text-stone-900 ring-1 ring-inset ring-stone-300 placeholder:text-stone-400 focus:ring-2 focus:ring-inset focus:ring-marca-600 sm:text-sm'

const MENSAJE_INICIAL = 'Olvidé mi contraseña. ¿Me envías una temporal, por favor?'

/** Va fuera del formulario de contraseña: le avisa al administrador, que le envía una contraseña temporal. */
function OlvideMiContrasena({ correo }: { correo: string }) {
  const [abierto, setAbierto] = useState(false)
  const [contacto, setContacto] = useState('')
  const [mensaje, setMensaje] = useState(MENSAJE_INICIAL)
  const solicitar = useMutation({
    mutationFn: () => solicitarReinicioContrasena({ contacto: contacto.trim() || undefined, mensaje }),
  })

  const cerrar = () => {
    setAbierto(false)
    solicitar.reset()
    setContacto('')
    setMensaje(MENSAJE_INICIAL)
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setAbierto(true)}
        className="text-sm font-medium text-marca-700 hover:underline"
      >
        ¿Olvidaste tu contraseña?
      </button>
      <Modal titulo="¿Olvidaste tu contraseña?" abierto={abierto} onCerrar={cerrar}>
        {solicitar.isSuccess ? (
          <div className="space-y-4">
            <Aviso tipo="exito">Le avisamos al administrador; te enviará una contraseña temporal.</Aviso>
            <Boton className="w-full" onClick={cerrar}>
              Entendido
            </Boton>
          </div>
        ) : (
          <form
            onSubmit={(evento) => {
              evento.preventDefault()
              solicitar.mutate()
            }}
            className="space-y-4"
          >
            <p className="text-sm text-stone-600">
              Le avisaremos al administrador para que te envíe una contraseña temporal. Con ella entras y creas una
              nueva.
            </p>
            <Campo etiqueta="Tu correo" type="email" value={correo} readOnly disabled />
            <Campo
              etiqueta="WhatsApp (opcional)"
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              maxLength={20}
              value={contacto}
              onChange={(e) => setContacto(e.target.value)}
              ayuda="Si lo escribes, el administrador puede enviarte la contraseña por ahí."
            />
            <div>
              <label htmlFor="mensaje-reinicio" className="mb-1 block text-sm font-medium text-stone-700">
                Mensaje
              </label>
              <textarea
                id="mensaje-reinicio"
                rows={3}
                maxLength={200}
                required
                value={mensaje}
                onChange={(e) => setMensaje(e.target.value)}
                className={estiloTexto}
              />
            </div>
            {solicitar.error && <Aviso tipo="error">{solicitar.error.message}</Aviso>}
            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <Boton type="button" variante="secundario" onClick={cerrar}>
                Cancelar
              </Boton>
              <Boton type="submit" cargando={solicitar.isPending}>
                Avisar al administrador
              </Boton>
            </div>
          </form>
        )}
      </Modal>
    </>
  )
}
