import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  CLAVE_CHAT,
  iniciarConversacion,
  obtenerConversacion,
  procesar,
  responder,
  type Conversacion,
  type MensajeChat,
  type Respuesta,
} from '../api/chat'
import { CLAVE_PRODUCTOS } from '../api/catalogo'
import { ErrorApi } from '../api/cliente'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Cargando } from '../componentes/Cargando'
import { HojaProductoNuevo, type ProductoNuevo } from './HojaProductoNuevo'
import { SelectorDeProducto } from './SelectorDeProducto'

/** RF-19: la pantalla principal es un chat de preguntas fijas, sin IA, que reúne la información del video del día. */
export function PaginaChat() {
  const clienteConsultas = useQueryClient()
  const conversacion = useQuery({ queryKey: CLAVE_CHAT, queryFn: obtenerConversacion, refetchOnWindowFocus: false })
  const guardar = (nueva: Conversacion) => clienteConsultas.setQueryData(CLAVE_CHAT, nueva)

  const iniciar = useMutation({ mutationFn: iniciarConversacion, onSuccess: guardar })
  const iniciada = useRef(false)
  // Si no hay conversación, se empieza una sola vez (StrictMode monta dos veces en desarrollo).
  useEffect(() => {
    if (conversacion.data === null && !iniciada.current) {
      iniciada.current = true
      iniciar.mutate(false)
    }
  }, [conversacion.data, iniciar])

  if (conversacion.isPending || (conversacion.data === null && !iniciar.error)) return <Cargando texto="Abriendo tu chat…" />
  if (!conversacion.data) {
    const error = conversacion.error ?? iniciar.error
    return (
      <div className="mx-auto max-w-md space-y-3 py-10">
        <Aviso tipo="error">{error?.message ?? 'No pudimos abrir tu chat.'}</Aviso>
        <Boton
          onClick={() => {
            iniciar.reset()
            if (conversacion.error) conversacion.refetch()
            else iniciar.mutate(false)
          }}
        >
          Reintentar
        </Boton>
      </div>
    )
  }
  const ultimoDelSistema = [...conversacion.data.mensajes].reverse().find((m) => m.autor === 'SISTEMA')

  return (
    <>
      {/* Región viva estable (no se vuelve a montar al empezar de nuevo): anuncia cada pregunta nueva. */}
      <p className="sr-only" aria-live="polite">
        {ultimoDelSistema?.texto}
      </p>
      <Chat
        key={conversacion.data.id}
        conversacion={conversacion.data}
        onActualizar={guardar}
        onRecargar={() => clienteConsultas.invalidateQueries({ queryKey: CLAVE_CHAT })}
        onEmpezarDeNuevo={() => {
          iniciar.reset()
          iniciar.mutate(true)
        }}
        empezando={iniciar.isPending}
        errorAlEmpezar={iniciar.error?.message ?? null}
      />
    </>
  )
}

interface PropsChat {
  conversacion: Conversacion
  onActualizar: (conversacion: Conversacion) => void
  onRecargar: () => void
  onEmpezarDeNuevo: () => void
  empezando: boolean
  errorAlEmpezar: string | null
}

