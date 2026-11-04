import { useState } from 'react'
import type { ContrasenaTemporal } from '../api/admin'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { formatearFechaHora } from '../componentes/formato'
import { Modal } from '../componentes/Modal'

interface Props {
  titulo: string
  credenciales: ContrasenaTemporal | null
  onCerrar: () => void
}

function mensajeParaCliente({ correo, contrasenaTemporal, enlace }: ContrasenaTemporal) {
  return [
    '¡Hola! Ya puedes entrar a AutoReel.',
    `Enlace: ${enlace}`,
    `Correo: ${correo}`,
    `Contraseña temporal: ${contrasenaTemporal}`,
    'Al entrar te pediremos crear tu propia contraseña. La contraseña temporal vence en 48 horas.',
  ].join('\n')
}

/**
 * RN-02: la contraseña temporal se muestra una sola vez. Solo vive en el estado de esta ventana y se descarta
 * al cerrarla.
 */
export function ModalContrasenaTemporal({ titulo, credenciales, onCerrar }: Props) {
  const [copia, setCopia] = useState<'pendiente' | 'copiado' | 'manual'>('pendiente')

  const cerrar = () => {
    setCopia('pendiente')
    onCerrar()
  }

  const copiar = async () => {
    if (!credenciales) return
    try {
      await navigator.clipboard.writeText(mensajeParaCliente(credenciales))
      setCopia('copiado')
    } catch {
      // El navegador puede bloquear el portapapeles: se muestra el mensaje para copiarlo a mano.
      setCopia('manual')
    }
  }

  return (
    <Modal titulo={titulo} abierto={credenciales !== null} onCerrar={cerrar}>
      {credenciales && (
        <div className="space-y-4">
          <Aviso tipo="info">
            Esta contraseña se muestra <strong>una sola vez</strong>. Cópiala y entrégasela al cliente.
          </Aviso>
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="text-stone-500">Correo</dt>
              <dd className="font-medium break-all">{credenciales.correo}</dd>
            </div>
            <div>
              <dt className="text-stone-500">Contraseña temporal</dt>
              <dd className="mt-1 rounded-lg bg-stone-100 px-3 py-2 font-mono text-lg tracking-wider select-all">
                {credenciales.contrasenaTemporal}
              </dd>
            </div>
            <div>
              <dt className="text-stone-500">Enlace</dt>
              <dd className="font-medium break-all">{credenciales.enlace}</dd>
            </div>
            <div>
              <dt className="text-stone-500">Vence</dt>
              <dd>{formatearFechaHora(credenciales.vence)}</dd>
            </div>
          </dl>
          {copia === 'copiado' && (
            <Aviso tipo="exito">Mensaje copiado. Pégalo en WhatsApp o en un correo para el cliente.</Aviso>
          )}
          {copia === 'manual' && (
            <div className="space-y-2">
              <Aviso tipo="info">No pudimos copiar automáticamente. Selecciona el mensaje y cópialo.</Aviso>
              <textarea
                readOnly
                rows={6}
                value={mensajeParaCliente(credenciales)}
                onFocus={(evento) => evento.currentTarget.select()}
                aria-label="Mensaje para el cliente"
                className="w-full rounded-lg bg-stone-50 p-3 font-mono text-xs ring-1 ring-stone-300"
              />
            </div>
          )}
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Boton variante="secundario" onClick={cerrar}>
              Listo
            </Boton>
            <Boton onClick={copiar}>Copiar mensaje para el cliente</Boton>
          </div>
        </div>
      )}
    </Modal>
  )
}
