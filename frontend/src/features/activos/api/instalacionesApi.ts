import { cliente } from '../../../shared/api/cliente'
import type { PageResponse, ParametrosPagina } from '../../../shared/api/paginacion'
import type { Instalacion, ResultadoImportacion } from '../types'

export async function listarInstalaciones(
  parametros: ParametrosPagina,
): Promise<PageResponse<Instalacion>> {
  const res = await cliente.get<PageResponse<Instalacion>>('/instalaciones', { params: parametros })
  return res.data
}

/** Sube la planilla CSV (HU-02, RF-29). Con errores responde 422 y no importa nada. */
export async function importarInstalaciones(archivo: File): Promise<ResultadoImportacion> {
  const datos = new FormData()
  datos.append('archivo', archivo)
  const res = await cliente.post<ResultadoImportacion>('/instalaciones/importar', datos)
  return res.data
}
