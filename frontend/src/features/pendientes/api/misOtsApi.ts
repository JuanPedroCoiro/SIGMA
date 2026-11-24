import { cliente } from '../../../shared/api/cliente'
import type { PageResponse, ParametrosPagina } from '../../../shared/api/paginacion'
import type { OrdenDeMiCuadrilla } from '../types'

/**
 * Bandeja «Mis OTs» (HU-07), solo el Técnico. El backend ordena por fecha de ejecución y
 * vencimiento e ignora `sort`.
 */
export async function listarMisOts(
  parametros: ParametrosPagina,
): Promise<PageResponse<OrdenDeMiCuadrilla>> {
  const res = await cliente.get<PageResponse<OrdenDeMiCuadrilla>>('/mis-ots', {
    params: parametros,
  })
  return res.data
}
