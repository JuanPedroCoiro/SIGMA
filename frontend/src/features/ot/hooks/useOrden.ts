import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { obtenerOrden, pausarOrden, reanudarOrden } from '../api/ordenesApi'
import type { DatosPausa, OrdenTrabajoDetalle } from '../types'

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
