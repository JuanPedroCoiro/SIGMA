import { cliente } from '../../../shared/api/cliente'
import type {
  CargaCuadrilla,
  DatosAsignacion,
  DatosCierre,
  DatosPausa,
  FotoCierre,
  MomentoFoto,
  OrdenTrabajoDetalle,
} from '../types'

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

/** HU-05, solo la Jefatura. Todas o ninguna; responde las OTs actualizadas. */
export async function asignarOrdenes(datos: DatosAsignacion): Promise<OrdenTrabajoDetalle[]> {
  const res = await cliente.post<OrdenTrabajoDetalle[]>('/ordenes/asignar', datos)
  return res.data
}

/** HU-05 (3): cuántas OTs tiene cada cuadrilla para esa fecha. */
export async function obtenerCargaPorCuadrilla(fecha: string): Promise<CargaCuadrilla[]> {
  const res = await cliente.get<CargaCuadrilla[]>('/ordenes/carga-por-cuadrilla', {
    params: { fecha },
  })
  return res.data
}

/**
 * Cierre técnico (CU-3, HU-07 y HU-08) en un solo envío multipart: «datos» con el JSON del cierre
 * y las fotos ya comprimidas, cada una con su momento en «momentos» (en el mismo orden). Un
 * reintento repite el mismo idOperacion: el backend no cierra ni guarda las fotos de nuevo.
 */
export async function cerrarOrden(
  id: number,
  datos: DatosCierre,
  fotos: readonly { archivo: File; momento: MomentoFoto }[],
): Promise<OrdenTrabajoDetalle> {
  const formulario = new FormData()
  formulario.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }))
  fotos.forEach((foto) => {
    formulario.append('fotos', foto.archivo)
    formulario.append('momentos', foto.momento)
  })
  const res = await cliente.post<OrdenTrabajoDetalle>(`/ordenes/${id}/cerrar`, formulario)
  return res.data
}

/** Las fotos del cierre técnico (#90), en el orden en que se subieron. */
export async function listarFotosOrden(id: number): Promise<FotoCierre[]> {
  const res = await cliente.get<FotoCierre[]>(`/ordenes/${id}/fotos`)
  return res.data
}
