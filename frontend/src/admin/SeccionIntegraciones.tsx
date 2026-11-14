import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  activarModelo,
  actualizarModelo,
  borrarCredencial,
  crearModelo,
  desactivarModelo,
  guardarCredencial,
  obtenerIntegraciones,
  probarModelo,
  type Credencial,
  type Integraciones,
  type ModeloIA,
  type ResultadoPrueba,
  type TipoModelo,
} from '../api/admin'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo, Selector } from '../componentes/Campo'
import { Cargando } from '../componentes/Cargando'
import { formatearFechaHora } from '../componentes/formato'
import { Modal } from '../componentes/Modal'

const CLAVE = ['integraciones']

/** Nombres conocidos de las credenciales, para que se entienda qué pegar en cada una. */
const CREDENCIALES_CONOCIDAS: Record<string, { titulo: string; ayuda: string; json: boolean }> = {
  'autoreel/gemini': {
    titulo: 'Google AI Studio',
    ayuda: 'La clave de API de aistudio.google.com/apikey (empieza con "AIza"). Sirve para el guion y los clips.',
    json: false,
  },
  'autoreel/google-cloud': {
    titulo: 'Google Cloud (Vertex AI)',
    ayuda:
      'Una clave de API de Google Cloud (empieza con "AQ." o "AIza"), o el archivo JSON completo de una cuenta de servicio. Con la clave, agrega el ID de tu proyecto como "proyecto" en los parámetros de Veo. Sirve para Veo y Gemini en Google Cloud.',
    json: true,
  },
}

const ORIGEN = {
  PANEL: { texto: 'Guardada desde Ajustes', estilo: 'bg-emerald-50 text-emerald-800' },
  CONFIGURACION: { texto: 'Desde el archivo .env', estilo: 'bg-sky-50 text-sky-800' },
  NINGUNO: { texto: 'Sin configurar', estilo: 'bg-red-50 text-red-800' },
}

const estiloTexto =
  'block w-full rounded-lg border-0 bg-white px-3 py-2 font-mono text-sm text-stone-900 ring-1 ring-inset ring-stone-300 placeholder:text-stone-400 focus:ring-2 focus:ring-inset focus:ring-marca-600'

/** RF-11: modelos de IA con su estado, prueba de conexión y las claves que usan (ADR 0018). */
export function SeccionIntegraciones() {
  const integraciones = useQuery({ queryKey: CLAVE, queryFn: obtenerIntegraciones })
  const [credencial, setCredencial] = useState<Credencial | null>(null)
  const [modelo, setModelo] = useState<ModeloIA | 'nuevo' | null>(null)

  if (integraciones.isPending) return <Cargando />
  if (integraciones.error) return <Aviso tipo="error">{integraciones.error.message}</Aviso>
  const { data } = integraciones

  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <div>
          <h2 className="text-lg font-semibold">Claves de los proveedores</h2>
          <p className="text-sm text-stone-600">
            Se guardan fuera de la base de datos y nunca se vuelven a mostrar: solo ves una pista para reconocerlas. La
            que pegues aquí tiene prioridad sobre la del archivo .env.
          </p>
        </div>
        <ul className="grid gap-3 md:grid-cols-2">
          {data.credenciales.map((item) => (
            <TarjetaCredencial key={item.nombre} credencial={item} onCambiar={() => setCredencial(item)} />
          ))}
        </ul>
      </section>

      <section className="space-y-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold">Modelos de IA</h2>
            <p className="text-sm text-stone-600">
              Un modelo nuevo de un proveedor ya integrado se agrega aquí, sin programar. Los planes eligen qué modelos
              usan.
            </p>
          </div>
          <Boton variante="secundario" onClick={() => setModelo('nuevo')}>
            Agregar modelo
          </Boton>
        </div>
        {(['VIDEO', 'TEXTO'] as TipoModelo[]).map((tipo) => (
          <div key={tipo} className="space-y-2">
            <h3 className="text-sm font-semibold tracking-wide text-stone-500 uppercase">
              {tipo === 'VIDEO' ? 'Video (clips)' : 'Texto (guion)'}
            </h3>
            <ul className="space-y-3">
              {data.modelos
                .filter((item) => item.tipo === tipo)
                .map((item) => (
                  <TarjetaModelo key={item.id} modelo={item} onEditar={() => setModelo(item)} />
                ))}
            </ul>
          </div>
        ))}
      </section>

      {credencial && <ModalCredencial credencial={credencial} onCerrar={() => setCredencial(null)} />}
      {modelo && (
        <ModalModelo
          modelo={modelo === 'nuevo' ? null : modelo}
          integraciones={data}
          onCerrar={() => setModelo(null)}
        />
      )}
    </div>
  )
}

