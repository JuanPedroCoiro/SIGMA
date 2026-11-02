import { Link, useParams } from 'react-router-dom'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import { formatearHoraMontevideo } from '../../../shared/lib/fechas'
import { AccionesPausa } from '../components/AccionesPausa'
import { formatearDuracion } from '../duracion'
import { useOrden } from '../hooks/useOrden'
import {
  ETIQUETA_ESTADO_OT,
  ETIQUETA_MOTIVO,
  MOTIVO_QUE_REINICIA_SLA,
  MOTIVOS_PAUSA,
  type OrdenTrabajoDetalle,
} from '../types'
import './OrdenDetallePage.css'

function mensajeDeError(error: unknown) {
  const status = obtenerProblemDetail(error)?.status
  if (status === 404) return 'No existe la orden de trabajo.'
  if (status === 403) return 'No tenés permiso para ver esta OT: no está asignada a tu cuadrilla.'
  return 'No se pudo cargar la OT. Volvé a intentar en unos minutos.'
}

/** Detalle de una OT: estado, vencimiento, pausa en curso, tiempos e historial de pausas (#39). */
export function OrdenDetallePage() {
  const id = Number(useParams().id)
  const orden = useOrden(id)

  if (orden.isPending) return <p>Cargando...</p>
  if (orden.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {mensajeDeError(orden.error)} <Link to="/">Volver al inicio</Link>
      </p>
    )
  }

  const datos = orden.data
  return (
    <section className="orden-detalle">
      <h1>Orden {datos.numero}</h1>

      <dl className="orden-datos">
        <dt>Estado</dt>
        <dd>{ETIQUETA_ESTADO_OT[datos.estado]}</dd>
        <dt>Tipo</dt>
        <dd>{datos.tipo === 'CORRECTIVA' ? 'Correctiva' : 'Preventiva'}</dd>
        <dt>Fecha límite</dt>
        <dd>
          <FechaLimite orden={datos} />
        </dd>
      </dl>

      {datos.pausaAbierta && (
        <div className="alerta alerta-aviso" role="status">
          <strong>En pausa: {ETIQUETA_MOTIVO[datos.pausaAbierta.motivo]}</strong>
          {datos.pausaAbierta.detalle && <> ({datos.pausaAbierta.detalle})</>} desde las{' '}
          {formatearHoraMontevideo(datos.pausaAbierta.inicio)}.
          {datos.pausaAbierta.motivo === MOTIVO_QUE_REINICIA_SLA && datos.slaDefinido && (
            <> El SLA no corre: vuelve a empezar completo al reanudar.</>
          )}
        </div>
      )}

      <AccionesPausa orden={datos} />

      {datos.tiempos && (
        <section aria-labelledby="titulo-tiempos">
          <h2 id="titulo-tiempos">Tiempos</h2>
          <dl className="orden-datos">
            <dt>Total</dt>
            <dd>{formatearDuracion(datos.tiempos.totalMinutos)}</dd>
            {MOTIVOS_PAUSA.filter((m) => datos.tiempos?.enPausaMinutos[m] !== undefined).map(
              (m) => (
                <Fila
                  key={m}
                  termino={`En pausa: ${ETIQUETA_MOTIVO[m]}`}
                  valor={formatearDuracion(datos.tiempos?.enPausaMinutos[m] ?? 0)}
                />
              ),
            )}
            <dt>Efectivo</dt>
            <dd>{formatearDuracion(datos.tiempos.efectivoMinutos)}</dd>
          </dl>
        </section>
      )}

      <section aria-labelledby="titulo-pausas">
        <h2 id="titulo-pausas">Historial de pausas</h2>
        {datos.pausas.length === 0 ? (
          <p>Sin pausas.</p>
        ) : (
          <table className="orden-pausas">
            <thead>
              <tr>
                <th scope="col">Motivo</th>
                <th scope="col">Detalle</th>
                <th scope="col">Desde</th>
                <th scope="col">Hasta</th>
              </tr>
            </thead>
            <tbody>
              {datos.pausas.map((pausa) => (
                <tr key={pausa.inicio}>
                  <td>{ETIQUETA_MOTIVO[pausa.motivo]}</td>
                  <td>{pausa.detalle ?? '—'}</td>
                  <td>{formatearHoraMontevideo(pausa.inicio)}</td>
                  <td>{pausa.fin ? formatearHoraMontevideo(pausa.fin) : 'En curso'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </section>
  )
}

function Fila({ termino, valor }: { termino: string; valor: string }) {
  return (
    <>
      <dt>{termino}</dt>
      <dd>{valor}</dd>
    </>
  )
}

function FechaLimite({ orden }: { orden: OrdenTrabajoDetalle }) {
  if (!orden.fechaLimite) {
    return <>{orden.tipo === 'CORRECTIVA' ? 'SLA sin definir' : '—'}</>
  }
  return (
    <>
      {formatearHoraMontevideo(orden.fechaLimite)}
      {orden.vencida && <span className="orden-vencida"> Vencida</span>}
    </>
  )
}
