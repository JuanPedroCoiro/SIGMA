import { useState } from 'react'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import { DialogoConfirmacion } from '../../../shared/components/DialogoConfirmacion'
import { formatearHoraMontevideo } from '../../../shared/lib/fechas'
import { useCerrarIncidenteSinOt, useGenerarOtDeIncidente } from '../../ot/hooks/useIncidentes'
import { ETIQUETA_PRIORIDAD } from '../../ot/types'
import { useIncidentesEnEspera } from '../hooks/usePendientes'
import type { IncidenteEnEspera } from '../types'

type Accion = { tipo: 'cerrar' | 'generar'; incidente: IncidenteEnEspera }

/**
 * Incidentes en espera de tercero (CU-1 4b): no tienen vencimiento. Se resuelven cerrándolos sin
 * OT (el tercero lo solucionó) o generando la OT (el SLA cuenta desde ese momento).
 */
export function SeccionIncidentesEnEspera() {
  const incidentes = useIncidentesEnEspera()
  const cerrar = useCerrarIncidenteSinOt()
  const generar = useGenerarOtDeIncidente()
  const [accion, setAccion] = useState<Accion | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function confirmar() {
    if (!accion) return
    setError(null)
    try {
      await (accion.tipo === 'cerrar' ? cerrar : generar).mutateAsync(accion.incidente.id)
    } catch (e) {
      setError(
        obtenerProblemDetail(e)?.detail ?? 'No se pudo resolver el incidente. Volvé a intentar.',
      )
    } finally {
      setAccion(null)
    }
  }

  return (
    <section className="pendientes-seccion">
      <h2>Incidentes en espera de tercero</h2>
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}
      {incidentes.isPending && <p>Cargando incidentes...</p>}
      {incidentes.isError && (
        <p className="alerta alerta-error" role="alert">
          No se pudieron cargar los incidentes en espera. Volvé a intentar en unos minutos.
        </p>
      )}
      {incidentes.data?.length === 0 && <p>No hay incidentes en espera de tercero.</p>}

      {incidentes.data && incidentes.data.length > 0 && (
        <table className="tabla-pendientes">
          <thead>
            <tr>
              <th scope="col">Incidente</th>
              <th scope="col">Tercero</th>
              <th scope="col">Prioridad</th>
              <th scope="col">Instalación</th>
              <th scope="col">Desde</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {incidentes.data.map((incidente) => (
              <tr key={incidente.id}>
                <td data-etiqueta="Incidente">
                  {incidente.numero}
                  <span className="pendientes-secundario">{incidente.descripcion}</span>
                </td>
                <td data-etiqueta="Tercero">
                  {incidente.tercero}
                  {incidente.causa && (
                    <span className="pendientes-secundario">{incidente.causa}</span>
                  )}
                </td>
                <td data-etiqueta="Prioridad">{ETIQUETA_PRIORIDAD[incidente.prioridad]}</td>
                <td data-etiqueta="Instalación">
                  {incidente.codigoInstalacion} · {incidente.nombreInstalacion}
                </td>
                <td data-etiqueta="Desde">{formatearHoraMontevideo(incidente.creadoEn)}</td>
                <td data-etiqueta="Acciones">
                  <div className="pendientes-acciones">
                    <button
                      type="button"
                      className="boton boton-primario"
                      onClick={() => setAccion({ tipo: 'generar', incidente })}
                    >
                      Generar OT
                    </button>
                    <button
                      type="button"
                      className="boton boton-secundario"
                      onClick={() => setAccion({ tipo: 'cerrar', incidente })}
                    >
                      Cerrar sin OT
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {accion && (
        <DialogoConfirmacion
          titulo={accion.tipo === 'cerrar' ? 'Cerrar sin OT' : 'Generar OT'}
          textoConfirmar={accion.tipo === 'cerrar' ? 'Cerrar sin OT' : 'Generar OT'}
          onConfirmar={confirmar}
          onCancelar={() => setAccion(null)}
          procesando={cerrar.isPending || generar.isPending}
        >
          {accion.tipo === 'cerrar' ? (
            <p>
              El incidente {accion.incidente.numero} se cierra sin orden de trabajo porque{' '}
              {accion.incidente.tercero} resolvió la falla.
            </p>
          ) : (
            <p>
              Se genera la orden de trabajo del incidente {accion.incidente.numero}. El SLA empieza
              a contar desde ahora.
            </p>
          )}
        </DialogoConfirmacion>
      )}
    </section>
  )
}
