import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { CLAVE_INCIDENTES } from '../../ot/hooks/useIncidentes'
import { CLAVE_ORDENES } from '../../ot/hooks/useOrden'
import { listarIncidentesEnEspera, listarOrdenesPendientes } from '../api/pendientesApi'
import type { CriterioOrden } from '../types'

/**
 * Bajo la clave de órdenes a propósito: pausar y reanudar (y después asignar y anular) invalidan
 * [CLAVE_ORDENES], así que el tablero se recarga solo.
 */
export function useOrdenesPendientes(orden: CriterioOrden, pagina: number) {
  return useQuery({
    queryKey: [CLAVE_ORDENES, 'pendientes', orden, pagina],
    queryFn: () => listarOrdenesPendientes(orden, { page: pagina }),
    placeholderData: keepPreviousData,
  })
}

export function useIncidentesEnEspera() {
  return useQuery({
    queryKey: [CLAVE_INCIDENTES, 'en-espera'],
    queryFn: listarIncidentesEnEspera,
  })
}
