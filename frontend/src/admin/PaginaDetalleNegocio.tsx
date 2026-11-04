import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import {
  listarProductosDeNegocio,
  obtenerNegocio,
  reactivarNegocio,
  reiniciarContrasena,
  suspenderNegocio,
  type ContrasenaTemporal,
  type NegocioDetalle,
} from '../api/admin'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Cargando } from '../componentes/Cargando'
import { EtiquetaEstadoPago } from '../componentes/EtiquetaEstadoPago'
import { formatearFecha, formatearFechaHora, formatearMonto } from '../componentes/formato'
import { ImagenRemota } from '../componentes/ImagenRemota'
import { ModalContrasenaTemporal } from './ModalContrasenaTemporal'

/** RF-05: detalle del negocio. Los pagos y los costos llegan en la fase 7. */
export function PaginaDetalleNegocio() {
  const id = Number(useParams().id)
  const clienteConsultas = useQueryClient()
  const [credenciales, setCredenciales] = useState<ContrasenaTemporal | null>(null)
  const negocio = useQuery({ queryKey: ['negocios', 'detalle', id], queryFn: () => obtenerNegocio(id) })

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

  if (negocio.isPending) return <Cargando />
  if (negocio.error) return <Aviso tipo="error">{negocio.error.message}</Aviso>

  const { data } = negocio
  const suspendido = data.estado === 'SUSPENDIDO'
  const errorAccion = reiniciar.error ?? suspender.error ?? reactivar.error

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

        <Tarjeta titulo="Suscripción">
          <Dato nombre="Plan">{data.suscripcion.plan}</Dato>
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

      <PerfilDelNegocio perfil={data.perfil} />
      <CatalogoDelNegocio negocioId={id} />

      <VideosDelNegocio videos={data.videos} />

      <div className="grid gap-4 sm:grid-cols-2">
        <Pendiente titulo="Pagos" fase={7} />
        <Pendiente titulo="Costos" fase={7} />
      </div>

      <ModalContrasenaTemporal
        titulo="Nueva contraseña temporal"
        credenciales={credenciales}
        onCerrar={() => setCredenciales(null)}
      />
    </div>
  )
}

function Tarjeta({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-stone-200">
      <h2 className="mb-3 font-semibold">{titulo}</h2>
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

/** RF-05: consumo del mes y últimos videos, para dar soporte. */
function VideosDelNegocio({ videos }: { videos: NegocioDetalle['videos'] }) {
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-stone-200">
      <h2 className="font-semibold">Videos</h2>
      <p className="mt-1 text-sm text-stone-600">
        {videos.delMes === 1 ? '1 video generado' : `${videos.delMes} videos generados`} este mes.
      </p>
      {videos.recientes.length === 0 ? (
        <p className="mt-3 text-sm text-stone-500">Todavía no tiene videos.</p>
      ) : (
        <div className="mt-3 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs text-stone-500 uppercase">
              <tr>
                <th className="py-2 pr-4">Fecha</th>
                <th className="py-2 pr-4">Estado</th>
                <th className="py-2 pr-4">Planos con respaldo</th>
                <th className="py-2">Detalle</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {videos.recientes.map((video) => (
                <tr key={video.id}>
                  <td className="py-2 pr-4 whitespace-nowrap">{formatearFechaHora(video.fechaCreacion)}</td>
                  <td className="py-2 pr-4">{ESTADOS_DEL_VIDEO[video.estado] ?? video.estado}</td>
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

function Pendiente({ titulo, fase }: { titulo: string; fase: number }) {
  return (
    <section className="rounded-xl border border-dashed border-stone-300 p-4">
      <h2 className="font-medium text-stone-700">{titulo}</h2>
      <p className="mt-1 text-sm text-stone-500">Disponible en la fase {fase}.</p>
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
