import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  cambiarPlanDeNegocio,
  listarPlanes,
  otorgarBeneficio,
  registrarPago,
  type MedioPago,
  type NegocioDetalle,
} from '../api/admin'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo, Selector } from '../componentes/Campo'
import { aEntradaLocal, formatearFecha, hoyEnLima } from '../componentes/formato'
import { Modal } from '../componentes/Modal'
import { MEDIOS } from './medios'


interface Props {
  negocio: NegocioDetalle
  abierto: boolean
  onCerrar: () => void
}

function Botones({ onCancelar, cargando, texto }: { onCancelar: () => void; cargando: boolean; texto: string }) {
  return (
    <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
      <Boton type="button" variante="secundario" onClick={onCancelar}>
        Cancelar
      </Boton>
      <Boton type="submit" cargando={cargando}>
        {texto}
      </Boton>
    </div>
  )
}

const estiloTexto =
  'block w-full rounded-lg border-0 bg-white px-3 py-2 text-base text-stone-900 ring-1 ring-inset ring-stone-300 placeholder:text-stone-400 focus:ring-2 focus:ring-inset focus:ring-marca-600 sm:text-sm'

/** RF-08: el pago cubre la próxima fecha de pago y la hace avanzar un mes (RN-42). */
export function ModalRegistrarPago({ negocio, abierto, onCerrar }: Props) {
  const clienteConsultas = useQueryClient()
  const [fecha, setFecha] = useState(hoyEnLima)
  const [monto, setMonto] = useState(String(negocio.suscripcion.montoMensual))
  const [medio, setMedio] = useState<MedioPago | ''>('')
  const [nota, setNota] = useState('')

  const registrar = useMutation({
    mutationFn: () =>
      registrarPago(negocio.id, { fechaPago: fecha, monto: monto ? Number(monto) : null, medio: medio || null, nota }),
    onSuccess: () => {
      clienteConsultas.invalidateQueries({ queryKey: ['negocios'] })
      clienteConsultas.invalidateQueries({ queryKey: ['notificaciones'] })
      cerrar()
    },
  })

  const cerrar = () => {
    registrar.reset()
    setMedio('')
    setNota('')
    onCerrar()
  }

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    registrar.mutate()
  }

  return (
    <Modal titulo="Registrar pago" abierto={abierto} onCerrar={cerrar}>
      <form onSubmit={enviar} className="space-y-4">
        <Aviso>
          Cubre el pago del <strong>{formatearFecha(negocio.suscripcion.proximaFechaPago)}</strong>. La próxima fecha
          de pago pasará al mes siguiente, el día {negocio.suscripcion.diaPago} (o el último día del mes).
        </Aviso>
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Fecha del pago" type="date" required max={hoyEnLima()} value={fecha} onChange={(e) => setFecha(e.target.value)} />
          <Campo
            etiqueta="Monto (US$)"
            type="number"
            inputMode="decimal"
            min="0.01"
            step="0.01"
            required
            value={monto}
            onChange={(e) => setMonto(e.target.value)}
          />
        </div>
        <Selector etiqueta="Medio de pago" required value={medio} onChange={(e) => setMedio(e.target.value as MedioPago)}>
          <option value="" disabled>
            Elige el medio
          </option>
          {MEDIOS.map((opcion) => (
            <option key={opcion.valor} value={opcion.valor}>
              {opcion.texto}
            </option>
          ))}
        </Selector>
        <div>
          <label htmlFor="nota-pago" className="mb-1 block text-sm font-medium text-stone-700">
            Nota (opcional)
          </label>
          <textarea id="nota-pago" rows={2} maxLength={300} value={nota} onChange={(e) => setNota(e.target.value)} className={estiloTexto} placeholder="Número de operación, comentario…" />
        </div>
        {registrar.error && <Aviso tipo="error">{registrar.error.message}</Aviso>}
        <Botones onCancelar={cerrar} cargando={registrar.isPending} texto="Registrar pago" />
      </form>
    </Modal>
  )
}

