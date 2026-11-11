import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cerrarIncidenteSinOt,
  generarOtDeIncidente,
  listarCategoriasFalla,
  registrarIncidente,
} from '../api/incidentesApi'
import { CLAVE_ORDENES } from './useOrden'

export const CLAVE_INCIDENTES = 'incidentes'

/** Al registrar o resolver una espera cambian los incidentes y, si se generó una OT, las órdenes. */
function useCambioDeIncidente<T, R>(accion: (datos: T) => Promise<R>) {
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
  return useCambioDeIncidente(cerrarIncidenteSinOt)
}

export function useGenerarOtDeIncidente() {
  return useCambioDeIncidente(generarOtDeIncidente)
}

/** Registrar un incidente (CU-1, HU-03): el tablero de pendientes se recarga solo. */
export function useRegistrarIncidente() {
  return useCambioDeIncidente(registrarIncidente)
}

/** El catálogo cambia muy poco: se pide una vez por sesión de la pantalla. */
export function useCategoriasFalla() {
  return useQuery({
    queryKey: ['categorias-falla'],
    queryFn: listarCategoriasFalla,
    staleTime: 10 * 60_000,
  })
}
