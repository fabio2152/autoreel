import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate } from 'react-router'
import {
  actualizarPerfil,
  CLAVE_CONFIGURACION,
  CLAVE_PERFIL,
  completarOnboarding,
  obtenerConfiguracion,
  obtenerPerfil,
  type CambiosPerfil,
  type ConfiguracionNicho,
  type Perfil,
} from '../api/negocio'
import type { Sesion } from '../api/auth'
import { CLAVE_SESION, useSesion } from '../auth/useSesion'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Cargando } from '../componentes/Cargando'
import { Marca } from '../componentes/Marca'
import {
  borradorDesde,
  cambiosDeMarca,
  cambiosDeRespuestas,
  faltanteDeMarca,
  faltanteDePreguntas,
  type BorradorPerfil,
} from './formularioPerfil'
import { SeccionFotoLocal, SeccionMarca, SeccionPreguntas, type CambioDeBorrador } from './SeccionesPerfil'

const PASOS = ['Tu marca', 'Tu negocio', 'Tu toque'] as const

/** RF-16: onboarding de pantalla completa en tres pasos, antes de usar el resto de la aplicación. */
export function AsistenteOnboarding() {
  const { data: sesion } = useSesion()
  // Solo al entrar: al terminar, la sesión cambia pero se muestra la pantalla final.
  const [yaCompleto] = useState(() => sesion?.onboardingCompleto ?? false)
  const configuracion = useQuery({ queryKey: CLAVE_CONFIGURACION, queryFn: obtenerConfiguracion })
  const perfil = useQuery({ queryKey: CLAVE_PERFIL, queryFn: obtenerPerfil })

  if (yaCompleto) return <Navigate to="/" replace />
  if (configuracion.isPending || perfil.isPending) return <Cargando />
  if (configuracion.error || perfil.error) {
    return (
      <div className="p-4">
        <Aviso tipo="error">{(configuracion.error ?? perfil.error)?.message}</Aviso>
      </div>
    )
  }
  return <Asistente configuracion={configuracion.data} perfil={perfil.data} />
}

function pasoInicial(borrador: BorradorPerfil, configuracion: ConfiguracionNicho): number {
  if (faltanteDeMarca(borrador)) return 0
  if (faltanteDePreguntas(borrador, configuracion.preguntas.filter((p) => p.paso === 2))) return 1
  return 2
}

