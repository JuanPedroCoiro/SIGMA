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

/** Devuelve el ProblemDetail de un error de axios, o null si la respuesta no trae uno. */
export function obtenerProblemDetail(error: unknown): ProblemDetail | null {
  if (!isAxiosError(error)) return null
  const datos: unknown = error.response?.data
  if (typeof datos === 'object' && datos !== null && 'type' in datos && 'status' in datos) {
    return datos as ProblemDetail
  }
  return null
}
