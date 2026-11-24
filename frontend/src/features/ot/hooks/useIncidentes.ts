import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cerrarIncidenteSinOt,
  generarOtDeIncidente,
  listarCategoriasFalla,
  listarFotosIncidente,
  registrarIncidente,
  subirFotosIncidente,
} from '../api/incidentesApi'
import { CLAVE_ORDENES } from './useOrden'

export const CLAVE_INCIDENTES = 'incidentes'
const CLAVE_FOTOS = 'fotos-incidente'

/**
 * Las fotos de un incidente (#71). Las ven Operador, Jefatura y el técnico de la cuadrilla de la OT
 * (#85); el backend controla la cuadrilla. Sin sesión, no se piden.
 */

export function useFotosIncidente(idIncidente: number, habilitado: boolean) {
  return useQuery({
    queryKey: [CLAVE_FOTOS, idIncidente],
    queryFn: () => listarFotosIncidente(idIncidente),
    enabled: habilitado,
  })
}

export function useSubirFotosIncidente() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ idIncidente, archivos }: { idIncidente: number; archivos: readonly File[] }) =>
      subirFotosIncidente(idIncidente, archivos),
    onSuccess: (_, { idIncidente }) =>
      queryClient.invalidateQueries({ queryKey: [CLAVE_FOTOS, idIncidente] }),
  })
}

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