function Asistente({ configuracion, perfil }: { configuracion: ConfiguracionNicho; perfil: Perfil }) {
  const clienteConsultas = useQueryClient()
  const [borrador, setBorrador] = useState(() => borradorDesde(perfil))
  // Si salió a mitad, retoma en el primer paso con algo pendiente.
  const [paso, setPaso] = useState(() => pasoInicial(borradorDesde(perfil), configuracion))
  const [subiendo, setSubiendo] = useState(false)
  const [faltante, setFaltante] = useState<string | null>(null)
  const [terminado, setTerminado] = useState(false)
  const [avisos, setAvisos] = useState<string[]>([])
  const preguntasDelPaso = (numero: number) => configuracion.preguntas.filter((p) => p.paso === numero)

  const guardar = useMutation({
    mutationFn: async (cambios: CambiosPerfil) => {
      const guardado = await actualizarPerfil(cambios)
      if (paso === PASOS.length - 1) return completarOnboarding()
      return guardado
    },
    onSuccess: (guardado) => {
      clienteConsultas.setQueryData(CLAVE_PERFIL, guardado)
      // Las claves de subida ya se movieron a su lugar definitivo: se usan las del perfil guardado.
      setBorrador((actual) => ({
        ...actual,
        logo: guardado.logo?.clave ?? null,
        fotoLocal: guardado.fotoLocal?.clave ?? null,
        respuestas: guardado.respuestas,
      }))
      setAvisos(guardado.avisos)
      if (guardado.onboardingCompleto) {
        clienteConsultas.setQueryData<Sesion | null>(CLAVE_SESION, (actual) =>
          actual ? { ...actual, onboardingCompleto: true, nombreNegocio: guardado.nombre } : actual,
        )
        setTerminado(true)
      } else {
        setPaso((actual) => actual + 1)
        window.scrollTo({ top: 0 })
      }
    },
  })

  const cambiar = (cambios: CambioDeBorrador) => {
    setFaltante(null)
    setBorrador((actual) => ({ ...actual, ...(typeof cambios === 'function' ? cambios(actual) : cambios) }))
  }

  const siguiente = () => {
    const pendiente =
      paso === 0 ? faltanteDeMarca(borrador) : faltanteDePreguntas(borrador, preguntasDelPaso(paso + 1))
    if (pendiente) {
      setFaltante(pendiente)
      return
    }
    const cambios: CambiosPerfil =
      paso === 0
        ? cambiosDeMarca(borrador)
        : paso === 1
          ? cambiosDeRespuestas(borrador, preguntasDelPaso(2))
          : { ...cambiosDeRespuestas(borrador, preguntasDelPaso(3)), fotoLocal: borrador.fotoLocal }
    guardar.mutate(cambios)
  }

  if (terminado) {
    return (
      <PantallaFinal
        sinTransparencia={avisos.includes('LOGO_SIN_TRANSPARENCIA') || perfil.logoSinTransparencia}
      />
    )
  }

  return (
    <div className="flex min-h-dvh flex-col bg-white">
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-white/95 px-4 pt-4 pb-3 backdrop-blur">
        <div className="mx-auto max-w-xl">
          <div className="flex items-center justify-between">
            <Marca />
            <span className="text-sm text-stone-500">
              Paso {paso + 1} de {PASOS.length}
            </span>
          </div>
          <div className="mt-3 flex gap-1.5" aria-hidden>
            {PASOS.map((nombre, i) => (
              <span key={nombre} className={`h-1.5 flex-1 rounded-full ${i <= paso ? 'bg-marca-600' : 'bg-stone-200'}`} />
            ))}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-xl flex-1 px-4 py-6">
        <h1 className="text-2xl font-semibold tracking-tight">{PASOS[paso]}</h1>
        <p className="mt-1 mb-6 text-sm text-stone-600">
          {paso === 0 && 'Así se verán tus videos.'}
          {paso === 1 && 'Con esto escribimos los textos y el cierre de cada video.'}
          {paso === 2 && 'Opcional: ayúdanos a que tus videos suenen a ti.'}
        </p>

        {paso === 0 && (
          <SeccionMarca borrador={borrador} onCambiar={cambiar} onSubiendo={setSubiendo} estilos={configuracion.estilos} />
        )}
        {paso === 1 && <SeccionPreguntas borrador={borrador} onCambiar={cambiar} preguntas={preguntasDelPaso(2)} />}
        {paso === 2 && (
          <div className="space-y-6">
            <SeccionPreguntas borrador={borrador} onCambiar={cambiar} preguntas={preguntasDelPaso(3)} />
            <SeccionFotoLocal borrador={borrador} onCambiar={cambiar} onSubiendo={setSubiendo} />
          </div>
        )}

        <p className="mt-8 rounded-lg bg-stone-50 px-3 py-2 text-xs text-stone-500">
          Podrás cambiar estos datos cuando quieras en Ajustes.
        </p>
      </main>

      <footer className="sticky bottom-0 border-t border-stone-200 bg-white px-4 py-3">
        <div className="mx-auto max-w-xl space-y-2">
          {(faltante || guardar.error) && <Aviso tipo="error">{faltante ?? guardar.error?.message}</Aviso>}
          <div className="flex gap-3">
            {paso > 0 && (
              <Boton variante="secundario" onClick={() => setPaso(paso - 1)} disabled={guardar.isPending}>
                Atrás
              </Boton>
            )}
            <Boton className="flex-1" onClick={siguiente} cargando={guardar.isPending} disabled={subiendo}>
              {subiendo ? 'Subiendo imagen…' : paso === PASOS.length - 1 ? 'Terminar' : 'Siguiente'}
            </Boton>
          </div>
        </div>
      </footer>
    </div>
  )
}

/** Sección 6.2, paso 4: sugiere registrar los primeros productos antes de llegar al chat. */
function PantallaFinal({ sinTransparencia }: { sinTransparencia: boolean }) {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-6 px-4 py-10 text-center">
      <div>
        <p className="text-5xl" aria-hidden>
          🎉
        </p>
        <h1 className="mt-4 text-2xl font-semibold tracking-tight">¡Tu perfil está listo!</h1>
        <p className="mt-2 text-stone-600">
          Ahora registra tus productos con tres fotos cada uno. Solo lo haces una vez y luego eliges cuál mostrar en
          cada video.
        </p>
      </div>
      {sinTransparencia && (
        <Aviso tipo="info">
          Tu logo no tiene fondo transparente. Funciona, pero se verá mejor si subes una versión transparente en Ajustes.
        </Aviso>
      )}
      <div className="flex flex-col gap-2">
        <Link
          to="/catalogo/nuevo"
          className="inline-flex min-h-11 items-center justify-center rounded-lg bg-marca-700 px-4 font-semibold text-white hover:bg-marca-800"
        >
          Registrar mi primer producto
        </Link>
        <Link
          to="/"
          className="inline-flex min-h-11 items-center justify-center rounded-lg px-4 font-semibold text-stone-700 hover:bg-stone-100"
        >
          Más tarde
        </Link>
      </div>
    </main>
  )
}
