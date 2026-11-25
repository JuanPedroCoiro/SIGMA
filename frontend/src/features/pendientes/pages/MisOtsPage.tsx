import { useState } from 'react'
import { Paginacion } from '../../../shared/components/Paginacion'
import { usePaginado } from '../../../shared/hooks/usePaginado'
import { CambiarCuadrillaDialogo } from '../components/CambiarCuadrillaDialogo'
import { TarjetaMiOt } from '../components/TarjetaMiOt'
import { useMiCuadrilla } from '../hooks/useMiCuadrilla'
import { useMisOts } from '../hooks/useMisOts'
import './MisOtsPage.css'

/**
 * Bandeja «Mis OTs» del técnico (CU-3, HU-07), pensada para el celular: las OTs Asignada, En
 * proceso y En pausa de su cuadrilla, por fecha de ejecución y vencimiento. Desde acá se cambia de
 * cuadrilla (HU-20).
 */
export function MisOtsPage() {
  const { pagina, irAPagina } = usePaginado()
  const misOts = useMisOts(pagina)
  const miCuadrilla = useMiCuadrilla()
  const [cambiando, setCambiando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)

  return (
    <section className="mis-ots">
      <h1>Mis OTs</h1>
      {miCuadrilla.data && (
        <div className="mis-ots-cuadrilla">
          <p>
            Cuadrilla: <strong>{miCuadrilla.data.actual?.nombre ?? 'sin cuadrilla'}</strong>
          </p>
          <button
            type="button"
            className="boton boton-secundario"
            onClick={() => {
              setMensaje(null)
              setCambiando(true)
            }}
          >
            Cambiar de cuadrilla
          </button>
        </div>
      )}
      {mensaje && (
        <p className="alerta alerta-exito" role="status">
          {mensaje}
        </p>
      )}
      {cambiando && miCuadrilla.data && (
        <CambiarCuadrillaDialogo
          miCuadrilla={miCuadrilla.data}
          onCerrar={() => setCambiando(false)}
          onCambiada={(nombre) => {
            setCambiando(false)
            irAPagina(0)
            setMensaje(`Ahora estás en ${nombre}.`)
          }}
        />
      )}
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
