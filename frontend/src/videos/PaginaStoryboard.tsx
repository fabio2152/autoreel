import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import type { TipoFoto } from '../api/catalogo'
import { ErrorApi } from '../api/cliente'
import {
  aprobarStoryboard,
  aprobarVideo,
  CLAVE_VIDEOS,
  claveVideo,
  contarPalabras,
  descartarVideo,
  editarPlano,
  enlaceDeDescarga,
  obtenerVideo,
  regenerarPlano,
  type CambioDePlano,
  type CierreVista,
  type PlanoVista,
  type Video,
} from '../api/videos'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Cargando } from '../componentes/Cargando'
import { ImagenRemota } from '../componentes/ImagenRemota'
import { Modal } from '../componentes/Modal'
import { SubidorDeImagen } from '../componentes/SubidorDeImagen'
import { CLAVE_AVISOS } from './avisos'
import { PantallaGenerando, Reproductor } from './Generacion'

/** A partir de este tiempo esperando el guion, se avisa y se consulta con menos frecuencia. */
const ESPERA_LARGA_MS = 60_000

const NOMBRES_FOTO: Record<TipoFoto, string> = { ENTERO: 'Entero', DETALLE: 'Detalle', PORCION: 'Porción' }

/** Igual que ValidadorDeGuion.MAXIMO_CARACTERES_TEXTO del backend. */
const MAXIMO_CARACTERES_TEXTO = 200

const ENLACE_PEQUENO = 'inline-flex min-h-11 items-center text-xs font-semibold text-marca-700'
const CAMPO =
  'w-full rounded-lg border border-stone-300 px-3 py-2 text-base focus:border-marca-600 focus:outline-none sm:text-sm'

/**
 * El video en todas sus etapas: la propuesta (storyboard, RF-21 y RF-22), la generación (RF-23), la revisión con el
 * video final (RF-24) y la descarga (RF-25).
 */
export function PaginaStoryboard() {
  const id = Number(useParams().id)
  const clave = claveVideo(id)
  const consulta = useQuery({
    queryKey: clave,
    queryFn: () => obtenerVideo(id),
    enabled: Number.isInteger(id) && id > 0,
    refetchInterval: (query) => {
      const video = query.state.data
      if (video?.estado === 'GENERANDO' || video?.estado === 'COMPONIENDO') return 5000
      if (video?.estado !== 'GUION_PENDIENTE') return false
      return query.state.dataUpdatedAt - Date.parse(video.fechaCreacion) > ESPERA_LARGA_MS ? 5000 : 2000
    },
  })

  if (!Number.isInteger(id) || id <= 0) {
    return (
      <div className="mx-auto max-w-md space-y-3">
        <Aviso tipo="error">No encontramos ese video.</Aviso>
        <Link to="/videos" className="text-sm font-semibold text-marca-700">
          Ver mis videos
        </Link>
      </div>
    )
  }
  if (consulta.isPending) return <Cargando />
  // Si falla una consulta posterior, se sigue mostrando lo que ya había para no perder lo que se está editando.
  if (consulta.isError && consulta.data === undefined) {
    return (
      <div className="mx-auto max-w-md space-y-3">
        <Aviso tipo="error">{consulta.error.message}</Aviso>
        <Boton variante="secundario" onClick={() => consulta.refetch()}>
          Reintentar
        </Boton>
      </div>
    )
  }

  const video = consulta.data
  if (!video) return <Cargando />
  if (video.estado === 'GUION_PENDIENTE') {
    const tardando = consulta.dataUpdatedAt - Date.parse(video.fechaCreacion) > ESPERA_LARGA_MS
    return (
      <div className="mx-auto max-w-md space-y-4 py-8 text-center">
        <Cargando texto="Estoy preparando tu propuesta…" />
        {tardando && (
          <Aviso>Está tardando más de lo normal. Puedes salir: tu propuesta te esperará en Mis videos.</Aviso>
        )}
      </div>
    )
  }
  return (
    <>
      {consulta.isError && (
        <div className="mx-auto mb-4 max-w-xl">
          <Aviso tipo="error">No pudimos actualizar la propuesta. Revisa tu conexión.</Aviso>
        </div>
      )}
      {video.estado === 'GENERANDO' || video.estado === 'COMPONIENDO' ? (
        <PantallaGenerando key={video.id} video={video} />
      ) : (
        <Storyboard key={video.id} video={video} />
      )}
    </>
  )
}

