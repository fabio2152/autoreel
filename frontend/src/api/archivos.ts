import { ErrorApi, pedir } from './cliente'

export type PropositoArchivo = 'LOGO' | 'FOTO_PRODUCTO' | 'FOTO_LOCAL' | 'COMPROBANTE_PAGO'

/** Donde el negocio pide la URL firmada; el panel usa la suya (por ejemplo, la del comprobante de un pago). */
export const URL_FIRMA_DEL_NEGOCIO = '/api/archivos/url-subida'

interface SubidaFirmada {
  clave: string
  url: string
  metodo: 'PUT'
  cabeceras: Record<string, string>
}

const pedirSubida = (urlDeFirma: string, proposito: PropositoArchivo, archivo: Blob) =>
  pedir<SubidaFirmada>(urlDeFirma, 'POST', {
    proposito,
    tipoContenido: archivo.type,
    tamano: archivo.size,
  })

/**
 * PUT directo al almacenamiento con la URL firmada. Usa XMLHttpRequest para informar el progreso y no envía
 * cookies ni el token CSRF de AutoReel: el destino es otro servidor.
 */
function enviar(subida: SubidaFirmada, archivo: Blob, alProgresar?: (porcentaje: number) => void): Promise<number> {
  return new Promise((resolver) => {
    const xhr = new XMLHttpRequest()
    xhr.open(subida.metodo, subida.url)
    xhr.withCredentials = false
    Object.entries(subida.cabeceras).forEach(([nombre, valor]) => xhr.setRequestHeader(nombre, valor))
    xhr.upload.onprogress = (evento) => {
      if (evento.lengthComputable && alProgresar) alProgresar(Math.round((evento.loaded / evento.total) * 100))
    }
    xhr.onload = () => resolver(xhr.status)
    xhr.onerror = () => resolver(0)
    xhr.send(archivo)
  })
}

/**
 * Sube un archivo y devuelve su clave temporal. El backend lo verifica cuando el perfil, el producto o el pago lo
 * usan. Si la subida falla (red o URL vencida), pide una URL nueva y reintenta una vez.
 */
export async function subirArchivo(
  proposito: PropositoArchivo,
  archivo: Blob,
  alProgresar?: (porcentaje: number) => void,
  urlDeFirma: string = URL_FIRMA_DEL_NEGOCIO,
): Promise<string> {
  for (let intento = 0; intento < 2; intento++) {
    const subida = await pedirSubida(urlDeFirma, proposito, archivo)
    const estado = await enviar(subida, archivo, alProgresar)
    if (estado >= 200 && estado < 300) return subida.clave
  }
  throw new ErrorApi(0, 'SUBIDA_FALLIDA', 'No pudimos subir el archivo. Revisa tu conexión e inténtalo de nuevo.')
}
