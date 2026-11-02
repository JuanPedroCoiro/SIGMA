import { cliente } from '../../../shared/api/cliente'
import type { DatosPausa, OrdenTrabajoDetalle } from '../types'

export async function obtenerOrden(id: number): Promise<OrdenTrabajoDetalle> {
  const res = await cliente.get<OrdenTrabajoDetalle>(`/ordenes/${id}`)
  return res.data
}

export async function pausarOrden(id: number, datos: DatosPausa): Promise<OrdenTrabajoDetalle> {
  const res = await cliente.post<OrdenTrabajoDetalle>(`/ordenes/${id}/pausar`, datos)
  return res.data
}

export async function reanudarOrden(id: number): Promise<OrdenTrabajoDetalle> {
  const res = await cliente.post<OrdenTrabajoDetalle>(`/ordenes/${id}/reanudar`)
  return res.data
}

/** CU-19, solo la Jefatura. Responde el detalle de la OT ya anulada. */
export async function anularOrden(id: number, motivo: string): Promise<OrdenTrabajoDetalle> {
  const res = await cliente.post<OrdenTrabajoDetalle>(`/ordenes/${id}/anular`, { motivo })
  return res.data
}
