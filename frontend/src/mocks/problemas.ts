import { HttpResponse } from 'msw/http'
import { TIPOS_PROBLEMA } from '../shared/api/problemDetail'
import { base, type UsuarioSimulado } from './datos'

type Tipo = (typeof TIPOS_PROBLEMA)[keyof typeof TIPOS_PROBLEMA]

/** ProblemDetail como el que arma el backend, con el `type` acordado. */
export function problema(
  status: number,
  tipo: Tipo,
  title: string,
  detail: string,
  extra: Record<string, unknown> = {},
) {
  return HttpResponse.json(
    { type: `/problemas/${tipo}`, title, status, detail, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export const sinSesion = () =>
  problema(401, TIPOS_PROBLEMA.sinSesion, 'No autenticado', 'Iniciá sesión para continuar.')

export const sinPermiso = () =>
  problema(
    403,
    TIPOS_PROBLEMA.sinPermiso,
    'Acceso denegado',
    'No tenés permiso para realizar esta operación.',
  )

export function usuarioEnSesion(): UsuarioSimulado | undefined {
  return base.usuarios.find((u) => u.id === base.idSesion)
}
