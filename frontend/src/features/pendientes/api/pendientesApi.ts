import { cliente } from '../../../shared/api/cliente'
import type { PageResponse, ParametrosPagina } from '../../../shared/api/paginacion'
import type { CriterioOrden, IncidenteEnEspera, OrdenPendiente } from '../types'

/** El orden va en `orden`, no en `sort`: los criterios no son columnas. */
export async function listarOrdenesPendientes(
  orden: CriterioOrden,
  parametros: ParametrosPagina,
): Promise<PageResponse<OrdenPendiente>> {
  const res = await cliente.get<PageResponse<OrdenPendiente>>('/pendientes/ordenes', {
    params: { ...parametros, orden },
  })
  return res.data
}

export async function listarIncidentesEnEspera(): Promise<IncidenteEnEspera[]> {
  const res = await cliente.get<IncidenteEnEspera[]>('/pendientes/incidentes-en-espera')
  return res.data
}
