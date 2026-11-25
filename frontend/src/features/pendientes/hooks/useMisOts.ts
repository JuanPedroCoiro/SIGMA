import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { CLAVE_ORDENES } from '../../ot/hooks/useOrden'
import { listarMisOts } from '../api/misOtsApi'

/**
 * Bandeja «Mis OTs» (HU-07). Bajo la clave de órdenes a propósito, como el tablero: iniciar,
 * pausar, reanudar y cerrar invalidan [CLAVE_ORDENES], así que la bandeja se recarga sola.
 */
export function useMisOts(pagina: number) {
  return useQuery({
    queryKey: [CLAVE_ORDENES, 'mis-ots', pagina],
    queryFn: () => listarMisOts({ page: pagina }),
    placeholderData: keepPreviousData,
  })
}
