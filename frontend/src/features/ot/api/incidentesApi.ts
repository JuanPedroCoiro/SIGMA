import { cliente } from '../../../shared/api/cliente'
import type { CategoriaFalla, DatosIncidente, IncidenteResponse } from '../types'

/** Registra un incidente (CU-1): con OT, o en espera de un tercero (4b). */
export async function registrarIncidente(datos: DatosIncidente): Promise<IncidenteResponse> {
  const res = await cliente.post<IncidenteResponse>('/incidentes', datos)
  return res.data
}

/** Categorías de falla activas, ordenadas por nombre. Catálogo chico: sin paginar. */
export async function listarCategoriasFalla(): Promise<CategoriaFalla[]> {
  const res = await cliente.get<CategoriaFalla[]>('/categorias-falla')
  return res.data
}

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
