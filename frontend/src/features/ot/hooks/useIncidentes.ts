import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cerrarIncidenteSinOt, generarOtDeIncidente } from '../api/incidentesApi'
import { CLAVE_ORDENES } from './useOrden'

export const CLAVE_INCIDENTES = 'incidentes'

/** Al resolver una espera cambian los incidentes y, si se generó una OT, las órdenes. */
function useResolverEspera(accion: (id: number) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: accion,
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: [CLAVE_INCIDENTES] }),
        queryClient.invalidateQueries({ queryKey: [CLAVE_ORDENES] }),
      ]),
  })
}

export function useCerrarIncidenteSinOt() {
  return useResolverEspera(cerrarIncidenteSinOt)
}

export function useGenerarOtDeIncidente() {
  return useResolverEspera(generarOtDeIncidente)
}
