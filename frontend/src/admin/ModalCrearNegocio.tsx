import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { crearNegocio, listarNichos, listarPlanes, type ContrasenaTemporal } from '../api/admin'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo, Selector } from '../componentes/Campo'
import { Modal } from '../componentes/Modal'

interface Props {
  abierto: boolean
  onCerrar: () => void
  onCreado: (credenciales: ContrasenaTemporal) => void
}

const NICHO_POR_DEFECTO = 'PASTELERIA'

/** RF-03: nicho (Pastelería marcado), nombre, correo, plan (Lite marcado), monto mensual y fecha del primer pago. */
export function ModalCrearNegocio({ abierto, onCerrar, onCreado }: Props) {
  const clienteConsultas = useQueryClient()
  const nichos = useQuery({ queryKey: ['nichos'], queryFn: listarNichos, enabled: abierto })
  const planes = useQuery({ queryKey: ['planes'], queryFn: listarPlanes, enabled: abierto })
  const planesActivos = (planes.data ?? []).filter((plan) => plan.activo)

  const [nicho, setNicho] = useState(NICHO_POR_DEFECTO)
  const [nombre, setNombre] = useState('')
  const [correo, setCorreo] = useState('')
  const [planElegido, setPlanElegido] = useState('')
  const [monto, setMonto] = useState('')
  const [fechaPrimerPago, setFechaPrimerPago] = useState('')

  // El plan Lite (el primero activo) queda marcado por defecto.
  const planId = planElegido || (planesActivos[0] ? String(planesActivos[0].id) : '')

  const reiniciar = () => {
    setNicho(NICHO_POR_DEFECTO)
    setNombre('')
    setCorreo('')
    setPlanElegido('')
    setMonto('')
    setFechaPrimerPago('')
  }

  const crear = useMutation({
    mutationFn: () =>
      crearNegocio({
        nicho,
        nombre,
        correo,
        planId: planId ? Number(planId) : null,
        montoMensual: monto ? Number(monto) : null,
        fechaPrimerPago: fechaPrimerPago || null,
      }),
    onSuccess: (credenciales) => {
      clienteConsultas.invalidateQueries({ queryKey: ['negocios'] })
      reiniciar()
      crear.reset()
      onCreado(credenciales)
    },
  })

  const cerrar = () => {
    crear.reset()
    onCerrar()
  }

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    crear.mutate()
  }

  return (
    <Modal titulo="Crear negocio" abierto={abierto} onCerrar={cerrar}>
      <form onSubmit={enviar} className="space-y-4">
        <Selector etiqueta="Nicho" value={nicho} onChange={(e) => setNicho(e.target.value)} required>
          {(nichos.data ?? [{ id: NICHO_POR_DEFECTO, nombre: 'Pastelería' }]).map((opcion) => (
            <option key={opcion.id} value={opcion.id}>
              {opcion.nombre}
            </option>
          ))}
        </Selector>
        <Campo
          etiqueta="Nombre del negocio"
          required
          maxLength={120}
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          placeholder="Pastelería Dulce Encanto"
        />
        <Campo
          etiqueta="Correo del usuario"
          type="email"
          required
          maxLength={254}
          value={correo}
          onChange={(e) => setCorreo(e.target.value)}
          placeholder="contacto@pasteleria.pe"
          ayuda="Con este correo el negocio iniciará sesión. No se puede repetir."
        />
        <Selector
          etiqueta="Plan"
          value={planId}
          onChange={(e) => setPlanElegido(e.target.value)}
          required
          disabled={planes.isPending}
        >
          {planesActivos.map((plan) => (
            <option key={plan.id} value={plan.id}>
              {plan.nombre} · {plan.videosPorDia} video por día · {plan.modeloVideo}
            </option>
          ))}
        </Selector>
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo
            etiqueta="Monto mensual (US$)"
            type="number"
            inputMode="decimal"
            min="0.01"
            step="0.01"
            required
            value={monto}
            onChange={(e) => setMonto(e.target.value)}
            placeholder="49.00"
          />
          <Campo
            etiqueta="Fecha del primer pago"
            type="date"
            required
            value={fechaPrimerPago}
            onChange={(e) => setFechaPrimerPago(e.target.value)}
          />
        </div>
        {(planes.error || nichos.error) && <Aviso tipo="error">No pudimos cargar los planes. Recarga la página.</Aviso>}
        {crear.error && <Aviso tipo="error">{crear.error.message}</Aviso>}
        <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
          <Boton type="button" variante="secundario" onClick={cerrar}>
            Cancelar
          </Boton>
          <Boton type="submit" cargando={crear.isPending}>
            Crear negocio
          </Boton>
        </div>
      </form>
    </Modal>
  )
}
