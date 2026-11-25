import { Paginacion } from '../../../shared/components/Paginacion'
import { usePaginado } from '../../../shared/hooks/usePaginado'
import { TarjetaMiOt } from '../components/TarjetaMiOt'
import { useMisOts } from '../hooks/useMisOts'
import './MisOtsPage.css'

/**
 * Bandeja «Mis OTs» del técnico (CU-3, HU-07), pensada para el celular: las OTs Asignada, En
 * proceso y En pausa de su cuadrilla, por fecha de ejecución y vencimiento.
 */
export function MisOtsPage() {
  const { pagina, irAPagina } = usePaginado()
  const misOts = useMisOts(pagina)

  return (
    <section className="mis-ots">
      <h1>Mis OTs</h1>
      {misOts.isPending ? (
        <p>Cargando...</p>
      ) : misOts.isError ? (
        <p className="alerta alerta-error" role="alert">
          No se pudieron cargar tus OTs. Volvé a intentar en unos minutos.
        </p>
      ) : misOts.data.contenido.length === 0 ? (
        <p>No tenés OTs asignadas. Cuando la Jefatura le asigne una a tu cuadrilla, aparece acá.</p>
      ) : (
        <>
          <ul className="mis-ots-lista" aria-label="OTs de tu cuadrilla">
            {misOts.data.contenido.map((orden) => (
              <TarjetaMiOt key={orden.id} orden={orden} />
            ))}
          </ul>
          <Paginacion
            pagina={misOts.data.pagina}
            totalPaginas={misOts.data.totalPaginas}
            totalElementos={misOts.data.totalElementos}
            onCambiar={irAPagina}
          />
        </>
      )}
    </section>
  )
}
