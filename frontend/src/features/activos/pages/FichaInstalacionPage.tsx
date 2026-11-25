import { Link, useParams } from 'react-router-dom'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import { formatearFechaHoraMontevideo } from '../../../shared/lib/fechas'
import { ETIQUETA_ESTADO_OT, ETIQUETA_PRIORIDAD } from '../../ot/types'
import { MapaInstalaciones } from '../components/MapaInstalaciones'
import { formatearCoordenada } from '../coordenadas'
import { etiquetaInstalacion } from '../etiquetas'
import { useFichaInstalacion } from '../hooks/useInstalaciones'
import type { OrdenEnCursoFicha } from '../types'
import './FichaInstalacionPage.css'

const SIN_DATOS = 'Sin datos'

function mensajeDeError(error: unknown) {
  if (obtenerProblemDetail(error)?.status === 404) return 'No existe la instalación.'
  return 'No se pudo cargar la ficha de la instalación. Volvé a intentar en unos minutos.'
}

/**
 * Ficha de la instalación (HU-10, CU-7) para todos los roles: datos, ubicación en el mapa y OTs en
 * curso. Lo que no está cargado se muestra como «Sin datos» (2b).
 */
export function FichaInstalacionPage() {
  const id = Number(useParams().id)
  const ficha = useFichaInstalacion(id)

  if (ficha.isPending) return <p>Cargando...</p>
  if (ficha.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {mensajeDeError(ficha.error)} <Link to="/">Volver al inicio</Link>
      </p>
    )
  }

  const datos = ficha.data
  return (
    <section className="ficha-instalacion">
      <h1>
        {etiquetaInstalacion(datos)}
        {!datos.activa && <span className="estado estado-inactivo">Inactiva</span>}
      </h1>

      <dl className="ficha-datos">
        <dt>Código</dt>
        <dd>{datos.codigo}</dd>
        <dt>Nombre</dt>
        <dd>{datos.nombre}</dd>
        <dt>Tipo</dt>
        <dd>{datos.tipo ?? SIN_DATOS}</dd>
        <dt>Dirección</dt>
        <dd>{datos.direccion ?? SIN_DATOS}</dd>
        <dt>Ruta o zona</dt>
        <dd>{datos.rutaZona || SIN_DATOS}</dd>
        <dt>Coordenadas</dt>
        <dd>
          {formatearCoordenada(datos.latitud)}, {formatearCoordenada(datos.longitud)}
        </dd>
        <dt>Organismo</dt>
        <dd>{datos.organismo.nombre}</dd>
      </dl>

      <MapaInstalaciones instalaciones={[datos]} elegida={datos} />

      <section aria-labelledby="titulo-ots-en-curso">
        <h2 id="titulo-ots-en-curso">OTs en curso</h2>
        {datos.ordenesEnCurso.length === 0 ? (
          <p>No hay OTs en curso en esta instalación.</p>
        ) : (
          <ul className="ficha-ordenes">
            {datos.ordenesEnCurso.map((orden) => (
              <OrdenEnCurso key={orden.id} orden={orden} />
            ))}
          </ul>
        )}
      </section>
    </section>
  )
}

/** Tarjeta en lugar de fila de tabla: se lee en el celular sin desplazarse de costado. */
function OrdenEnCurso({ orden }: { orden: OrdenEnCursoFicha }) {
  return (
    <li className="ficha-orden">
      <div className="ficha-orden-encabezado">
        {orden.puedeVerDetalle ? (
          <Link to={`/ordenes/${orden.id}`} className="ficha-orden-numero">
            {orden.numero}
          </Link>
        ) : (
          <span className="ficha-orden-numero">{orden.numero}</span>
        )}
        <span className="estado ficha-orden-estado">{ETIQUETA_ESTADO_OT[orden.estado]}</span>
        {orden.vencida && <span className="estado ficha-orden-vencida">Vencida</span>}
      </div>
      <dl className="ficha-orden-datos">
        <dt>Tipo</dt>
        <dd>
          {orden.tipo === 'CORRECTIVA' ? 'Correctiva' : 'Preventiva'}
          {orden.numeroIncidente && <> ({orden.numeroIncidente})</>}
        </dd>
        {orden.prioridad && (
          <>
            <dt>Prioridad</dt>
            <dd>{ETIQUETA_PRIORIDAD[orden.prioridad]}</dd>
          </>
        )}
        <dt>Cuadrilla</dt>
        <dd>{orden.cuadrilla?.nombre ?? 'Sin asignar'}</dd>
        <dt>Fecha límite</dt>
        <dd>
          {orden.fechaLimite ? formatearFechaHoraMontevideo(orden.fechaLimite) : 'SLA sin definir'}
        </dd>
      </dl>
    </li>
  )
}
