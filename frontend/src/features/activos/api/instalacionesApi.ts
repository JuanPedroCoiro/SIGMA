import { cliente } from '../../../shared/api/cliente'
import type { PageResponse, ParametrosPagina } from '../../../shared/api/paginacion'
import type {
  EntradaHistorial,
  FichaInstalacion,
  Instalacion,
  ResultadoImportacion,
} from '../types'

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

/** Instalaciones activas cuyo código o nombre contiene el texto (HU-03), las primeras 10. */
export async function buscarInstalaciones(texto: string): Promise<PageResponse<Instalacion>> {
  const res = await cliente.get<PageResponse<Instalacion>>('/instalaciones', {
    params: { buscar: texto, size: 10, sort: 'codigo,asc' },
  })
  return res.data
}

/** Instalaciones activas con sus coordenadas, sin paginar, para el mapa (HU-11, #70). */
export async function listarInstalacionesMapa(): Promise<Instalacion[]> {
  const res = await cliente.get<Instalacion[]>('/instalaciones/mapa')
  return res.data
}

/** Ficha de una instalación con sus OTs en curso (HU-10, CU-7), para todos los roles. */
export async function obtenerFichaInstalacion(id: number): Promise<FichaInstalacion> {
  const res = await cliente.get<FichaInstalacion>(`/instalaciones/${id}/ficha`)
  return res.data
}

/** Historial de la instalación (HU-10, CU-8), de lo más nuevo a lo más viejo; el orden es fijo. */
export async function listarHistorialInstalacion(
  id: number,
  parametros: ParametrosPagina,
): Promise<PageResponse<EntradaHistorial>> {
  const res = await cliente.get<PageResponse<EntradaHistorial>>(`/instalaciones/${id}/historial`, {
    params: parametros,
  })
  return res.data
}
