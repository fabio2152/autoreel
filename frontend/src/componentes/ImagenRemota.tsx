import { useQueryClient, type QueryKey } from '@tanstack/react-query'
import { useState, type ImgHTMLAttributes } from 'react'

interface Props extends ImgHTMLAttributes<HTMLImageElement> {
  /** Consulta que trajo la URL firmada: si la imagen no carga (URL vencida), se vuelve a pedir una vez. */
  consulta: QueryKey
}

export function ImagenRemota({ consulta, onError, ...resto }: Props) {
  const clienteConsultas = useQueryClient()
  const [reintentada, setReintentada] = useState(false)
  return (
    <img
      {...resto}
      onError={(evento) => {
        if (!reintentada) {
          setReintentada(true)
          clienteConsultas.invalidateQueries({ queryKey: consulta })
        }
        onError?.(evento)
      }}
    />
  )
}