function Storyboard({ video }: { video: Video }) {
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  const clave = claveVideo(video.id)
  const enRevision = video.estado === 'LISTO'
  const editable = video.estado === 'STORYBOARD' || enRevision
  const [confirmarDescarte, setConfirmarDescarte] = useState(false)

  const guardar = (nuevo: Video) => {
    clienteConsultas.setQueryData(clave, nuevo)
    clienteConsultas.invalidateQueries({ queryKey: CLAVE_VIDEOS, exact: true })
    // Si el video empezó a generarse, el aviso de "listo" empieza a vigilarlo.
    clienteConsultas.invalidateQueries({ queryKey: CLAVE_AVISOS })
  }
  const descartar = useMutation({
    mutationFn: () => descartarVideo(video.id),
    onSuccess: (nuevo) => {
      guardar(nuevo)
      navegar('/videos')
    },
  })
  const crear = useMutation({ mutationFn: () => aprobarStoryboard(video.id), onSuccess: guardar })
  const aprobar = useMutation({ mutationFn: () => aprobarVideo(video.id), onSuccess: guardar })
  const descargar = useMutation({
    mutationFn: () => enlaceDeDescarga(video.id),
    onSuccess: (url) => window.location.assign(url),
  })
  const { videosHoy } = video.generacion

  return (
    <div className="mx-auto max-w-xl space-y-5">
      <header>
        <p className="text-sm text-stone-500">{video.tipoVideo}</p>
        <h1 className="text-xl font-semibold text-stone-900">{video.producto.nombre}</h1>
        <p className="mt-1 text-sm text-stone-600">
          {enRevision
            ? 'Tu video está listo. Míralo y, si quieres, cambia un texto o pide otra versión de un momento.'
            : video.estado === 'APROBADO'
              ? 'Aprobaste tu video. Ya puedes descargarlo.'
              : 'Revisa los cuatro momentos de tu video de 15 segundos. Puedes cambiar los textos y las fotos.'}
        </p>
      </header>

      {(enRevision || video.estado === 'APROBADO') && <Reproductor video={video} />}
      {video.generacion.avisoRevision && enRevision && <Aviso tipo="error">{video.generacion.avisoRevision}</Aviso>}
      {video.estado === 'DESCARTADO' && <Aviso>Descartaste esta propuesta.</Aviso>}
      {video.estado === 'ERROR' && (
        <Aviso tipo="error">
          {video.generacion.motivoError ?? 'No pudimos preparar este video.'} Crea otro desde el inicio.
        </Aviso>
      )}
      {video.avisoDeRespaldo && video.estado === 'STORYBOARD' && <Aviso>{video.avisoDeRespaldo}</Aviso>}

      <ol className="space-y-4">
        {video.planos.map((plano) => (
          <li key={plano.orden}>
            <TarjetaPlano video={video} plano={plano} editable={editable} enRevision={enRevision} onGuardado={guardar} />
          </li>
        ))}
        {video.cierre && (
          <li>
            <TarjetaCierre video={video} cierre={video.cierre} editable={editable} onGuardado={guardar} />
          </li>
        )}
      </ol>

      {video.descripcion && (
        <section className="rounded-2xl bg-white p-4 ring-1 ring-stone-200">
          <h2 className="text-sm font-semibold text-stone-900">Texto de la publicación</h2>
          <p className="mt-2 text-sm whitespace-pre-line text-stone-700">{video.descripcion}</p>
          <p className="mt-2 text-sm font-medium text-marca-700">{video.hashtags.join(' ')}</p>
        </section>
      )}

      {video.estado === 'STORYBOARD' && (
        <div className="space-y-2 pb-4">
          {crear.isError && <Aviso tipo="error">{mensajeDeError(crear.error)}</Aviso>}
          <Boton className="w-full" cargando={crear.isPending} disabled={!videosHoy.puedeAprobar} onClick={() => crear.mutate()}>
            Crear mi video
          </Boton>
          <p className="text-center text-xs text-stone-500">
            {videosHoy.puedeAprobar
              ? `Usarás ${videosHoy.porDia === 1 ? 'tu video de hoy' : `1 de tus ${videosHoy.porDia} videos de hoy`}.`
              : 'Ya usaste tu video de hoy. Mañana tienes uno nuevo.'}
          </p>
          <Boton variante="texto" className="w-full" onClick={() => setConfirmarDescarte(true)}>
            Descartar propuesta
          </Boton>
        </div>
      )}
      {enRevision && (
        <div className="space-y-2 pb-4">
          {(aprobar.isError || descargar.isError) && (
            <Aviso tipo="error">{mensajeDeError(aprobar.error ?? descargar.error)}</Aviso>
          )}
          <Boton className="w-full" cargando={aprobar.isPending} onClick={() => aprobar.mutate()}>
            Aprobar mi video
          </Boton>
          <Boton variante="secundario" className="w-full" cargando={descargar.isPending} onClick={() => descargar.mutate()}>
            Descargar
          </Boton>
          <Boton variante="texto" className="w-full" onClick={() => setConfirmarDescarte(true)}>
            Descartar video
          </Boton>
        </div>
      )}
      {video.estado === 'APROBADO' && (
        <div className="space-y-2 pb-4">
          {descargar.isError && <Aviso tipo="error">{mensajeDeError(descargar.error)}</Aviso>}
          <Boton className="w-full" cargando={descargar.isPending} onClick={() => descargar.mutate()}>
            Descargar
          </Boton>
          <p className="text-center text-xs text-stone-500">Muy pronto podrás publicarlo en Instagram desde aquí.</p>
        </div>
      )}
      {!editable && (
        <Link to="/" className="block text-center text-sm font-semibold text-marca-700">
          Crear otro video
        </Link>
      )}

      <Modal
        titulo={enRevision ? '¿Descartar este video?' : '¿Descartar esta propuesta?'}
        abierto={confirmarDescarte}
        onCerrar={() => setConfirmarDescarte(false)}
      >
        <p className="text-sm text-stone-600">
          Quedará en Mis videos como descartado y no se podrá editar.
          {enRevision && ' El video del día que usaste no se devuelve.'}
        </p>
        {descartar.isError && (
          <div className="mt-3">
            <Aviso tipo="error">{descartar.error.message}</Aviso>
          </div>
        )}
        <div className="mt-5 flex gap-2">
          <Boton variante="secundario" className="flex-1" onClick={() => setConfirmarDescarte(false)}>
            Volver
          </Boton>
          <Boton variante="peligro" className="flex-1" cargando={descartar.isPending} onClick={() => descartar.mutate()}>
            Descartar
          </Boton>
        </div>
      </Modal>
    </div>
  )
}

