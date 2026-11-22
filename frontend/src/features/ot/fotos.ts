import { obtenerProblemDetail } from '../../shared/api/problemDetail'

// Fotos del incidente (#71). Los mismos límites que FotosIncidenteService en el backend; acá se
// controlan por el tipo que informa el navegador, y el backend vuelve a validar por el contenido.
// Las fotos del cierre técnico (#90) usan las mismas validaciones con su propio máximo.

export const MAXIMO_FOTOS = 3
export const TAMANIO_MAXIMO_FOTO = 10 * 1024 * 1024
export const TIPOS_FOTO: readonly string[] = ['image/jpeg', 'image/png', 'image/webp']

/** Una foto elegida en el registro, con su vista previa (una URL `blob:` que hay que liberar). */
export type FotoElegida = {
  archivo: File
  vistaPrevia: string
}

/**
 * Separa las fotos que se pueden agregar de las que no, con un mensaje por cada rechazo. Las que
 * pasan del máximo quedan afuera con un solo mensaje. El máximo es el de las fotos del incidente,
 * salvo que se indique otro (el cierre técnico admite más, #90).
 */
export function validarFotosNuevas(
  cantidadActual: number,
  nuevas: readonly File[],
  maximo = MAXIMO_FOTOS,
) {
  const aceptadas: File[] = []
  const errores: string[] = []
  let sobrantes = 0
  for (const archivo of nuevas) {
    const nombre = `«${archivo.name}»`
    if (!TIPOS_FOTO.includes(archivo.type)) {
      errores.push(`${nombre} no es una imagen JPG, PNG ni WEBP.`)
    } else if (archivo.size === 0) {
      errores.push(`${nombre} está vacía.`)
    } else if (archivo.size > TAMANIO_MAXIMO_FOTO) {
      errores.push(`${nombre} supera los 10 MB.`)
    } else if (cantidadActual + aceptadas.length >= maximo) {
      sobrantes++
    } else {
      aceptadas.push(archivo)
    }
  }
  if (sobrantes > 0) {
    errores.push(
      `Se pueden adjuntar hasta ${maximo} fotos: ` +
        (sobrantes === 1 ? 'quedó afuera 1.' : `quedaron afuera ${sobrantes}.`),
    )
  }
  return { aceptadas, errores }
}

/** El motivo de un rechazo al subir, tal como lo explica el backend. */
export function mensajeErrorFotos(error: unknown) {
  return obtenerProblemDetail(error)?.detail ?? 'Volvé a intentar en unos minutos.'
}

export function textoCantidadFotos(cantidad: number) {
  return cantidad === 1 ? '1 foto' : `${cantidad} fotos`
}
