import { useQuery } from '@tanstack/react-query'
import { obtenerResumenInicio } from '../api/inicioApi'

/** Iniciar y cerrar una OT (HU-07) la invalidan: cambian las OTs de hoy de la cuadrilla. */
export const CLAVE_RESUMEN_INICIO = ['inicio', 'resumen'] as const

/** Se vuelve a pedir cada vez que se entra al inicio, así los números están al día. */
export function useResumenInicio() {
  return useQuery({ queryKey: CLAVE_RESUMEN_INICIO, queryFn: obtenerResumenInicio })
}
