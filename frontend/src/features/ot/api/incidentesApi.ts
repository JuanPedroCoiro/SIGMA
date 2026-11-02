import { cliente } from '../../../shared/api/cliente'
import type { IncidenteResponse } from '../types'

/** Resuelve la espera de un tercero sin OT (CU-1 4b). */
export async function cerrarIncidenteSinOt(id: number): Promise<IncidenteResponse> {
  const res = await cliente.post<IncidenteResponse>(`/incidentes/${id}/cerrar-sin-ot`)
  return res.data
}

/** Resuelve la espera de un tercero con una OT: el SLA se cuenta desde ahora (CU-1 4b). */
export async function generarOtDeIncidente(id: number): Promise<IncidenteResponse> {
  const res = await cliente.post<IncidenteResponse>(`/incidentes/${id}/generar-ot`)
  return res.data
}
