import { cliente } from '../../../shared/api/cliente'
import type { PageResponse, ParametrosPagina } from '../../../shared/api/paginacion'
import type { DatosUsuario, DesactivacionResponse, NuevoUsuario, Usuario } from '../types'

export async function listarUsuarios(parametros: ParametrosPagina): Promise<PageResponse<Usuario>> {
  const res = await cliente.get<PageResponse<Usuario>>('/usuarios', { params: parametros })
  return res.data
}

export async function obtenerUsuario(id: number): Promise<Usuario> {
  const res = await cliente.get<Usuario>(`/usuarios/${id}`)
  return res.data
}

export async function crearUsuario(datos: NuevoUsuario): Promise<Usuario> {
  const res = await cliente.post<Usuario>('/usuarios', datos)
  return res.data
}

export async function modificarUsuario(id: number, datos: DatosUsuario): Promise<Usuario> {
  const res = await cliente.put<Usuario>(`/usuarios/${id}`, datos)
  return res.data
}

export async function restablecerPassword(id: number, password: string): Promise<void> {
  await cliente.post(`/usuarios/${id}/restablecer-password`, { password })
}

/** CU-13d. Si es un técnico, la respuesta avisa cuántas OTs tiene su cuadrilla (3a). */
export async function desactivarUsuario(id: number): Promise<DesactivacionResponse> {
  const res = await cliente.post<DesactivacionResponse>(`/usuarios/${id}/desactivar`)
  return res.data
}

export async function activarUsuario(id: number): Promise<void> {
  await cliente.post(`/usuarios/${id}/activar`)
}
