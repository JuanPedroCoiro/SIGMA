import { useQuery } from '@tanstack/react-query'
import { obtenerResumenInicio } from '../api/inicioApi'

/** Se vuelve a pedir cada vez que se entra al inicio, así los números están al día. */
export function useResumenInicio() {
  return useQuery({ queryKey: ['inicio', 'resumen'], queryFn: obtenerResumenInicio })
}
