import { cliente } from '../../../shared/api/cliente'
import type { CategoriaFalla, DatosIncidente, FotoIncidente, IncidenteResponse } from '../types'

/** Sube fotos al incidente (#71), en el campo «fotos»: se suben todas o ninguna. */
export async function subirFotosIncidente(
  idIncidente: number,
  archivos: readonly File[],
): Promise<FotoIncidente[]> {
  const formulario = new FormData()
  archivos.forEach((archivo) => formulario.append('fotos', archivo))
  const res = await cliente.post<FotoIncidente[]>(`/incidentes/${idIncidente}/fotos`, formulario)
  return res.data
}

export async function listarFotosIncidente(idIncidente: number): Promise<FotoIncidente[]> {
  const res = await cliente.get<FotoIncidente[]>(`/incidentes/${idIncidente}/fotos`)
  return res.data
}

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
