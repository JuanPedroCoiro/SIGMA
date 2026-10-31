import { cliente } from '../../../shared/api/cliente'
import type { PageResponse, ParametrosPagina } from '../../../shared/api/paginacion'
import type { Instalacion } from '../types'

export async function listarInstalaciones(
  parametros: ParametrosPagina,
): Promise<PageResponse<Instalacion>> {
  const res = await cliente.get<PageResponse<Instalacion>>('/instalaciones', { params: parametros })
  return res.data
}
