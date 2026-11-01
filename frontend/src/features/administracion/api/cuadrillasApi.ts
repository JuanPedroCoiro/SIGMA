import { cliente } from '../../../shared/api/cliente'
import type { Cuadrilla, DatosCuadrilla } from '../types'

/** Catálogo chico: no se pagina. */
export async function listarCuadrillas(): Promise<Cuadrilla[]> {
  const res = await cliente.get<Cuadrilla[]>('/cuadrillas')
  return res.data
}

export async function crearCuadrilla(datos: DatosCuadrilla): Promise<Cuadrilla> {
  const res = await cliente.post<Cuadrilla>('/cuadrillas', datos)
  return res.data
}

export async function modificarCuadrilla(id: number, datos: DatosCuadrilla): Promise<Cuadrilla> {
  const res = await cliente.put<Cuadrilla>(`/cuadrillas/${id}`, datos)
  return res.data
}

export async function cambiarEstadoCuadrilla(
  id: number,
  accion: 'activar' | 'desactivar',
): Promise<void> {
  await cliente.post(`/cuadrillas/${id}/${accion}`)
}