/** Guarda un cambio de un plano; cada llamada decide qué cerrar al terminar. */
function useEditarPlano(video: Video, orden: number, onGuardado: (video: Video) => void) {
  return useMutation({
    mutationFn: (cambio: CambioDePlano) => editarPlano(video.id, orden, cambio),
    onSuccess: onGuardado,
  })
}

function mensajeDeError(error: Error | null) {
  if (!error) return null
  return error instanceof ErrorApi ? error.message : 'No pudimos guardar el cambio. Inténtalo de nuevo.'
}

function TarjetaPlano({
  video,
  plano,
  editable,
  enRevision,
  onGuardado,
}: {
  video: Video
  plano: PlanoVista
  editable: boolean
  enRevision: boolean
  onGuardado: (video: Video) => void
}) {
  const regenerar = useMutation({ mutationFn: () => regenerarPlano(video.id, plano.orden), onSuccess: onGuardado })
  const restantes = video.plan.regeneracionesPorVideo - video.generacion.regeneracionesUsadas
  const conIa = enRevision && video.generacion.iaDisponible
  // En la revisión, una foto nueva crea un clip nuevo: sin regeneraciones, no se ofrece.
  const sinFotoNueva = conIa && restantes <= 0
  const etiquetaRegenerar =
    (plano.usoRespaldo ? 'Reintentar con IA' : 'Regenerar este plano') +
    (restantes > 0 ? ` (te quedan ${restantes})` : ' (ya no quedan)')
  const [texto, setTexto] = useState<string | null>(null)
  const [eligiendoFoto, setEligiendoFoto] = useState(false)
  // Texto y foto se guardan por separado: el error o el éxito de uno no cierra ni borra el otro.
  const guardarTexto = useEditarPlano(video, plano.orden, onGuardado)
  const guardarFoto = useEditarPlano(video, plano.orden, onGuardado)
  const elegirFoto = (cambio: CambioDePlano) =>
    guardarFoto.mutate(cambio, { onSuccess: () => setEligiendoFoto(false) })
  const palabras = contarPalabras(texto ?? plano.texto)
  const excede = palabras > plano.maxPalabras

  return (
    <article className="overflow-hidden rounded-2xl bg-white ring-1 ring-stone-200">
      <div className="flex gap-4 p-4">
        <div className="relative w-28 shrink-0">
          <ImagenRemota
            consulta={claveVideo(video.id)}
            src={plano.foto.url}
            alt={`Foto del momento ${plano.orden}`}
            className="aspect-[9/16] w-full rounded-xl bg-stone-100 object-cover"
          />
          {editable && !sinFotoNueva && (
            <button
              type="button"
              onClick={() => {
                guardarFoto.reset()
                setEligiendoFoto(true)
              }}
              aria-label={'Cambiar foto del momento ' + plano.orden}
              className={ENLACE_PEQUENO + ' w-full justify-center'}
            >
              Cambiar foto
            </button>
          )}
        </div>
        <div className="min-w-0 flex-1 space-y-2">
          <p className="text-xs font-medium tracking-wide text-stone-500 uppercase">
            {plano.orden}. {plano.nombre} · {plano.segundos} s
          </p>
          <p className="text-sm text-stone-600">
            <span className="font-medium text-stone-800">Movimiento:</span> {plano.movimiento.descripcion}
          </p>
          {texto === null ? (
            <div>
              <p className="text-lg leading-snug font-semibold text-stone-900">{plano.texto}</p>
              {editable && (
                <button
                  type="button"
                  onClick={() => {
                    guardarTexto.reset()
                    setTexto(plano.texto)
                  }}
                  aria-label={'Editar texto del momento ' + plano.orden}
                  className={ENLACE_PEQUENO}
                >
                  Editar texto
                </button>
              )}
              {enRevision && plano.usoRespaldo && (
                <p className="mt-1 text-xs text-stone-500">
                  Este momento usa un acercamiento suave a tu foto: no pudimos crear el clip con IA.
                </p>
              )}
              {conIa && (
                <div className="mt-1">
                  <button
                    type="button"
                    disabled={restantes <= 0 || regenerar.isPending}
                    onClick={() => regenerar.mutate()}
                    aria-label={`${etiquetaRegenerar}, momento ${plano.orden}`}
                    className={ENLACE_PEQUENO + ' disabled:text-stone-400'}
                  >
                    {etiquetaRegenerar}
                  </button>
                  {regenerar.isError && <Aviso tipo="error">{mensajeDeError(regenerar.error)}</Aviso>}
                </div>
              )}
            </div>
          ) : (
            <form
              className="space-y-2"
              onSubmit={(evento) => {
                evento.preventDefault()
                guardarTexto.mutate({ texto }, { onSuccess: () => setTexto(null) })
              }}
            >
              <label className="sr-only" htmlFor={`texto-${plano.orden}`}>
                Texto del momento {plano.orden}
              </label>
              <textarea
                id={`texto-${plano.orden}`}
                value={texto}
                onChange={(evento) => setTexto(evento.target.value)}
                rows={2}
                maxLength={MAXIMO_CARACTERES_TEXTO}
                className={CAMPO}
              />
              <p className={`text-xs ${excede ? 'font-semibold text-red-700' : 'text-stone-500'}`} aria-live="polite">
                {palabras} de {plano.maxPalabras} palabras
              </p>
              {guardarTexto.isError && <Aviso tipo="error">{mensajeDeError(guardarTexto.error)}</Aviso>}
              <div className="flex gap-2">
                <Boton
                  type="submit"
                  className="flex-1"
                  cargando={guardarTexto.isPending}
                  disabled={excede || !texto.trim()}
                >
                  Guardar
                </Boton>
                <Boton type="button" variante="secundario" className="flex-1" onClick={() => setTexto(null)}>
                  Cancelar
                </Boton>
              </div>
            </form>
          )}
        </div>
      </div>

      <Modal titulo={`Foto del momento ${plano.orden}`} abierto={eligiendoFoto} onCerrar={() => setEligiendoFoto(false)}>
        <p className="text-sm text-stone-600">Elige otra foto de tu producto o sube una nueva solo para este video.</p>
        {conIa && (
          <p className="mt-2 text-xs text-stone-500">
            Cambiar la foto crea un clip nuevo y usa 1 de tus regeneraciones (te quedan {restantes}).
          </p>
        )}
        <div className="mt-4 grid grid-cols-3 gap-3">
          {video.fotosDelProducto.map((foto) => {
            const actual = plano.foto.tipo === foto.tipo
            return (
              <button
                key={foto.tipo}
                type="button"
                disabled={guardarFoto.isPending || actual}
                aria-pressed={actual}
                onClick={() => foto.tipo && elegirFoto({ foto: { tipo: foto.tipo } })}
                className={`overflow-hidden rounded-xl text-xs font-medium ring-2 ${
                  actual ? 'ring-marca-600' : 'ring-transparent hover:ring-stone-300'
                }`}
              >
                <ImagenRemota
                  consulta={claveVideo(video.id)}
                  src={foto.url}
                  alt=""
                  className="aspect-[9/16] w-full bg-stone-100 object-cover"
                />
                <span className="block py-1">{foto.tipo ? NOMBRES_FOTO[foto.tipo] : 'Otra'}</span>
              </button>
            )
          })}
        </div>
        <div className="mt-5">
          <SubidorDeImagen
            proposito="FOTO_PRODUCTO"
            etiqueta="Subir otra foto"
            deshabilitado={guardarFoto.isPending}
            guia={<p>Vertical, con buena luz. Solo se usará en este video.</p>}
            onSubida={(imagen) => elegirFoto({ foto: { clave: imagen.clave } })}
          />
        </div>
        {guardarFoto.isError && (
          <div className="mt-3">
            <Aviso tipo="error">{mensajeDeError(guardarFoto.error)}</Aviso>
          </div>
        )}
      </Modal>
    </article>
  )
}

