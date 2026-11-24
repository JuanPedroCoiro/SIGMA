import { Link, useParams } from 'react-router-dom'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import { formatearFechaMontevideo, formatearHoraMontevideo } from '../../../shared/lib/fechas'
import { AccionesPausa } from '../components/AccionesPausa'
import { BotonIniciar } from '../components/BotonIniciar'
import { FormularioCierre } from '../components/FormularioCierre'
import { textoCantidadFotos } from '../fotos'
import { useFotosOrden, useOrden } from '../hooks/useOrden'
import {
  ETIQUETA_ESTADO_OT,
  ETIQUETA_MOTIVO,
  ETIQUETA_PRIORIDAD,
  type OrdenTrabajoDetalle,
} from '../types'
import './IntervencionPage.css'

function mensajeDeError(error: unknown) {
  const status = obtenerProblemDetail(error)?.status
  if (status === 404) return 'No existe la orden de trabajo.'
  if (status === 403) return 'Esta OT no es de tu cuadrilla: puede que la hayan reasignado.'
  return 'No se pudo cargar la OT. Volvé a intentar en unos minutos.'
}

/**
 * Intervención del técnico en el celular (CU-3, HU-07): qué hay que hacer y dónde, y la acción
 * que corresponde al estado: iniciar, pausar o reanudar, y el cierre técnico con fotos. El
 * detalle completo (tiempos, historial de pausas) queda en /ordenes/:id.
 */
export function IntervencionPage() {
  const id = Number(useParams().id)
  const orden = useOrden(id)

  if (orden.isPending) return <p>Cargando...</p>
  if (orden.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {mensajeDeError(orden.error)} <Link to="/mis-ots">Volver a Mis OTs</Link>
      </p>
    )
  }

  const datos = orden.data
  return (
    <section className="intervencion">
      <p className="intervencion-volver">
        <Link to="/mis-ots">← Mis OTs</Link>
      </p>
      <h1>{datos.numero}</h1>

      <dl className="intervencion-datos">
        <dt>Estado</dt>
        <dd>{ETIQUETA_ESTADO_OT[datos.estado]}</dd>
        <dt>Instalación</dt>
        <dd>
          {datos.instalacion.codigo} — {datos.instalacion.nombre}
        </dd>
        {datos.incidente && (
          <>
            <dt>Falla</dt>
            <dd>
              {datos.incidente.categoria} (prioridad{' '}
              {ETIQUETA_PRIORIDAD[datos.incidente.prioridad].toLowerCase()})
            </dd>
            <dt>Descripción</dt>
            <dd>{datos.incidente.descripcion}</dd>
          </>
        )}
        <dt>Fecha de ejecución</dt>
        <dd>{datos.fechaEjecucion ? formatearFechaMontevideo(datos.fechaEjecucion) : '—'}</dd>
        <dt>Vencimiento</dt>
        <dd>
          <Vencimiento orden={datos} />
        </dd>
        {datos.iniciadaEn && (
          <>
            <dt>Iniciada</dt>
            <dd>{formatearHoraMontevideo(datos.iniciadaEn)}</dd>
          </>
        )}
      </dl>

      {datos.pausaAbierta && (
        <p className="alerta alerta-aviso" role="status">
          <strong>En pausa: {ETIQUETA_MOTIVO[datos.pausaAbierta.motivo]}</strong>
          {datos.pausaAbierta.detalle && <> ({datos.pausaAbierta.detalle})</>} desde las{' '}
          {formatearHoraMontevideo(datos.pausaAbierta.inicio)}.
        </p>
      )}

      <Acciones orden={datos} />

      <p>
        <Link to={`/ordenes/${datos.id}`}>Ver el detalle completo de la OT</Link>
      </p>
    </section>
  )
}

/** Lo que el técnico puede hacer según el estado de la OT (Fig. 18). */
function Acciones({ orden }: { orden: OrdenTrabajoDetalle }) {
  switch (orden.estado) {
    case 'ASIGNADA':
      return (
        <div className="intervencion-acciones">
          <BotonIniciar orden={orden} />
          <AccionesPausa orden={orden} />
        </div>
      )
    case 'EN_PROCESO':
      return (
        <>
          <div className="intervencion-acciones">
            <AccionesPausa orden={orden} />
          </div>
          <FormularioCierre key={orden.id} orden={orden} />
        </>
      )
    case 'EN_PAUSA':
      return (
        <div className="intervencion-acciones">
          <AccionesPausa orden={orden} />
        </div>
      )
    case 'CERRADA_TECNICA':
      return <CierreEnviado idOrden={orden.id} />
    default:
      return (
        <p className="alerta alerta-aviso" role="status">
          Esta OT está {ETIQUETA_ESTADO_OT[orden.estado].toLowerCase()}: ya no está en tu bandeja.
        </p>
      )
  }
}

function CierreEnviado({ idOrden }: { idOrden: number }) {
  const fotos = useFotosOrden(idOrden)
  return (
    <div className="alerta alerta-exito" role="status">
      <strong>Enviada, pendiente de validación.</strong>
      {fotos.isSuccess && fotos.data.length > 0 && (
        <> Se adjuntaron {textoCantidadFotos(fotos.data.length)}.</>
      )}{' '}
      <Link to="/mis-ots">Volver a Mis OTs</Link>
    </div>
  )
}

function Vencimiento({ orden }: { orden: OrdenTrabajoDetalle }) {
  if (!orden.fechaLimite) return <>{orden.tipo === 'CORRECTIVA' ? 'SLA sin definir' : '—'}</>
  return (
    <>
      {formatearHoraMontevideo(orden.fechaLimite)}
      {orden.vencida && <strong className="intervencion-vencida"> Vencida</strong>}
    </>
  )
}