function TarjetaCredencial({ credencial, onCambiar }: { credencial: Credencial; onCambiar: () => void }) {
  const clienteConsultas = useQueryClient()
  const conocida = CREDENCIALES_CONOCIDAS[credencial.nombre]
  const quitar = useMutation({
    mutationFn: () => borrarCredencial(credencial.nombre),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: CLAVE }),
  })
  const origen = ORIGEN[credencial.origen]
  return (
    <li className="rounded-xl bg-white p-4 ring-1 ring-stone-200">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-semibold">{conocida?.titulo ?? credencial.nombre}</p>
          <p className="font-mono text-xs text-stone-500">{credencial.nombre}</p>
        </div>
        <span className={`rounded px-2 py-0.5 text-xs font-medium ${origen.estilo}`}>{origen.texto}</span>
      </div>
      {credencial.descripcion && <p className="mt-2 text-sm text-stone-700">{credencial.descripcion}</p>}
      <p className="mt-1 text-sm text-stone-500">La usan: {credencial.modelos.join(', ')}</p>
      {quitar.error && <div className="mt-2"><Aviso tipo="error">{quitar.error.message}</Aviso></div>}
      <div className="mt-3 flex flex-wrap gap-2">
        <Boton variante="secundario" onClick={onCambiar}>
          {credencial.origen === 'NINGUNO' ? 'Pegar clave' : 'Cambiar clave'}
        </Boton>
        {credencial.origen === 'PANEL' && (
          <Boton
            variante="texto"
            cargando={quitar.isPending}
            onClick={() => {
              if (window.confirm('¿Quitar la clave guardada en Ajustes? Si hay una en el archivo .env, se usará esa.'))
                quitar.mutate()
            }}
          >
            Quitar la de Ajustes
          </Boton>
        )}
      </div>
    </li>
  )
}