function Chat({ conversacion, onActualizar, onRecargar, onEmpezarDeNuevo, empezando, errorAlEmpezar }: PropsChat) {
  const clienteConsultas = useQueryClient()
  const [texto, setTexto] = useState('')
  const [hojaAbierta, setHojaAbierta] = useState(false)
  // Al volver a un chat empezado en otra visita, se pregunta si continuar (sección 7.3).
  const [preguntarSiContinuar, setPreguntarSiContinuar] = useState(
    () => conversacion.estado === 'EN_CURSO' && conversacion.mensajes.some((m) => m.autor === 'USUARIO'),
  )
  const final = useRef<HTMLDivElement>(null)
  const { paso } = conversacion

  const enviar = useMutation({
    mutationFn: (respuesta: Respuesta) => responder(conversacion.id, respuesta),
    onSuccess: (nueva, respuesta) => {
      onActualizar(nueva)
      // Si siguió escribiendo mientras viajaba el envío, lo nuevo se conserva.
      if ('texto' in respuesta) setTexto((actual) => (actual.trim() === respuesta.texto ? '' : actual))
      if ('productoNuevo' in respuesta) {
        setHojaAbierta(false)
        clienteConsultas.invalidateQueries({ queryKey: CLAVE_PRODUCTOS })
      }
    },
    onError: (error) => {
      // La pantalla estaba desactualizada (otra pestaña o un reintento): se recarga sin mostrar error.
      if (error instanceof ErrorApi && (error.codigo === 'PASO_CAMBIADO' || error.codigo === 'CONVERSACION_CERRADA')) {
        onRecargar()
      }
    },
  })
  const cerrarGrupo = useMutation({
    mutationFn: () => procesar(conversacion.id),
    onSuccess: onActualizar,
    onError: (error) => {
      if (error instanceof ErrorApi && error.codigo === 'CONVERSACION_CERRADA') onRecargar()
    },
  })

  // RN-17: 4 s después del último mensaje, el backend agrupa lo escrito en una sola respuesta.
  const { procesarEnMs } = conversacion
  const cerrar = cerrarGrupo.mutate
  useEffect(() => {
    if (procesarEnMs === null) return
    const temporizador = window.setTimeout(() => cerrar(), procesarEnMs + 150)
    const alVolver = () => {
      if (document.visibilityState === 'visible') cerrar()
    }
    document.addEventListener('visibilitychange', alVolver)
    return () => {
      window.clearTimeout(temporizador)
      document.removeEventListener('visibilitychange', alVolver)
    }
  }, [procesarEnMs, conversacion.mensajes.length, cerrar])

  useEffect(() => {
    final.current?.scrollIntoView({ block: 'end' })
  }, [conversacion.mensajes.length, paso?.id])

  // Al abrirse el teclado del celular la lista se achica: se mantiene a la vista la última pregunta.
  useEffect(() => {
    const pantalla = window.visualViewport
    if (!pantalla) return
    const alCambiar = () => final.current?.scrollIntoView({ block: 'end' })
    pantalla.addEventListener('resize', alCambiar)
    return () => pantalla.removeEventListener('resize', alCambiar)
  }, [])

  // La hoja de producto nuevo solo se muestra mientras la pregunta sea la del producto
  // (otra pestaña pudo haber avanzado la conversación).
  const hojaVisible = hojaAbierta && paso?.tipo === 'PRODUCTO'

  const pendiente = conversacion.mensajes.filter((m) => m.pendiente).reduce((total, m) => total + m.texto.length + 1, 0)
  const restantes = conversacion.maximoCaracteres - pendiente - texto.trim().length
  const ocupado = enviar.isPending || empezando
  const escribiendo = cerrarGrupo.isPending || (enviar.isPending && !('texto' in (enviar.variables ?? {})))

  const enviarTexto = (evento: FormEvent) => {
    evento.preventDefault()
    if (ocupado || !paso || paso.tipo !== 'TEXTO' || !texto.trim() || restantes < 0) return
    enviar.mutate({ paso: paso.id, texto: texto.trim() })
  }

  const errorVisible =
    enviar.error && !hojaVisible && !(enviar.error instanceof ErrorApi && enviar.error.codigo === 'PASO_CAMBIADO')
      ? enviar.error.message
      : (errorAlEmpezar ?? cerrarGrupo.error?.message ?? null)

  return (
    <div className="-mx-4 -my-6 flex h-[calc(100dvh-3.5rem-4rem)] flex-col md:h-[calc(100dvh-3.5rem)] md:flex-row">
      <aside className="hidden w-64 shrink-0 border-r border-stone-200 p-5 text-sm text-stone-500 md:block">
        {conversacion.aviso}
      </aside>

      <div className="flex min-h-0 flex-1 flex-col">
        <div role="log" aria-live="off" aria-label="Conversación" className="flex-1 space-y-3 overflow-y-auto px-4 py-5">
          {conversacion.mensajes.map((mensaje) => (
            <Burbuja key={mensaje.id} mensaje={mensaje} />
          ))}
          {conversacion.estado === 'COMPLETA' && (
            <TarjetaFinal conversacion={conversacion} onOtro={onEmpezarDeNuevo} empezando={empezando} />
          )}
          <div ref={final} />
        </div>
        {escribiendo && (
          <p className="px-4 pb-1 text-xs text-stone-400" aria-hidden>
            escribiendo…
          </p>
        )}

        {preguntarSiContinuar && conversacion.estado === 'EN_CURSO' && (
          <div className="mx-4 mb-3 rounded-xl bg-marca-50 p-3 ring-1 ring-marca-100">
            <p className="text-sm font-medium text-stone-800">¿Continuamos donde quedamos?</p>
            <div className="mt-2 flex gap-2">
              <Boton className="flex-1" onClick={() => setPreguntarSiContinuar(false)}>
                Continuar
              </Boton>
              <Boton
                variante="secundario"
                className="flex-1"
                cargando={empezando}
                onClick={() => {
                  setPreguntarSiContinuar(false)
                  onEmpezarDeNuevo()
                }}
              >
                Empezar de nuevo
              </Boton>
            </div>
          </div>
        )}

        {paso && !preguntarSiContinuar && (
          <div className="border-t border-stone-200 bg-white px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
            {errorVisible && (
              <div className="mb-2">
                <Aviso tipo="error">{errorVisible}</Aviso>
              </div>
            )}

            {paso.tipo === 'BOTONES' && (
              <div role="group" aria-label={paso.texto} className="flex flex-wrap gap-2">
                {paso.opciones.map((opcion) => (
                  <Boton
                    key={opcion.valor}
                    variante="secundario"
                    disabled={ocupado}
                    onClick={() => enviar.mutate({ paso: paso.id, opcion: opcion.valor })}
                  >
                    {opcion.texto}
                  </Boton>
                ))}
              </div>
            )}

            {paso.tipo === 'PRODUCTO' && (
              <SelectorDeProducto
                productos={conversacion.productos}
                incompletos={conversacion.productosIncompletos}
                deshabilitado={ocupado}
                onElegir={(producto) => enviar.mutate({ paso: paso.id, productoId: producto.id })}
                onSubirNuevo={() => {
                  enviar.reset()
                  setHojaAbierta(true)
                }}
              />
            )}

            {paso.tipo === 'TEXTO' && (
              <form onSubmit={enviarTexto} className="space-y-2">
                {(paso.sugerencia || paso.omitible) && (
                  <div className="flex flex-wrap gap-2">
                    {paso.sugerencia && (
                      <button
                        type="button"
                        onClick={() => setTexto(paso.sugerencia ?? '')}
                        className="min-h-9 rounded-full bg-stone-100 px-3 text-xs font-medium text-stone-700 hover:bg-stone-200"
                      >
                        Usar la descripción de mi producto
                      </button>
                    )}
                    {paso.omitible && (
                      <button
                        type="button"
                        disabled={ocupado}
                        onClick={() => enviar.mutate({ paso: paso.id, omitir: true })}
                        className="min-h-9 rounded-full bg-stone-100 px-3 text-xs font-medium text-stone-700 hover:bg-stone-200"
                      >
                        No, gracias
                      </button>
                    )}
                  </div>
                )}
                <div className="flex items-end gap-2">
                  <label htmlFor="respuesta" className="sr-only">
                    Tu respuesta
                  </label>
                  <textarea
                    id="respuesta"
                    rows={Math.min(4, Math.max(1, Math.ceil(texto.length / 34) + (texto.match(/\n/g)?.length ?? 0)))}
                    value={texto}
                    enterKeyHint="send"
                    onChange={(e) => setTexto(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) enviarTexto(e)
                    }}
                    placeholder="Escribe tu respuesta…"
                    className="max-h-32 min-h-11 flex-1 resize-none rounded-2xl border-0 bg-stone-100 px-4 py-2.5 text-base focus:ring-2 focus:ring-marca-600 sm:text-sm"
                  />
                  <Boton type="submit" disabled={!texto.trim() || restantes < 0} cargando={enviar.isPending}>
                    Enviar
                  </Boton>
                </div>
                <p className={`text-right text-xs tabular-nums ${restantes < 0 ? 'text-red-700' : 'text-stone-400'}`}>
                  {restantes < 0 ? 'Es demasiado largo: resúmelo un poco' : `${restantes} caracteres`}
                </p>
              </form>
            )}

            {paso.tipo !== 'TEXTO' && (
              <p className="mt-2 text-xs text-stone-400 md:hidden">{conversacion.aviso}</p>
            )}
          </div>
        )}
      </div>

      <HojaProductoNuevo
        key={hojaVisible ? 'abierta' : 'cerrada'}
        abierto={hojaVisible}
        enviando={enviar.isPending}
        error={hojaVisible && enviar.error ? enviar.error.message : null}
        onCerrar={() => {
          setHojaAbierta(false)
          enviar.reset()
        }}
        onUsar={(producto: ProductoNuevo) => paso && enviar.mutate({ paso: paso.id, productoNuevo: producto })}
      />
    </div>
  )
}

