import { cliente } from '../../../shared/api/cliente'
import type { Cuadrilla } from '../types'

/** Catálogo chico: no se pagina. */
export async function listarCuadrillas(): Promise<Cuadrilla[]> {
  const res = await cliente.get<Cuadrilla[]>('/cuadrillas')
  return res.data
}