function ModalCredencial({ credencial, onCerrar }: { credencial: Credencial; onCerrar: () => void }) {
  const clienteConsultas = useQueryClient()
  const conocida = CREDENCIALES_CONOCIDAS[credencial.nombre]
  const [valor, setValor] = useState('')
  const guardar = useMutation({
    mutationFn: () => guardarCredencial(credencial.nombre, valor),
    onSuccess: () => {
      setValor('')
      clienteConsultas.invalidateQueries({ queryKey: CLAVE })
      onCerrar()
    },
  })
  const cerrar = () => {
    setValor('')
    onCerrar()
  }
  return (
    <Modal titulo={`Clave de ${conocida?.titulo ?? credencial.nombre}`} abierto onCerrar={cerrar}>
      <form
        onSubmit={(evento: FormEvent) => {
          evento.preventDefault()
          guardar.mutate()
        }}
        className="space-y-4"
      >
        {conocida && <p className="text-sm text-stone-600">{conocida.ayuda}</p>}
        <div>
          <label htmlFor="valor-credencial" className="mb-1 block text-sm font-medium text-stone-700">
            {conocida?.json ? 'Clave de API o contenido del archivo JSON' : 'Clave'}
          </label>
          {conocida?.json ? (
            <textarea
              id="valor-credencial"
              rows={8}
              required
              autoComplete="off"
              spellCheck={false}
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              className={estiloTexto}
              placeholder='AQ.… o {"type": "service_account", ...}'
            />
          ) : (
            <input
              id="valor-credencial"
              type="password"
              required
              autoComplete="off"
              spellCheck={false}
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              className={`${estiloTexto} min-h-11`}
              placeholder="AIza…"
            />
          )}
        </div>
        <p className="text-xs text-stone-500">
          Después de guardarla no se vuelve a mostrar. Usa "Probar conexión" en sus modelos para confirmar que funciona.
        </p>
        {guardar.error && <Aviso tipo="error">{guardar.error.message}</Aviso>}
        <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
          <Boton type="button" variante="secundario" onClick={cerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={guardar.isPending}>
            Guardar clave
          </Boton>
        </div>
      </form>
    </Modal>
  )
}

function TarjetaModelo({ modelo, onEditar }: { modelo: ModeloIA; onEditar: () => void }) {
  const clienteConsultas = useQueryClient()
  const [resultado, setResultado] = useState<ResultadoPrueba | null>(null)
  const actualizar = () => clienteConsultas.invalidateQueries({ queryKey: CLAVE })
  const probar = useMutation({
    mutationFn: () => probarModelo(modelo.id),
    onSuccess: (respuesta) => {
      setResultado(respuesta)
      actualizar()
    },
  })
  const activar = useMutation({ mutationFn: () => activarModelo(modelo.id), onSuccess: actualizar })
  const desactivar = useMutation({ mutationFn: () => desactivarModelo(modelo.id), onSuccess: actualizar })
  const error = probar.error ?? activar.error ?? desactivar.error
  const activo = modelo.estado === 'ACTIVO'
  const prueba = resultado
    ? { exitosa: resultado.exitosa, mensaje: resultado.mensaje, fecha: resultado.fecha }
    : modelo.ultimaPrueba
      ? { exitosa: modelo.ultimaPruebaExitosa, mensaje: modelo.ultimaPruebaMensaje, fecha: modelo.ultimaPrueba }
      : null

  return (
    <li className="rounded-xl bg-white p-4 ring-1 ring-stone-200">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="font-semibold">
            {modelo.nombre}{' '}
            <span
              className={`ml-1 rounded px-1.5 py-0.5 text-xs font-medium ${activo ? 'bg-emerald-50 text-emerald-800' : 'bg-stone-100 text-stone-600'}`}
            >
              {activo ? 'Activo' : 'Inactivo'}
            </span>
          </p>
          <p className="text-sm break-all text-stone-600">
            {modelo.proveedor} · {modelo.adaptador} · <span className="font-mono">{modelo.identificadorModelo}</span>
          </p>
          <p className="text-sm text-stone-600">
            US$ {modelo.costoReferencial} {modelo.unidadCosto === 'POR_SEGUNDO' ? 'por segundo de video' : 'por guion'} ·
            clave <span className="font-mono">{modelo.nombreSecreto}</span>
            {!modelo.credencialConfigurada && <span className="text-red-700"> (sin configurar)</span>}
          </p>
          <p className="text-sm text-stone-500">
            {modelo.planes.length > 0 ? `Lo usa el plan ${modelo.planes.join(', ')}` : 'Ningún plan lo usa'}
          </p>
          {!modelo.adaptadorDisponible && (
            <p className="text-sm text-red-700">El adaptador {modelo.adaptador} no existe en el código.</p>
          )}
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <Boton variante="secundario" onClick={() => probar.mutate()} cargando={probar.isPending}>
            Probar conexión
          </Boton>
          {activo ? (
            <Boton variante="texto" onClick={() => desactivar.mutate()} cargando={desactivar.isPending}>
              Desactivar
            </Boton>
          ) : (
            <Boton variante="texto" onClick={() => activar.mutate()} cargando={activar.isPending}>
              Activar
            </Boton>
          )}
          <Boton variante="texto" onClick={onEditar}>
            Editar
          </Boton>
        </div>
      </div>
      {prueba && (
        <div className="mt-3">
          <Aviso tipo={prueba.exitosa ? 'exito' : 'error'}>
            {prueba.mensaje} <span className="opacity-70">({formatearFechaHora(prueba.fecha)})</span>
          </Aviso>
        </div>
      )}
      {error && (
        <div className="mt-3">
          <Aviso tipo="error">{error.message}</Aviso>
        </div>
      )}
    </li>
  )
}

function ModalModelo({
  modelo,
  integraciones,
  onCerrar,
}: {
  modelo: ModeloIA | null
  integraciones: Integraciones
  onCerrar: () => void
}) {
  const clienteConsultas = useQueryClient()
  const [tipo, setTipo] = useState<TipoModelo>(modelo?.tipo ?? 'VIDEO')
  const [nombre, setNombre] = useState(modelo?.nombre ?? '')
  const [proveedor, setProveedor] = useState(modelo?.proveedor ?? 'GOOGLE')
  const [adaptadorElegido, setAdaptador] = useState(modelo?.adaptador ?? '')
  const [identificador, setIdentificador] = useState(modelo?.identificadorModelo ?? '')
  const [costo, setCosto] = useState(modelo ? String(modelo.costoReferencial) : '')
  const [secreto, setSecreto] = useState(modelo?.nombreSecreto ?? 'autoreel/gemini')
  const [parametros, setParametros] = useState(JSON.stringify(modelo?.parametros ?? {}, null, 2))
  const [errorJson, setErrorJson] = useState<string | null>(null)
  const adaptadores = integraciones.adaptadores[tipo] ?? []
  const adaptador = adaptadorElegido && adaptadores.includes(adaptadorElegido) ? adaptadorElegido : (adaptadores[0] ?? '')

  const guardar = useMutation({
    mutationFn: (valores: Record<string, unknown>) => {
      const solicitud = {
        nombre,
        proveedor,
        tipo,
        adaptador,
        identificadorModelo: identificador,
        parametros: valores,
        costoReferencial: costo ? Number(costo) : null,
        nombreSecreto: secreto,
      }
      return modelo ? actualizarModelo(modelo.id, solicitud) : crearModelo(solicitud)
    },
    onSuccess: () => {
      clienteConsultas.invalidateQueries({ queryKey: CLAVE })
      clienteConsultas.invalidateQueries({ queryKey: ['planes'] })
      onCerrar()
    },
  })

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    let valores: unknown
    try {
      valores = JSON.parse(parametros || '{}')
    } catch {
      setErrorJson('Los parámetros deben ser un JSON válido, por ejemplo {"resolucion": "720p"}.')
      return
    }
    if (typeof valores !== 'object' || valores === null || Array.isArray(valores)) {
      setErrorJson('Los parámetros deben ser un objeto JSON, entre llaves.')
      return
    }
    setErrorJson(null)
    guardar.mutate(valores as Record<string, unknown>)
  }

  return (
    <Modal titulo={modelo ? `Editar ${modelo.nombre}` : 'Agregar modelo'} abierto onCerrar={onCerrar}>
      <form onSubmit={enviar} className="space-y-4">
        {modelo ? (
          <p className="text-sm text-stone-600">Modelo de {modelo.tipo === 'VIDEO' ? 'video' : 'texto'} (el tipo no cambia).</p>
        ) : (
          <Selector etiqueta="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoModelo)}>
            <option value="VIDEO">Video (clips, se cobra por segundo)</option>
            <option value="TEXTO">Texto (guion, se cobra por solicitud)</option>
          </Selector>
        )}
        <Campo etiqueta="Nombre visible" required maxLength={80} value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Veo 3.1 Fast" />
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Proveedor" required maxLength={30} value={proveedor} onChange={(e) => setProveedor(e.target.value)} />
          <Selector
            etiqueta="Adaptador"
            required
            value={adaptador}
            onChange={(e) => setAdaptador(e.target.value)}
            ayuda="La implementación en el código. Un proveedor nuevo requiere programar su adaptador."
          >
            {adaptadores.map((opcion) => (
              <option key={opcion} value={opcion}>
                {opcion}
              </option>
            ))}
          </Selector>
        </div>
        <Campo
          etiqueta="Identificador del modelo"
          required
          maxLength={120}
          value={identificador}
          onChange={(e) => setIdentificador(e.target.value)}
          placeholder="veo-3.1-fast-generate-001"
          ayuda="El nombre exacto que exige la API del proveedor."
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo
            etiqueta={tipo === 'VIDEO' ? 'Costo por segundo (US$)' : 'Costo por guion (US$)'}
            type="number"
            min="0"
            step="0.000001"
            required
            value={costo}
            onChange={(e) => setCosto(e.target.value)}
          />
          <Campo
            etiqueta="Clave que usa"
            required
            maxLength={120}
            list="secretos-conocidos"
            value={secreto}
            onChange={(e) => setSecreto(e.target.value)}
          />
          <datalist id="secretos-conocidos">
            {integraciones.credenciales.map((item) => (
              <option key={item.nombre} value={item.nombre} />
            ))}
          </datalist>
        </div>
        <div>
          <label htmlFor="parametros-modelo" className="mb-1 block text-sm font-medium text-stone-700">
            Parámetros (JSON)
          </label>
          <textarea
            id="parametros-modelo"
            rows={5}
            spellCheck={false}
            value={parametros}
            onChange={(e) => setParametros(e.target.value)}
            className={estiloTexto}
          />
          <p className="mt-1 text-xs text-stone-500">Por ejemplo resolución, relación de aspecto o región, según el adaptador.</p>
        </div>
        {!modelo && <p className="text-xs text-stone-500">El modelo nace inactivo: pruébalo y actívalo después.</p>}
        {(errorJson ?? guardar.error) && <Aviso tipo="error">{errorJson ?? guardar.error?.message}</Aviso>}
        <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
          <Boton type="button" variante="secundario" onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={guardar.isPending}>
            {modelo ? 'Guardar' : 'Agregar modelo'}
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
