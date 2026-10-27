import { isAxiosError } from 'axios'

/** Error de la API según RFC 9457, como lo arma el backend con ProblemDetail. */
export type ProblemDetail = {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  [extension: string]: unknown
}

/** Error de validación de un campo (400), en la propiedad `errores` del ProblemDetail. */
export type ErrorCampo = {
  campo: string
  mensaje: string
}

/**
 * Tipos de error acordados con el backend: el último tramo del `type` del ProblemDetail
 * (por ejemplo `.../problemas/cuenta-desactivada`).
 */
export const TIPOS_PROBLEMA = {
  credenciales: 'credenciales',
  cuentaDesactivada: 'cuenta-desactivada',
  usuarioBloqueado: 'usuario-bloqueado',
  sinSesion: 'sin-sesion',
  sinPermiso: 'sin-permiso',
  csrf: 'csrf',
  validacion: 'validacion',
  correoDuplicado: 'correo-duplicado',
} as const

/** Devuelve el ProblemDetail de un error de axios, o null si la respuesta no trae uno. */
export function obtenerProblemDetail(error: unknown): ProblemDetail | null {
  if (!isAxiosError(error)) return null
  const datos: unknown = error.response?.data
  if (typeof datos === 'object' && datos !== null && 'type' in datos && 'status' in datos) {
    return datos as ProblemDetail
  }
  return null
}

/** El tipo del error (ver TIPOS_PROBLEMA), o null si no tiene uno propio (`about:blank`). */
export function tipoProblema(problema: ProblemDetail | null): string | null {
  if (!problema || !problema.type || problema.type === 'about:blank') return null
  return problema.type.split(/[/:]/).pop() || null
}

/** Los errores por campo de una respuesta 400, o una lista vacía. */
export function erroresDeCampo(problema: ProblemDetail | null): ErrorCampo[] {
  const errores = problema?.errores
  return Array.isArray(errores) ? (errores as ErrorCampo[]) : []
}
