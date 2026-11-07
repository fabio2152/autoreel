import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { actualizarPlan, crearPlan, listarPlanes, obtenerIntegraciones, type Plan } from '../api/admin'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo, Selector } from '../componentes/Campo'
import { Cargando } from '../componentes/Cargando'
import { formatearMonto } from '../componentes/formato'
import { Modal } from '../componentes/Modal'

/** RF-10: planes con sus videos por día, regeneraciones, modelos y precio; crear y editar sin programar. */
export function SeccionPlanes() {
  const planes = useQuery({ queryKey: ['planes'], queryFn: listarPlanes })
  const [editando, setEditando] = useState<Plan | 'nuevo' | null>(null)

  return (
    <section className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-stone-600">
          Cada plan define cuántos videos por día y regeneraciones permite y qué modelos de IA usa. Un cambio rige para
          los videos nuevos.
        </p>
        <Boton onClick={() => setEditando('nuevo')}>Crear plan</Boton>
      </div>
      {planes.isPending && <Cargando />}
      {planes.error && <Aviso tipo="error">{planes.error.message}</Aviso>}
      <ul className="grid gap-3 md:grid-cols-2">
        {planes.data?.map((plan) => (
          <li key={plan.id} className="rounded-xl bg-white p-4 ring-1 ring-stone-200">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold">
                  {plan.nombre}{' '}
                  {!plan.activo && <span className="ml-1 rounded bg-stone-100 px-1.5 py-0.5 text-xs text-stone-600">Inactivo</span>}
                </p>
                {plan.descripcion && <p className="text-sm text-stone-600">{plan.descripcion}</p>}
              </div>
              <Boton variante="secundario" onClick={() => setEditando(plan)}>
                Editar
              </Boton>
            </div>
            <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
              <dt className="text-stone-500">Videos por día</dt>
              <dd>{plan.videosPorDia}</dd>
              <dt className="text-stone-500">Regeneraciones</dt>
              <dd>{plan.regeneracionesPorVideo} por video</dd>
              <dt className="text-stone-500">Modelo de texto</dt>
              <dd>{plan.modeloTexto}</dd>
              <dt className="text-stone-500">Modelo de video</dt>
              <dd>{plan.modeloVideo}</dd>
              <dt className="text-stone-500">Precio de referencia</dt>
              <dd>{plan.precioReferencial == null ? '—' : formatearMonto(plan.precioReferencial)}</dd>
              <dt className="text-stone-500">Negocios</dt>
              <dd>{plan.negocios}</dd>
            </dl>
          </li>
        ))}
      </ul>
      {editando && <ModalPlan plan={editando === 'nuevo' ? null : editando} onCerrar={() => setEditando(null)} />}
    </section>
  )
}

