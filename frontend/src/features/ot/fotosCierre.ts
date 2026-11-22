import type { MomentoFoto } from './types'

// Fotos del cierre técnico (#90, HU-08). El máximo es el de FotosCierreService en el backend.

export const MAXIMO_FOTOS_CIERRE = 6
/** Lado mayor después de comprimir: alcanza para ver el detalle de una falla en pantalla. */
export const LADO_MAXIMO = 1600
export const CALIDAD_JPEG = 0.8

/** Una foto elegida para el cierre, con su momento y su vista previa (una URL `blob:`). */
export type FotoParaCierre = {
  archivo: File
  momento: MomentoFoto
  vistaPrevia: string
}

/** Las medidas para que el lado mayor no pase de `ladoMaximo`, sin agrandar las chicas. */
export function medidasReducidas(ancho: number, alto: number, ladoMaximo = LADO_MAXIMO) {
  const escala = Math.min(1, ladoMaximo / Math.max(ancho, alto))
  return { ancho: Math.round(ancho * escala), alto: Math.round(alto * escala) }
}

/**
 * Comprime la foto antes de enviarla, para ahorrar datos móviles: lado mayor de hasta 1600 px y
 * JPEG de calidad 0,8. Si el navegador no puede, o la comprimida no queda más chica, se envía la
 * original: el backend igual la valida.
 */
export async function comprimirFoto(archivo: File): Promise<File> {
  try {
    const imagen = await createImageBitmap(archivo)
    const { ancho, alto } = medidasReducidas(imagen.width, imagen.height)
    const lienzo = document.createElement('canvas')
    lienzo.width = ancho
    lienzo.height = alto
    const contexto = lienzo.getContext('2d')
    if (!contexto) return archivo
    contexto.drawImage(imagen, 0, 0, ancho, alto)
    imagen.close()
    const comprimida = await new Promise<Blob | null>((resolver) =>
      lienzo.toBlob(resolver, 'image/jpeg', CALIDAD_JPEG),
    )
    if (!comprimida || comprimida.size >= archivo.size) return archivo
    const nombre = archivo.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([comprimida], nombre, { type: 'image/jpeg' })
  } catch {
    return archivo
  }
}