function TarjetaCierre({
  video,
  cierre,
  editable,
  onGuardado,
}: {
  video: Video
  cierre: CierreVista
  editable: boolean
  onGuardado: (video: Video) => void
}) {
  const [precio, setPrecio] = useState<string | null>(null)
  const guardar = useEditarPlano(video, cierre.orden, onGuardado)
  const fondo = cierre.fotoLocal
    ? undefined
    : { background: `linear-gradient(160deg, ${cierre.colorPrimario ?? '#be123c'}, ${cierre.colorSecundario ?? '#fde68a'})` }
  // El llamado "Te esperamos en Miraflores" ya incluye el distrito: no se repite.
  const mostrarUbicacion = !cierre.llamado?.includes(cierre.ubicacion)

  return (
    <article className="overflow-hidden rounded-2xl bg-white ring-1 ring-stone-200">
      <div className="flex gap-4 p-4">
        <div className="relative aspect-[9/16] w-28 shrink-0 overflow-hidden rounded-xl" style={fondo}>
          {cierre.fotoLocal && (
            <ImagenRemota
              consulta={claveVideo(video.id)}
              src={cierre.fotoLocal}
              alt="Foto del local"
              className="absolute inset-0 size-full object-cover brightness-75"
            />
          )}
          <div className="relative flex size-full flex-col items-center justify-center gap-1 p-2 text-center text-white">
            {cierre.logo && (
              <ImagenRemota consulta={claveVideo(video.id)} src={cierre.logo} alt="Logo" className="size-10 object-contain" />
            )}
            {cierre.precio && <span className="text-[11px] leading-tight font-bold">{cierre.precio}</span>}
            {cierre.llamado && <span className="text-[10px] leading-tight">{cierre.llamado}</span>}
          </div>
        </div>
        <div className="min-w-0 flex-1 space-y-2 text-sm">
          <p className="text-xs font-medium tracking-wide text-stone-500 uppercase">
            {cierre.orden}. {cierre.nombre} · {cierre.segundos} s
          </p>
          <p className="text-stone-600">Lo armamos con tu logo y tus datos.</p>
          {precio === null ? (
            <div>
              <p>
                <span className="font-medium text-stone-800">Precio:</span> {cierre.precio ?? 'Sin precio'}
              </p>
              {editable && (
                <button
                  type="button"
                  onClick={() => {
                    guardar.reset()
                    setPrecio(cierre.precio ?? '')
                  }}
                  className={ENLACE_PEQUENO}
                >
                  {cierre.precio ? 'Editar precio' : 'Agregar precio'}
                </button>
              )}
            </div>
          ) : (
            <form
              className="space-y-2"
              onSubmit={(evento) => {
                evento.preventDefault()
                guardar.mutate({ precio }, { onSuccess: () => setPrecio(null) })
              }}
            >
              <label className="sr-only" htmlFor="precio-cierre">
                Precio o promoción
              </label>
              <input
                id="precio-cierre"
                value={precio}
                maxLength={cierre.maximoCaracteresPrecio}
                onChange={(evento) => setPrecio(evento.target.value)}
                placeholder="Por ejemplo: S/ 65"
                className={CAMPO}
              />
              <p className="text-xs text-stone-500">
                {precio.length} de {cierre.maximoCaracteresPrecio} caracteres. Déjalo vacío para no mostrar precio.
              </p>
              {guardar.isError && <Aviso tipo="error">{mensajeDeError(guardar.error)}</Aviso>}
              <div className="flex gap-2">
                <Boton type="submit" className="flex-1" cargando={guardar.isPending}>
                  Guardar
                </Boton>
                <Boton type="button" variante="secundario" className="flex-1" onClick={() => setPrecio(null)}>
                  Cancelar
                </Boton>
              </div>
            </form>
          )}
          {cierre.llamado && (
            <p>
              <span className="font-medium text-stone-800">Llamado:</span> {cierre.llamado}
            </p>
          )}
          {mostrarUbicacion && (
            <p>
              <span className="font-medium text-stone-800">Ubicación:</span> {cierre.ubicacion}
            </p>
          )}
          {cierre.whatsapp && (
            <p>
              <span className="font-medium text-stone-800">WhatsApp:</span> {cierre.whatsapp}
            </p>
          )}
        </div>
      </div>
    </article>
  )
}
