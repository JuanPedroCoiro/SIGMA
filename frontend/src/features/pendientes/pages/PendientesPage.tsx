import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Paginacion } from '../../../shared/components/Paginacion'
import { usePaginado } from '../../../shared/hooks/usePaginado'
import { formatearHoraMontevideo } from '../../../shared/lib/fechas'
import {
  ETIQUETA_ESTADO_OT,
  ETIQUETA_MOTIVO,
  ETIQUETA_PRIORIDAD,
  MOTIVO_QUE_REINICIA_SLA,
} from '../../ot/types'
import { SeccionIncidentesEnEspera } from '../components/SeccionIncidentesEnEspera'
import { useOrdenesPendientes } from '../hooks/usePendientes'
import {
  CRITERIOS_ORDEN,
  ETIQUETA_CRITERIO,
  type CriterioOrden,
  type OrdenPendiente,
} from '../types'
import './PendientesPage.css'

/** HU-04 (CU-9): OTs abiertas por vencimiento y, aparte, los incidentes en espera de tercero. */
export function PendientesPage() {
  const { pagina, irAPagina } = usePaginado()
  const [orden, setOrden] = useState<CriterioOrden>('VENCIMIENTO')
  const ordenes = useOrdenesPendientes(orden, pagina)

  function cambiarOrden(nuevo: CriterioOrden) {
    setOrden(nuevo)
    irAPagina(0)
  }

  return (
    <section className="pendientes">
      <div className="encabezado-pagina">
        <h1>Pendientes</h1>
        <label className="pendientes-orden">
          Ordenar por{' '}
          <select value={orden} onChange={(e) => cambiarOrden(e.target.value as CriterioOrden)}>
            {CRITERIOS_ORDEN.map((criterio) => (
              <option key={criterio} value={criterio}>
                {ETIQUETA_CRITERIO[criterio]}
              </option>
            ))}
          </select>
        </label>
      </div>

      <h2>Órdenes de trabajo</h2>
      {ordenes.isPending && <p>Cargando órdenes...</p>}
      {ordenes.isError && (
        <p className="alerta alerta-error" role="alert">
          No se pudo cargar el tablero. Volvé a intentar en unos minutos.
        </p>
      )}
      {ordenes.data?.totalElementos === 0 && <p>No hay órdenes de trabajo pendientes.</p>}

      {ordenes.data && ordenes.data.totalElementos > 0 && (
        <>
          <table className="tabla-pendientes">
            <thead>
              <tr>
                <th scope="col">OT</th>
                <th scope="col">Vencimiento</th>
                <th scope="col">Estado</th>
                <th scope="col">Prioridad</th>
                <th scope="col">Instalación</th>
                <th scope="col">Cuadrilla</th>
              </tr>
            </thead>
            <tbody>
              {ordenes.data.contenido.map((ot) => (
                <tr key={ot.id} className={ot.vencida ? 'fila-vencida' : undefined}>
                  <td data-etiqueta="OT">
                    <Link to={`/ordenes/${ot.id}`}>{ot.numero}</Link>
                    {ot.numeroIncidente && (
                      <span className="pendientes-secundario">{ot.numeroIncidente}</span>
                    )}
                  </td>
                  <td data-etiqueta="Vencimiento">
                    <Vencimiento orden={ot} />
                  </td>
                  <td data-etiqueta="Estado">
                    {ETIQUETA_ESTADO_OT[ot.estado]}
                    {ot.pausa && (
                      <span className="pendientes-secundario">
                        {ETIQUETA_MOTIVO[ot.pausa.motivo]}
                        {ot.pausa.detalle ? `: ${ot.pausa.detalle}` : ''}
                      </span>
                    )}
                  </td>
                  <td data-etiqueta="Prioridad">
                    {ot.prioridad ? ETIQUETA_PRIORIDAD[ot.prioridad] : '—'}
                  </td>
                  <td data-etiqueta="Instalación">
                    {ot.codigoInstalacion} · {ot.nombreInstalacion}
                  </td>
                  <td data-etiqueta="Cuadrilla">{ot.cuadrilla ?? 'Sin asignar'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Paginacion
            pagina={ordenes.data.pagina}
            totalPaginas={ordenes.data.totalPaginas}
            totalElementos={ordenes.data.totalElementos}
            onCambiar={irAPagina}
          />
        </>
      )}

      <SeccionIncidentesEnEspera />
    </section>
  )
}

/** Fecha límite con sus marcas: vencida, detenida por un tercero o sin SLA. */
function Vencimiento({ orden }: { orden: OrdenPendiente }) {
  if (orden.tipo === 'PREVENTIVA') {
    return <span className="pendientes-secundario">Preventiva, sin SLA</span>
  }
  if (!orden.fechaLimite) {
    return <span className="marca marca-aviso">SLA sin definir</span>
  }
  if (orden.pausa?.motivo === MOTIVO_QUE_REINICIA_SLA) {
    return <span className="marca marca-neutra">Detenido por un tercero</span>
  }
  return (
    <>
      {formatearHoraMontevideo(orden.fechaLimite)}
      {orden.vencida && <span className="marca marca-peligro">Vencida</span>}
    </>
  )
}
