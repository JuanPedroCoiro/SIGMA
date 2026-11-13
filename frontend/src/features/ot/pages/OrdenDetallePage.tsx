import { Link, useParams } from 'react-router-dom'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import {
  formatearFechaHoraMontevideo,
  formatearFechaMontevideo,
  formatearHoraMontevideo,
} from '../../../shared/lib/fechas'
import { AccionesPausa } from '../components/AccionesPausa'
import { formatearDuracion } from '../duracion'
import { useOrden } from '../hooks/useOrden'
import {
  ETIQUETA_ESTADO_OT,
  ETIQUETA_MOTIVO,
  ETIQUETA_ORIGEN,
  ETIQUETA_PRIORIDAD,
  MOTIVO_QUE_REINICIA_SLA,
  MOTIVOS_PAUSA,
  type IncidenteDeOT,
  type OrdenTrabajoDetalle,
} from '../types'
import './OrdenDetallePage.css'

function mensajeDeError(error: unknown) {
  const status = obtenerProblemDetail(error)?.status
  if (status === 404) return 'No existe la orden de trabajo.'
  if (status === 403) return 'No tenés permiso para ver esta OT: no está asignada a tu cuadrilla.'
  return 'No se pudo cargar la OT. Volvé a intentar en unos minutos.'
}

/**
 * Detalle de una OT: estado, vencimiento, pausa en curso, tiempos e historial de pausas (#39);
 * instalación, cuadrilla, incidente y quién pausó y reanudó (#57).
 */
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
        <dt>Instalación</dt>
        <dd>
          {datos.instalacion.codigo} — {datos.instalacion.nombre}
        </dd>
        <dt>Cuadrilla</dt>
        <dd>{datos.cuadrilla?.nombre ?? 'Sin asignar'}</dd>
        <dt>Fecha de ejecución</dt>
        <dd>{datos.fechaEjecucion ? formatearFechaMontevideo(datos.fechaEjecucion) : '—'}</dd>
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

      {datos.incidente && <Incidente incidente={datos.incidente} />}

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
                <th scope="col">Pausó</th>
                <th scope="col">Reanudó</th>
              </tr>
            </thead>
            <tbody>
              {datos.pausas.map((pausa) => (
                <tr key={pausa.inicio}>
                  <td>{ETIQUETA_MOTIVO[pausa.motivo]}</td>
                  <td>{pausa.detalle ?? '—'}</td>
                  <td className="orden-pausas-fija">
                    {formatearFechaHoraMontevideo(pausa.inicio)}
                  </td>
                  <td className="orden-pausas-fija">
                    {pausa.fin ? formatearFechaHoraMontevideo(pausa.fin) : 'En curso'}
                  </td>
                  <td className="orden-pausas-fija">{pausa.nombreUsuarioPausa}</td>
                  <td className="orden-pausas-fija">{pausa.nombreUsuarioReanudacion ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </section>
  )
}

function Incidente({ incidente }: { incidente: IncidenteDeOT }) {
  return (
    <section aria-labelledby="titulo-incidente">
      <h2 id="titulo-incidente">Incidente</h2>
      <dl className="orden-datos">
        <Fila termino="Número" valor={incidente.numero} />
        <Fila termino="Origen" valor={ETIQUETA_ORIGEN[incidente.origen]} />
        <Fila termino="Categoría" valor={incidente.categoria} />
        <Fila termino="Prioridad" valor={ETIQUETA_PRIORIDAD[incidente.prioridad]} />
        <Fila termino="Descripción" valor={incidente.descripcion} />
        {incidente.tercero && <Fila termino="Tercero" valor={incidente.tercero} />}
        {incidente.causa && <Fila termino="Causa" valor={incidente.causa} />}
      </dl>
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