/** RN-49: el plan nuevo rige para los videos que se creen desde ahora. */
export function ModalCambiarPlan({ negocio, abierto, onCerrar }: Props) {
  const clienteConsultas = useQueryClient()
  const planes = useQuery({ queryKey: ['planes'], queryFn: listarPlanes, enabled: abierto })
  const [planElegido, setPlanElegido] = useState('')
  const [monto, setMonto] = useState(String(negocio.suscripcion.montoMensual))
  const planId = planElegido || String(negocio.suscripcion.planId)

  const cambiar = useMutation({
    mutationFn: () => cambiarPlanDeNegocio(negocio.id, { planId: Number(planId), montoMensual: Number(monto) }),
    onSuccess: () => {
      clienteConsultas.invalidateQueries({ queryKey: ['negocios'] })
      cerrar()
    },
  })

  const cerrar = () => {
    cambiar.reset()
    setPlanElegido('')
    onCerrar()
  }

  return (
    <Modal titulo="Cambiar plan" abierto={abierto} onCerrar={cerrar}>
      <form
        onSubmit={(evento) => {
          evento.preventDefault()
          cambiar.mutate()
        }}
        className="space-y-4"
      >
        <Selector etiqueta="Plan" required value={planId} onChange={(e) => setPlanElegido(e.target.value)}>
          {(planes.data ?? [])
            .filter((plan) => plan.activo || plan.id === negocio.suscripcion.planId)
            .map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.nombre} · {plan.videosPorDia} por día · {plan.modeloVideo}
              </option>
            ))}
        </Selector>
        <Campo
          etiqueta="Monto mensual (US$)"
          type="number"
          inputMode="decimal"
          min="0.01"
          step="0.01"
          required
          value={monto}
          onChange={(e) => setMonto(e.target.value)}
        />
        <p className="text-sm text-stone-600">
          Rige para los videos que el negocio cree desde ahora. Los videos en proceso terminan con el plan con el que
          empezaron. La fecha de pago no cambia.
        </p>
        {cambiar.error && <Aviso tipo="error">{cambiar.error.message}</Aviso>}
        <Botones onCancelar={cerrar} cargando={cambiar.isPending} texto="Guardar" />
      </form>
    </Modal>
  )
}

/** RF-12 y RN-51: otro plan durante un período de hasta 30 días, con motivo. */
export function ModalOtorgarBeneficio({ negocio, abierto, onCerrar }: Props) {
  const clienteConsultas = useQueryClient()
  const planes = useQuery({ queryKey: ['planes'], queryFn: listarPlanes, enabled: abierto })
  const opciones = (planes.data ?? []).filter((plan) => plan.activo && plan.id !== negocio.suscripcion.planId)
  const [planElegido, setPlanElegido] = useState('')
  const [desde, setDesde] = useState(() => aEntradaLocal(new Date()))
  const [hasta, setHasta] = useState(() => aEntradaLocal(new Date(Date.now() + 24 * 3600_000)))
  const [motivo, setMotivo] = useState('')
  const planId = planElegido || (opciones[0] ? String(opciones[0].id) : '')

  const otorgar = useMutation({
    mutationFn: () =>
      otorgarBeneficio(negocio.id, {
        planId: planId ? Number(planId) : null,
        desde: new Date(desde).toISOString(),
        hasta: new Date(hasta).toISOString(),
        motivo,
      }),
    onSuccess: () => {
      clienteConsultas.invalidateQueries({ queryKey: ['negocios'] })
      cerrar()
    },
  })

  const cerrar = () => {
    otorgar.reset()
    setMotivo('')
    onCerrar()
  }

  return (
    <Modal titulo="Otorgar beneficio" abierto={abierto} onCerrar={cerrar}>
      <form
        onSubmit={(evento) => {
          evento.preventDefault()
          otorgar.mutate()
        }}
        className="space-y-4"
      >
        {planes.isSuccess && opciones.length === 0 ? (
          <Aviso>No hay otro plan activo. Crea uno en Ajustes → Planes.</Aviso>
        ) : (
          <Selector etiqueta="Plan del beneficio" required value={planId} onChange={(e) => setPlanElegido(e.target.value)}>
            {opciones.map((plan) => (
              <option key={plan.id} value={plan.id}>
                {plan.nombre} · {plan.videosPorDia} por día · {plan.modeloVideo}
              </option>
            ))}
          </Selector>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Desde" type="datetime-local" required value={desde} onChange={(e) => setDesde(e.target.value)} />
          <Campo etiqueta="Hasta" type="datetime-local" required value={hasta} onChange={(e) => setHasta(e.target.value)} ayuda="Máximo 30 días." />
        </div>
        <div>
          <label htmlFor="motivo-beneficio" className="mb-1 block text-sm font-medium text-stone-700">
            Motivo
          </label>
          <textarea
            id="motivo-beneficio"
            rows={2}
            maxLength={300}
            required
            value={motivo}
            onChange={(e) => setMotivo(e.target.value)}
            className={estiloTexto}
            placeholder="Cortesía por ser cliente antiguo, compensación por un inconveniente…"
          />
        </div>
        <p className="text-sm text-stone-600">
          Cambia los modelos, los videos por día y las regeneraciones. No cambia el monto ni la fecha de pago. Al
          terminar, el negocio vuelve solo a su plan.
        </p>
        {otorgar.error && <Aviso tipo="error">{otorgar.error.message}</Aviso>}
        <Botones onCancelar={cerrar} cargando={otorgar.isPending} texto="Otorgar beneficio" />
      </form>
    </Modal>
  )
}