function Burbuja({ mensaje }: { mensaje: MensajeChat }) {
  const propia = mensaje.autor === 'USUARIO'
  return (
    <div className={`flex ${propia ? 'justify-end' : 'justify-start'}`}>
      <p
        className={`max-w-[85%] rounded-2xl px-4 py-2.5 text-sm whitespace-pre-wrap shadow-sm ${
          propia ? 'rounded-br-md bg-marca-700 text-white' : 'rounded-bl-md bg-white text-stone-800 ring-1 ring-stone-200'
        } ${mensaje.pendiente ? 'opacity-80' : ''}`}
      >
        <span className="sr-only">{propia ? 'Tú: ' : 'AutoReel: '}</span>
        {mensaje.texto}
      </p>
    </div>
  )
}

function TarjetaFinal({
  conversacion,
  onOtro,
  empezando,
}: {
  conversacion: Conversacion
  onOtro: () => void
  empezando: boolean
}) {
  return (
    <section className="mt-4 rounded-2xl bg-white p-5 ring-1 ring-stone-200">
      <h2 className="font-semibold">Tus respuestas</h2>
      <dl className="mt-3 space-y-2 text-sm">
        {conversacion.resumen.map((fila) => (
          <div key={fila.pregunta}>
            <dt className="text-stone-500">{fila.pregunta}</dt>
            <dd className="font-medium text-stone-800">{fila.respuesta}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-4 text-sm text-stone-600">Guardamos todo. Muy pronto verás aquí la propuesta de tu video.</p>
      <Boton variante="secundario" className="mt-4 w-full" onClick={onOtro} cargando={empezando}>
        Crear otro video
      </Boton>
    </section>
  )
}
