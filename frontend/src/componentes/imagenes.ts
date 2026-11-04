/**
 * Preparación de imágenes en el navegador antes de subirlas. El backend vuelve a verificar todo; esto solo da una
 * respuesta rápida y sube archivos más livianos.
 */

export const LADO_MENOR_FOTO = 1080
const LADO_MAYOR_NORMALIZADO = 2560
const CALIDAD_JPEG = 0.88
const MB = 1024 * 1024
export const TIPOS_FOTO_ADMITIDOS = ['image/jpeg', 'image/png', 'image/webp']

export class ErrorDeImagen extends Error {}

function esHeic(archivo: File) {
  return /\.(heic|heif)$/i.test(archivo.name) || /image\/hei[cf]/.test(archivo.type)
}

async function abrir(archivo: Blob): Promise<ImageBitmap> {
  try {
    // "from-image" aplica la orientación de la cámara (EXIF) al leer la foto.
    return await createImageBitmap(archivo, { imageOrientation: 'from-image' })
  } catch {
    throw new ErrorDeImagen('No pudimos abrir la imagen. Prueba con otra foto en JPG o PNG.')
  }
}

/**
 * Foto de producto o del local: aplica la orientación de la cámara, reduce el lado mayor a 2560 px (sin bajar el
 * lado menor de 1080), la exporta como JPEG y, al redibujarla, le quita los metadatos (incluida la ubicación GPS).
 */
export async function normalizarFoto(archivo: File): Promise<{ foto: Blob; horizontal: boolean }> {
  if (esHeic(archivo)) {
    throw new ErrorDeImagen(
      'Las fotos HEIC del iPhone no se pueden usar todavía. En Ajustes > Cámara > Formatos elige "Más compatible", o comparte la foto como JPG.',
    )
  }
  if (!TIPOS_FOTO_ADMITIDOS.includes(archivo.type)) {
    throw new ErrorDeImagen('Ese archivo no es una foto. Sube una imagen JPG, PNG o WebP.')
  }
  const imagen = await abrir(archivo)
  const { width: anchoOriginal, height: altoOriginal } = imagen
  const menor = Math.min(anchoOriginal, altoOriginal)
  const mayor = Math.max(anchoOriginal, altoOriginal)
  if (menor < LADO_MENOR_FOTO) {
    imagen.close()
    throw new ErrorDeImagen(
      `Tu foto mide ${anchoOriginal} × ${altoOriginal} px. Necesitamos al menos ${LADO_MENOR_FOTO} px en el lado más corto. Tómala con la cámara del celular, sin recortarla.`,
    )
  }
  const escala = Math.max(Math.min(1, LADO_MAYOR_NORMALIZADO / mayor), LADO_MENOR_FOTO / menor)
  const ancho = Math.round(imagen.width * escala)
  const alto = Math.round(imagen.height * escala)
  const lienzo = document.createElement('canvas')
  lienzo.width = ancho
  lienzo.height = alto
  const contexto = lienzo.getContext('2d')
  if (!contexto) throw new ErrorDeImagen('Tu navegador no pudo procesar la foto.')
  contexto.drawImage(imagen, 0, 0, ancho, alto)
  imagen.close()
  const foto = await new Promise<Blob | null>((resolver) => lienzo.toBlob(resolver, 'image/jpeg', CALIDAD_JPEG))
  if (!foto) throw new ErrorDeImagen('Tu navegador no pudo procesar la foto.')
  if (foto.size > 10 * MB) throw new ErrorDeImagen('La foto pesa más de 10 MB. Prueba con otra.')
  return { foto, horizontal: ancho > alto }
}

/** El logo se sube tal cual (PNG, para conservar la transparencia). */
export async function revisarLogo(archivo: File): Promise<File> {
  if (archivo.type !== 'image/png') {
    throw new ErrorDeImagen('El logo debe ser una imagen PNG, idealmente con fondo transparente.')
  }
  if (archivo.size > 5 * MB) throw new ErrorDeImagen('El logo pesa más de 5 MB.')
  const imagen = await abrir(archivo)
  const menor = Math.min(imagen.width, imagen.height)
  imagen.close()
  if (menor < 200) {
    throw new ErrorDeImagen('Tu logo es muy pequeño: necesitamos al menos 200 px en el lado más corto.')
  }
  return archivo
}

/**
 * Dos colores dominantes del logo, ignorando lo transparente y lo casi blanco o casi negro. Se calcula sobre el
 * archivo local (no sobre la URL del almacenamiento, que es de otro origen).
 */
export async function coloresDelLogo(archivo: Blob): Promise<[string, string] | null> {
  const imagen = await abrir(archivo)
  const lado = 64
  const lienzo = document.createElement('canvas')
  lienzo.width = lado
  lienzo.height = lado
  const contexto = lienzo.getContext('2d', { willReadFrequently: true })
  if (!contexto) return null
  contexto.drawImage(imagen, 0, 0, lado, lado)
  imagen.close()
  const { data } = contexto.getImageData(0, 0, lado, lado)
  const conteo = new Map<string, { r: number; g: number; b: number; n: number }>()
  for (let i = 0; i < data.length; i += 4) {
    const [r, g, b, a] = [data[i], data[i + 1], data[i + 2], data[i + 3]]
    const luz = (r + g + b) / 3
    if (a < 128 || luz > 240 || luz < 15) continue
    const clave = `${r >> 5}-${g >> 5}-${b >> 5}`
    const grupo = conteo.get(clave) ?? { r: 0, g: 0, b: 0, n: 0 }
    conteo.set(clave, { r: grupo.r + r, g: grupo.g + g, b: grupo.b + b, n: grupo.n + 1 })
  }
  const ordenados = [...conteo.values()].sort((a, b) => b.n - a.n)
  if (ordenados.length === 0) return null
  const aHex = ({ r, g, b, n }: { r: number; g: number; b: number; n: number }) =>
    '#' + [r, g, b].map((c) => Math.round(c / n).toString(16).padStart(2, '0')).join('')
  const primero = aHex(ordenados[0])
  const segundo = ordenados.find((grupo) => aHex(grupo) !== primero)
  return [primero, segundo ? aHex(segundo) : '#ffffff']
}
