import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CLAVE_SESION } from '../../autenticacion/api/sesionApi'
import { CLAVE_ORDENES } from '../../ot/hooks/useOrden'
import { cambiarMiCuadrilla, obtenerMiCuadrilla } from '../api/miCuadrillaApi'

export const CLAVE_MI_CUADRILLA = ['mi-cuadrilla'] as const

/** La cuadrilla del técnico y a cuáles se puede sumar (HU-20). */
export function useMiCuadrilla() {
  return useQuery({ queryKey: CLAVE_MI_CUADRILLA, queryFn: obtenerMiCuadrilla })
}

/**
 * Cambiar de cuadrilla (HU-20). Después recarga la sesión (su idCuadrilla) y la bandeja, que cuelga
 * de [CLAVE_ORDENES]; la respuesta ya trae la cuadrilla nueva.
 */
export function useCambiarCuadrilla() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: cambiarMiCuadrilla,
    onSuccess: (miCuadrilla) => {
      queryClient.setQueryData(CLAVE_MI_CUADRILLA, miCuadrilla)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: CLAVE_SESION }),
        queryClient.invalidateQueries({ queryKey: [CLAVE_ORDENES] }),
      ])
    },
  })
}