function ModalPlan({ plan, onCerrar }: { plan: Plan | null; onCerrar: () => void }) {
  const clienteConsultas = useQueryClient()
  const integraciones = useQuery({ queryKey: ['integraciones'], queryFn: obtenerIntegraciones })
  const activos = (integraciones.data?.modelos ?? []).filter((modelo) => modelo.estado === 'ACTIVO')
  const [nombre, setNombre] = useState(plan?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(plan?.descripcion ?? '')
  const [videosPorDia, setVideosPorDia] = useState(String(plan?.videosPorDia ?? 1))
  const [regeneraciones, setRegeneraciones] = useState(String(plan?.regeneracionesPorVideo ?? 3))
  const [modeloTexto, setModeloTexto] = useState(plan ? String(plan.modeloTextoId) : '')
  const [modeloVideo, setModeloVideo] = useState(plan ? String(plan.modeloVideoId) : '')
  const [precio, setPrecio] = useState(plan?.precioReferencial == null ? '' : String(plan.precioReferencial))
  const [activo, setActivo] = useState(plan?.activo ?? true)

  const textoId = modeloTexto || String(activos.find((modelo) => modelo.tipo === 'TEXTO')?.id ?? '')
  const videoId = modeloVideo || String(activos.find((modelo) => modelo.tipo === 'VIDEO')?.id ?? '')

  const guardar = useMutation({
    mutationFn: () => {
      const solicitud = {
        nombre,
        descripcion: descripcion || null,
        videosPorDia: Number(videosPorDia),
        regeneracionesPorVideo: Number(regeneraciones),
        modeloTextoId: textoId ? Number(textoId) : null,
        modeloVideoId: videoId ? Number(videoId) : null,
        precioReferencial: precio ? Number(precio) : null,
        activo,
      }
      return plan ? actualizarPlan(plan.id, solicitud) : crearPlan(solicitud)
    },
    onSuccess: () => {
      clienteConsultas.invalidateQueries({ queryKey: ['planes'] })
      clienteConsultas.invalidateQueries({ queryKey: ['integraciones'] })
      clienteConsultas.invalidateQueries({ queryKey: ['negocios'] })
      onCerrar()
    },
  })

  // Un modelo inactivo que el plan ya tenía se muestra para no perderlo de vista, aunque no se pueda guardar así.
  const opciones = (tipo: 'TEXTO' | 'VIDEO', actual: number | undefined) =>
    (integraciones.data?.modelos ?? []).filter(
      (modelo) => modelo.tipo === tipo && (modelo.estado === 'ACTIVO' || modelo.id === actual),
    )

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    guardar.mutate()
  }

  return (
    <Modal titulo={plan ? `Editar plan ${plan.nombre}` : 'Crear plan'} abierto onCerrar={onCerrar}>
      <form onSubmit={enviar} className="space-y-4">
        <Campo etiqueta="Nombre" required maxLength={40} value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Pro" />
        <Campo etiqueta="Descripción" maxLength={300} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Videos por día" type="number" min={1} max={20} required value={videosPorDia} onChange={(e) => setVideosPorDia(e.target.value)} />
          <Campo etiqueta="Regeneraciones por video" type="number" min={0} max={20} required value={regeneraciones} onChange={(e) => setRegeneraciones(e.target.value)} />
        </div>
        <Selector etiqueta="Modelo de texto (guion)" required value={textoId} onChange={(e) => setModeloTexto(e.target.value)}>
          {opciones('TEXTO', plan?.modeloTextoId).map((modelo) => (
            <option key={modelo.id} value={modelo.id}>
              {modelo.nombre}
              {modelo.estado === 'INACTIVO' ? ' (inactivo)' : ''}
            </option>
          ))}
        </Selector>
        <Selector etiqueta="Modelo de video (clips)" required value={videoId} onChange={(e) => setModeloVideo(e.target.value)}>
          {opciones('VIDEO', plan?.modeloVideoId).map((modelo) => (
            <option key={modelo.id} value={modelo.id}>
              {modelo.nombre} · {modelo.costoReferencial} US$/s{modelo.estado === 'INACTIVO' ? ' (inactivo)' : ''}
            </option>
          ))}
        </Selector>
        <Campo
          etiqueta="Precio de referencia (US$, opcional)"
          type="number"
          min="0"
          step="0.01"
          value={precio}
          onChange={(e) => setPrecio(e.target.value)}
          ayuda="Solo de referencia: el monto de cada negocio se acuerda al crearlo."
        />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={activo} onChange={(e) => setActivo(e.target.checked)} className="size-4 accent-marca-700" />
          Activo (se puede elegir al crear un negocio o en un beneficio)
        </label>
        <p className="text-xs text-stone-500">Solo aparecen los modelos activos. Actívalos en Integraciones y claves.</p>
        {guardar.error && <Aviso tipo="error">{guardar.error.message}</Aviso>}
        <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
          <Boton type="button" variante="secundario" onClick={onCerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={guardar.isPending}>
            {plan ? 'Guardar' : 'Crear plan'}
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
