import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  anularOrden,
  asignarOrdenes,
  obtenerCargaPorCuadrilla,
  obtenerOrden,
  pausarOrden,
  reanudarOrden,
} from '../api/ordenesApi'
import type { DatosAsignacion, DatosPausa, OrdenTrabajoDetalle } from '../types'

export const CLAVE_ORDENES = 'ordenes'

export function useOrden(id: number) {
  return useQuery({ queryKey: [CLAVE_ORDENES, id], queryFn: () => obtenerOrden(id) })
}

/**
 * La respuesta trae la OT actualizada: se guarda como su detalle y se recargan las demás consultas
 * de órdenes (por ejemplo, el tablero).
 */
function useActualizarOrden(id: number) {
  const queryClient = useQueryClient()
  return (orden: OrdenTrabajoDetalle) => {
    queryClient.setQueryData([CLAVE_ORDENES, id], orden)
    return queryClient.invalidateQueries({
      queryKey: [CLAVE_ORDENES],
      predicate: (consulta) => consulta.queryKey[1] !== id,
    })
  }
}

export function usePausarOrden(id: number) {
  const actualizar = useActualizarOrden(id)
  return useMutation({
    mutationFn: (datos: DatosPausa) => pausarOrden(id, datos),
    onSuccess: actualizar,
  })
}

export function useReanudarOrden(id: number) {
  const actualizar = useActualizarOrden(id)
  return useMutation({ mutationFn: () => reanudarOrden(id), onSuccess: actualizar })
}

export function useAnularOrden(id: number) {
  const actualizar = useActualizarOrden(id)
  return useMutation({
    mutationFn: (motivo: string) => anularOrden(id, motivo),
    onSuccess: actualizar,
  })
}

/**
 * HU-05: asignar o reasignar varias OTs. Invalida todas las consultas de órdenes: el tablero, los
 * detalles y la carga por cuadrilla.
 */
export function useAsignarOrdenes() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (datos: DatosAsignacion) => asignarOrdenes(datos),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [CLAVE_ORDENES] }),
  })
}

/** HU-05 (3): la carga de cada cuadrilla para la fecha elegida, como referencia al asignar. */
export function useCargaPorCuadrilla(fecha: string) {
  return useQuery({
    queryKey: [CLAVE_ORDENES, 'carga-por-cuadrilla', fecha],
    queryFn: () => obtenerCargaPorCuadrilla(fecha),
    enabled: fecha !== '',
  })
}
