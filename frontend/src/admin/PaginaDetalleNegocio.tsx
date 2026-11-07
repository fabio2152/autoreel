import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import {
  entrarComoNegocio,
  listarBeneficios,
  listarPagos,
  listarProductosDeNegocio,
  obtenerNegocio,
  reactivarNegocio,
  reiniciarContrasena,
  suspenderNegocio,
  terminarBeneficio,
  type ContrasenaTemporal,
  type NegocioDetalle,
} from '../api/admin'
import { CLAVE_SESION } from '../auth/useSesion'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Cargando } from '../componentes/Cargando'
import { EtiquetaEstadoPago } from '../componentes/EtiquetaEstadoPago'
import { formatearCosto, formatearFecha, formatearFechaHora, formatearMonto } from '../componentes/formato'
import { ImagenRemota } from '../componentes/ImagenRemota'
import { ModalContrasenaTemporal } from './ModalContrasenaTemporal'
import { TEXTO_MEDIO } from './medios'
import { ModalCambiarPlan, ModalOtorgarBeneficio, ModalRegistrarPago } from './ModalesSuscripcion'

type Ventana = 'pago' | 'plan' | 'beneficio' | null

/** RF-05: detalle del negocio, con su suscripción, pagos, beneficios, costos, videos, perfil y catálogo. */
export function PaginaDetalleNegocio() {
  const id = Number(useParams().id)
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  const [parametros, setParametros] = useSearchParams()
  const [credenciales, setCredenciales] = useState<ContrasenaTemporal | null>(null)
  // Desde la notificación "Registrar pago" se llega con ?registrarPago=1.
  const [ventana, setVentana] = useState<Ventana>(() => (parametros.get('registrarPago') ? 'pago' : null))
  const negocio = useQuery({ queryKey: ['negocios', 'detalle', id], queryFn: () => obtenerNegocio(id) })

  const cerrarVentana = () => {
    setVentana(null)
    if (parametros.has('registrarPago')) setParametros({}, { replace: true })
  }

  const actualizar = () => clienteConsultas.invalidateQueries({ queryKey: ['negocios'] })

  const reiniciar = useMutation({
    mutationFn: () => reiniciarContrasena(id),
    onSuccess: (nuevas) => {
      setCredenciales(nuevas)
      actualizar()
    },
  })
  const suspender = useMutation({ mutationFn: () => suspenderNegocio(id), onSuccess: actualizar })
  const reactivar = useMutation({ mutationFn: () => reactivarNegocio(id), onSuccess: actualizar })
  const entrar = useMutation({
    mutationFn: () => entrarComoNegocio(id),
    onSuccess: (sesion) => {
      clienteConsultas.clear()
      clienteConsultas.setQueryData(CLAVE_SESION, sesion)
      navegar('/', { replace: true })
    },
  })

  if (negocio.isPending) return <Cargando />
  if (negocio.error) return <Aviso tipo="error">{negocio.error.message}</Aviso>

  const { data } = negocio
  const suspendido = data.estado === 'SUSPENDIDO'
  const errorAccion = reiniciar.error ?? suspender.error ?? reactivar.error ?? entrar.error

  const confirmarReinicio = () => {
    if (window.confirm(`Se generará una nueva contraseña temporal para ${data.nombre}. La actual dejará de funcionar y se cerrará su sesión. ¿Continuar?`)) {
      reiniciar.mutate()
    }
  }
  const confirmarSuspension = () => {
    if (window.confirm(`${data.nombre} no podrá iniciar sesión hasta que lo reactives. ¿Suspender?`)) {
      suspender.mutate()
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/admin" className="text-sm font-medium text-marca-700 hover:underline">
          ← Negocios
        </Link>
        <div className="mt-2 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{data.nombre}</h1>
            <p className="text-sm text-stone-600">
              {data.nombreNicho} · {suspendido ? <span className="font-medium text-red-700">Suspendido</span> : 'Activo'}
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Boton onClick={() => entrar.mutate()} cargando={entrar.isPending} title="Ver AutoReel tal como lo ve este negocio">
              Entrar como el negocio
            </Boton>
            <Boton variante="secundario" onClick={confirmarReinicio} cargando={reiniciar.isPending}>
              Reiniciar contraseña
            </Boton>
            {suspendido ? (
              <Boton variante="secundario" onClick={() => reactivar.mutate()} cargando={reactivar.isPending}>
                Reactivar
              </Boton>
            ) : (
              <Boton variante="peligro" onClick={confirmarSuspension} cargando={suspender.isPending}>
                Suspender
              </Boton>
            )}
          </div>
        </div>
      </div>

      {errorAccion && <Aviso tipo="error">{errorAccion.message}</Aviso>}

      <div className="grid gap-4 lg:grid-cols-2">
        <Tarjeta titulo="Datos">
          <Dato nombre="Correo del usuario">{data.usuario.correo}</Dato>
          <Dato nombre="Creado">{formatearFechaHora(data.fechaCreacion)}</Dato>
          <Dato nombre="Último acceso">{formatearFechaHora(data.usuario.ultimoAcceso)}</Dato>
          <Dato nombre="Onboarding">{data.onboardingCompleto ? 'Completo' : 'Pendiente'}</Dato>
          <Dato nombre="Contraseña">
            {data.usuario.debeCambiarContrasena
              ? `Temporal, vence ${formatearFechaHora(data.usuario.contrasenaTemporalVence)}`
              : 'Propia'}
          </Dato>
          {data.usuario.bloqueado && (
            <Dato nombre="Acceso">
              <span className="text-red-700">Bloqueado 15 minutos por intentos fallidos</span>
            </Dato>
          )}
        </Tarjeta>

        <Tarjeta
          titulo="Suscripción"
          acciones={
            <>
              <Boton variante="secundario" onClick={() => setVentana('plan')}>
                Cambiar plan
              </Boton>
              <Boton onClick={() => setVentana('pago')}>Registrar pago</Boton>
            </>
          }
        >
          <Dato nombre="Plan">
            {data.suscripcion.plan}
            {data.planEfectivo.beneficioHasta && (
              <span className="block text-xs font-normal text-emerald-700">
                Hoy usa {data.planEfectivo.plan} por un beneficio hasta {formatearFechaHora(data.planEfectivo.beneficioHasta)}
              </span>
            )}
          </Dato>
          <Dato nombre="Modelo de video">{data.planEfectivo.modeloVideo}</Dato>
          <Dato nombre="Modelo de texto">{data.planEfectivo.modeloTexto}</Dato>
          <Dato nombre="Límites">
            {data.planEfectivo.videosPorDia === 1 ? '1 video' : `${data.planEfectivo.videosPorDia} videos`} por día ·{' '}
            {data.planEfectivo.regeneracionesPorVideo} regeneraciones por video
          </Dato>
          <Dato nombre="Monto mensual">{formatearMonto(data.suscripcion.montoMensual)}</Dato>
          <Dato nombre="Inicio">{formatearFecha(data.suscripcion.fechaInicio)}</Dato>
          <Dato nombre="Próximo pago">
            <span className="flex flex-wrap items-center gap-2">
              {formatearFecha(data.suscripcion.proximaFechaPago)}
              <EtiquetaEstadoPago estado={data.suscripcion.estadoPago} />
            </span>
          </Dato>
          <Dato nombre="Aviso de pago">{data.suscripcion.diasAviso} días antes</Dato>
        </Tarjeta>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <PagosDelNegocio negocioId={id} />
        <BeneficiosDelNegocio negocioId={id} onOtorgar={() => setVentana('beneficio')} />
      </div>

      <VideosDelNegocio videos={data.videos} costo={data.costoDelMes} />

      <PerfilDelNegocio perfil={data.perfil} />
      <CatalogoDelNegocio negocioId={id} />

      {ventana === 'pago' && <ModalRegistrarPago negocio={data} abierto onCerrar={cerrarVentana} />}
      {ventana === 'plan' && <ModalCambiarPlan negocio={data} abierto onCerrar={cerrarVentana} />}
      {ventana === 'beneficio' && <ModalOtorgarBeneficio negocio={data} abierto onCerrar={cerrarVentana} />}

      <ModalContrasenaTemporal
        titulo="Nueva contraseña temporal"
        credenciales={credenciales}
        onCerrar={() => setCredenciales(null)}
      />
    </div>
  )
}

function Tarjeta({ titulo, acciones, children }: { titulo: string; acciones?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-stone-200">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold">{titulo}</h2>
        {acciones && <div className="flex flex-wrap gap-2">{acciones}</div>}
      </div>
      <dl className="divide-y divide-stone-100 text-sm">{children}</dl>
    </section>
  )
}

function Dato({ nombre, children }: { nombre: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 py-2 sm:flex-row sm:justify-between sm:gap-4">
      <dt className="text-stone-500">{nombre}</dt>
      <dd className="font-medium break-all sm:text-right">{children}</dd>
    </div>
  )
}

const ESTADOS_DEL_VIDEO: Record<string, string> = {
  GUION_PENDIENTE: 'Preparando propuesta',
  STORYBOARD: 'Por revisar (propuesta)',
  GENERANDO: 'Generando clips',
  COMPONIENDO: 'Componiendo',
  LISTO: 'Listo',
  APROBADO: 'Aprobado',
  PROGRAMADO: 'Programado',
  PUBLICADO: 'Publicado',
  ERROR: 'Error',
  DESCARTADO: 'Descartado',
}

/** RF-05, RF-07 y V32: consumo y costo del mes, y últimos videos con su modelo y su costo. */
function VideosDelNegocio({ videos, costo }: { videos: NegocioDetalle['videos']; costo: NegocioDetalle['costoDelMes'] }) {
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-stone-200">
      <h2 className="font-semibold">Videos y costos</h2>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Cifra nombre="Videos este mes" valor={String(videos.delMes)} />
        <Cifra nombre="Costo de IA del mes" valor={formatearCosto(costo.total)} />
        <Cifra nombre="Clips" valor={formatearCosto(costo.clips)} />
        <Cifra nombre="Guiones" valor={formatearCosto(costo.guion)} />
      </div>
      {videos.recientes.length === 0 ? (
        <p className="mt-3 text-sm text-stone-500">Todavía no tiene videos.</p>
      ) : (
        <div className="mt-3 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs text-stone-500 uppercase">
              <tr>
                <th className="py-2 pr-4">Fecha</th>
                <th className="py-2 pr-4">Estado</th>
                <th className="py-2 pr-4">Modelo de video</th>
                <th className="py-2 pr-4">Costo</th>
                <th className="py-2 pr-4">Planos con respaldo</th>
                <th className="py-2">Detalle</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {videos.recientes.map((video) => (
                <tr key={video.id}>
                  <td className="py-2 pr-4 whitespace-nowrap">{formatearFechaHora(video.fechaCreacion)}</td>
                  <td className="py-2 pr-4">{ESTADOS_DEL_VIDEO[video.estado] ?? video.estado}</td>
                  <td className="py-2 pr-4 whitespace-nowrap">{video.modeloVideo}</td>
                  <td className="py-2 pr-4 whitespace-nowrap">{formatearCosto(video.costoEstimado)}</td>
                  <td className="py-2 pr-4">{video.planosConRespaldo}</td>
                  <td className="py-2 text-stone-500">{video.motivoError ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function Cifra({ nombre, valor }: { nombre: string; valor: string }) {
  return (
    <div className="rounded-lg bg-stone-50 px-3 py-2">
      <p className="text-xs text-stone-500">{nombre}</p>
      <p className="font-semibold text-stone-900">{valor}</p>
    </div>
  )
}

/** RF-08: historial de pagos registrados. */
function PagosDelNegocio({ negocioId }: { negocioId: number }) {
  const pagos = useQuery({ queryKey: ['negocios', 'pagos', negocioId], queryFn: () => listarPagos(negocioId) })
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-stone-200">
      <h2 className="mb-3 font-semibold">Pagos</h2>
      {pagos.isPending && <Cargando />}
      {pagos.error && <Aviso tipo="error">{pagos.error.message}</Aviso>}
      {pagos.data?.length === 0 && <p className="text-sm text-stone-500">Todavía no hay pagos registrados.</p>}
      {pagos.data && pagos.data.length > 0 && (
        <ul className="divide-y divide-stone-100 text-sm">
          {pagos.data.map((pago) => (
            <li key={pago.id} className="flex items-start justify-between gap-3 py-2">
              <div>
                <p className="font-medium">{formatearMonto(pago.monto)} · {TEXTO_MEDIO[pago.medio]}</p>
                <p className="text-stone-500">
                  Pagó el {formatearFecha(pago.fechaPago)} · cubre el {formatearFecha(pago.periodoCubierto)}
                </p>
                {pago.nota && <p className="text-stone-500">{pago.nota}</p>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

const ESTADO_BENEFICIO = {
  PROGRAMADO: { texto: 'Programado', estilo: 'bg-sky-50 text-sky-800' },
  VIGENTE: { texto: 'Vigente', estilo: 'bg-emerald-50 text-emerald-800' },
  TERMINADO: { texto: 'Terminado', estilo: 'bg-stone-100 text-stone-600' },
}

/** RF-12: beneficios temporales del negocio. */
function BeneficiosDelNegocio({ negocioId, onOtorgar }: { negocioId: number; onOtorgar: () => void }) {
  const clienteConsultas = useQueryClient()
  const beneficios = useQuery({
    queryKey: ['negocios', 'beneficios', negocioId],
    queryFn: () => listarBeneficios(negocioId),
  })
  const terminar = useMutation({
    mutationFn: (beneficioId: number) => terminarBeneficio(negocioId, beneficioId),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: ['negocios'] }),
  })
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-stone-200">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-semibold">Beneficios</h2>
        <Boton variante="secundario" onClick={onOtorgar}>
          Otorgar beneficio
        </Boton>
      </div>
      {beneficios.isPending && <Cargando />}
      {(beneficios.error ?? terminar.error) && (
        <Aviso tipo="error">{(beneficios.error ?? terminar.error)?.message}</Aviso>
      )}
      {beneficios.data?.length === 0 && (
        <p className="text-sm text-stone-500">
          Sin beneficios. Un beneficio da otro plan por un tiempo, como cortesía o por un inconveniente.
        </p>
      )}
      <ul className="divide-y divide-stone-100 text-sm">
        {beneficios.data?.map((beneficio) => (
          <li key={beneficio.id} className="flex items-start justify-between gap-3 py-2">
            <div>
              <p className="font-medium">
                {beneficio.plan} · {beneficio.modeloVideo}{' '}
                <span className={`ml-1 rounded px-1.5 py-0.5 text-xs ${ESTADO_BENEFICIO[beneficio.estado].estilo}`}>
                  {ESTADO_BENEFICIO[beneficio.estado].texto}
                </span>
              </p>
              <p className="text-stone-500">
                {formatearFechaHora(beneficio.desde)} → {formatearFechaHora(beneficio.hasta)}
              </p>
              <p className="text-stone-500">{beneficio.motivo}</p>
            </div>
            {beneficio.estado !== 'TERMINADO' && (
              <Boton
                variante="texto"
                cargando={terminar.isPending && terminar.variables === beneficio.id}
                onClick={() => {
                  if (window.confirm('¿Terminar este beneficio ahora? El negocio vuelve a su plan.')) terminar.mutate(beneficio.id)
                }}
              >
                Terminar
              </Boton>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

function PerfilDelNegocio({ perfil: { perfil, respuestas } }: { perfil: NegocioDetalle['perfil'] }) {
  return (
    <Tarjeta titulo="Perfil">
      <div className="flex flex-wrap items-center gap-4 py-2">
        {perfil.logo ? (
          <img src={perfil.logo.url} alt="Logo" className="size-16 rounded-lg bg-stone-100 object-contain p-1" />
        ) : (
          <span className="text-sm text-stone-500">Sin logo</span>
        )}
        {perfil.colorPrimario && (
          <span className="flex items-center gap-2 text-sm">
            <span className="size-6 rounded-full ring-1 ring-stone-300" style={{ background: perfil.colorPrimario }} />
            <span className="size-6 rounded-full ring-1 ring-stone-300" style={{ background: perfil.colorSecundario ?? undefined }} />
          </span>
        )}
        {perfil.estilo && <span className="text-sm text-stone-600">Estilo {perfil.estilo.toLowerCase()}</span>}
      </div>
      {respuestas.length === 0 && <p className="py-2 text-sm text-stone-500">Todavía no completó el onboarding.</p>}
      {respuestas.map((respuesta) => (
        <Dato key={respuesta.pregunta} nombre={respuesta.pregunta}>
          {respuesta.respuesta}
        </Dato>
      ))}
    </Tarjeta>
  )
}

function CatalogoDelNegocio({ negocioId }: { negocioId: number }) {
  const clave = ['negocios', 'productos', negocioId]
  const productos = useQuery({ queryKey: clave, queryFn: () => listarProductosDeNegocio(negocioId) })
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-stone-200">
      <h2 className="mb-3 font-semibold">Catálogo</h2>
      {productos.isPending && <Cargando />}
      {productos.error && <Aviso tipo="error">{productos.error.message}</Aviso>}
      {productos.data?.length === 0 && <p className="text-sm text-stone-500">Todavía no registró productos.</p>}
      <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {productos.data?.map((producto) => (
          <li key={producto.id} className="overflow-hidden rounded-lg ring-1 ring-stone-200">
            <div className="aspect-[4/5] bg-stone-100">
              {producto.fotos[0] && (
                <ImagenRemota consulta={clave} src={producto.fotos[0].url} alt="" loading="lazy" className="size-full object-cover" />
              )}
            </div>
            <div className="p-2 text-xs">
              <p className="font-medium text-stone-800">{producto.nombre}</p>
              <p className="text-stone-500">
                {producto.estado === 'ARCHIVADO' ? 'Archivado' : producto.completo ? 'Listo' : 'Faltan fotos'}
              </p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
