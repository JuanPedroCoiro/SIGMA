import { Link } from 'react-router-dom'
import { formatearFechaMontevideo, formatearHoraMontevideo } from '../../../shared/lib/fechas'
import { BotonIniciar } from '../../ot/components/BotonIniciar'
import { ETIQUETA_ESTADO_OT, ETIQUETA_MOTIVO, ETIQUETA_PRIORIDAD } from '../../ot/types'
import { enlaceComoLlegar } from '../enlaceMapa'
import type { OrdenDeMiCuadrilla } from '../types'

type Props = {
  orden: OrdenDeMiCuadrilla
}

/**
 * Una OT de la bandeja «Mis OTs» (HU-07), legible a 360 px: qué hay que hacer, dónde y para
 * cuándo. El número lleva a la intervención; si está Asignada, se puede iniciar desde acá.
 */
export function TarjetaMiOt({ orden }: Props) {
  const { instalacion } = orden
  const comoLlegar = enlaceComoLlegar(instalacion.latitud, instalacion.longitud)
  const titulo = `titulo-mi-ot-${orden.id}`

  return (
    <li
      className={orden.vencida ? 'tarjeta-mi-ot tarjeta-mi-ot-vencida' : 'tarjeta-mi-ot'}
      aria-labelledby={titulo}
    >
      <div className="tarjeta-mi-ot-encabezado">
        <h2 id={titulo}>
          <Link to={`/mis-ots/${orden.id}`}>{orden.numero}</Link>
        </h2>
        <span className="estado estado-ot">{ETIQUETA_ESTADO_OT[orden.estado]}</span>
        {orden.prioridad && (
          <span className={`prioridad prioridad-${orden.prioridad.toLowerCase()}`}>
            Prioridad {ETIQUETA_PRIORIDAD[orden.prioridad].toLowerCase()}
          </span>
        )}
      </div>

      {orden.categoria && <p className="tarjeta-mi-ot-categoria">{orden.categoria}</p>}

      <p className="tarjeta-mi-ot-instalacion">
        <strong>
          {instalacion.codigo} — {instalacion.nombre}
        </strong>
        {instalacion.direccion && <span>{instalacion.direccion}</span>}
        {instalacion.rutaZona && <span>{instalacion.rutaZona}</span>}
      </p>

      <dl className="tarjeta-mi-ot-datos">
        <dt>Fecha de ejecución</dt>
        <dd>{orden.fechaEjecucion ? formatearFechaMontevideo(orden.fechaEjecucion) : '—'}</dd>
        <dt>Vencimiento</dt>
        <dd>
          <Vencimiento orden={orden} />
        </dd>
      </dl>

      {orden.pausa && (
        <p className="alerta alerta-aviso">
          <strong>En pausa: {ETIQUETA_MOTIVO[orden.pausa.motivo]}</strong>
          {orden.pausa.detalle && <> ({orden.pausa.detalle})</>} desde las{' '}
          {formatearHoraMontevideo(orden.pausa.inicio)}.
        </p>
      )}

      <div className="tarjeta-mi-ot-acciones">
        {orden.estado === 'ASIGNADA' && <BotonIniciar orden={orden} />}
        {comoLlegar && (
          <a
            className="boton boton-secundario boton-grande"
            href={comoLlegar}
            target="_blank"
            rel="noreferrer"
            aria-label={`Cómo llegar a ${instalacion.codigo}`}
          >
            Cómo llegar
          </a>
        )}
      </div>
    </li>
  )
}

function Vencimiento({ orden }: Props) {
  if (!orden.fechaLimite) return <>{orden.tipo === 'CORRECTIVA' ? 'SLA sin definir' : '—'}</>
  return (
    <>
      {formatearHoraMontevideo(orden.fechaLimite)}
      {orden.vencida && <strong className="mi-ot-vencida"> Vencida</strong>}
    </>
  )
}
