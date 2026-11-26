import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { Paginacion } from '../../../shared/components/Paginacion'
import { usePaginado } from '../../../shared/hooks/usePaginado'
import { formatearFechaHoraMontevideo } from '../../../shared/lib/fechas'
import { FotosDelIncidente } from '../../ot/components/FotosIncidente'
import { FotosDeLaIntervencion } from '../../ot/components/FotosIntervencion'
import { textoCantidadFotos } from '../../ot/fotos'
import { ETIQUETA_PRIORIDAD } from '../../ot/types'
import { useHistorialInstalacion } from '../hooks/useInstalaciones'
import type { EntradaHistorial } from '../types'
import './HistorialInstalacion.css'

type Marca = { texto: string; clase: string; fecha: string }

/** Cómo se distingue cada entrada (CU-8) y cómo se lee su fecha. */
function marcaDe(entrada: EntradaHistorial): Marca {
  switch (entrada.estadoOt ?? entrada.estadoIncidente) {
    case 'VALIDADA':
      return { texto: 'Validada', clase: 'historial-marca-exito', fecha: 'Cerrada el' }
    case 'CERRADA_TECNICA':
      return {
        texto: 'Cerrada técnicamente, falta validar',
        clase: 'historial-marca-aviso',
        fecha: 'Cerrada el',
      }
    case 'ANULADA':
      return { texto: 'Anulada', clase: 'historial-marca-neutra', fecha: 'Anulada el' }
    case 'EN_ESPERA_TERCERO':
      return {
        texto: 'En espera de tercero',
        clase: 'historial-marca-aviso',
        fecha: 'Reportado el',
      }
    default:
      return { texto: 'Cerrado sin OT', clase: 'historial-marca-neutra', fecha: 'Reportado el' }
  }
}

/**
 * Historial de la instalación (HU-10, CU-8): OTs terminadas e incidentes sin OT, de lo más nuevo
 * a lo más viejo y paginado. Tiene su propia carga y su propio error: si falla, la ficha sigue a
 * la vista.
 */
export function HistorialInstalacion({ idInstalacion }: { idInstalacion: number }) {
  const { pagina, irAPagina } = usePaginado()
  const historial = useHistorialInstalacion(idInstalacion, pagina)

  return (
    <section aria-labelledby="titulo-historial" className="historial-instalacion">
      <h2 id="titulo-historial">Historial</h2>
      {historial.isPending && <p>Cargando el historial...</p>}
      {historial.isError && (
        <p className="alerta alerta-error" role="alert">
          No se pudo cargar el historial de la instalación. Volvé a intentar en unos minutos.
        </p>
      )}
      {historial.data?.totalElementos === 0 && (
        <p>Esta instalación no tiene intervenciones registradas.</p>
      )}
      {historial.data && historial.data.totalElementos > 0 && (
        <>
          <ol className="historial-entradas">
            {historial.data.contenido.map((entrada) => (
              <Entrada key={`${entrada.clase}-${entrada.id}`} entrada={entrada} />
            ))}
          </ol>
          <Paginacion
            pagina={historial.data.pagina}
            totalPaginas={historial.data.totalPaginas}
            totalElementos={historial.data.totalElementos}
            onCambiar={irAPagina}
          />
        </>
      )}
    </section>
  )
}

function Entrada({ entrada }: { entrada: EntradaHistorial }) {
  const marca = marcaDe(entrada)
  const incidente = entrada.incidente
  // En los incidentes sin OT, el número ya es el del incidente: no se repite.
  const incidenteDeLaOt = entrada.clase === 'OT' ? incidente : null
  return (
    <li className="historial-entrada">
      <div className="historial-encabezado">
        {entrada.puedeVerDetalle ? (
          <Link to={`/ordenes/${entrada.id}`} className="historial-numero">
            {entrada.numero}
          </Link>
        ) : (
          <span className="historial-numero">{entrada.numero}</span>
        )}
        <span className={`estado ${marca.clase}`}>{marca.texto}</span>
      </div>
      <p className="historial-fecha">
        {marca.fecha} {formatearFechaHoraMontevideo(entrada.fecha)}
      </p>
      <dl className="historial-datos">
        {entrada.tipoOt && (
          <Dato
            termino="Tipo"
            valor={
              (entrada.tipoOt === 'CORRECTIVA' ? 'Correctiva' : 'Preventiva') +
              (incidenteDeLaOt ? ` (${incidenteDeLaOt.numero})` : '')
            }
          />
        )}
        {incidente && <Dato termino="Falla" valor={incidente.categoria} />}
        {incidente && <Dato termino="Prioridad" valor={ETIQUETA_PRIORIDAD[incidente.prioridad]} />}
        {incidente && <Dato termino="Descripción" valor={incidente.descripcion} />}
        {entrada.clase === 'OT' && (
          <Dato termino="Cuadrilla" valor={entrada.cuadrilla?.nombre ?? 'Sin asignar'} />
        )}
        {entrada.iniciadaEn && (
          <Dato termino="Iniciada" valor={formatearFechaHoraMontevideo(entrada.iniciadaEn)} />
        )}
        {entrada.diagnostico && <Dato termino="Diagnóstico" valor={entrada.diagnostico} />}
        {entrada.solucion && <Dato termino="Solución" valor={entrada.solucion} />}
        {entrada.observaciones && <Dato termino="Observaciones" valor={entrada.observaciones} />}
        {entrada.motivoAnulacion && (
          <Dato termino="Motivo de anulación" valor={entrada.motivoAnulacion} />
        )}
        {entrada.tercero && <Dato termino="Tercero" valor={entrada.tercero} />}
        {entrada.causa && <Dato termino="Causa" valor={entrada.causa} />}
      </dl>
      <FotosDeLaEntrada entrada={entrada} />
    </li>
  )
}

/**
 * Cuántas fotos tiene la entrada (del incidente y del cierre) y un botón para verlas. Se piden
 * recién al abrirlas: la página del historial no hace un pedido por entrada. Quien ve la entrada
 * puede verlas (el backend lo controla); en el historial no se agregan.
 */
function FotosDeLaEntrada({ entrada }: { entrada: EntradaHistorial }) {
  const [abiertas, setAbiertas] = useState(false)
  const idFotos = useId()
  const { fotosIncidente, fotosCierre, incidente } = entrada
  if (fotosIncidente === 0 && fotosCierre === 0) return null

  const partes = [
    fotosIncidente > 0 && `${textoCantidadFotos(fotosIncidente)} del incidente`,
    fotosCierre > 0 && `${textoCantidadFotos(fotosCierre)} del cierre`,
  ].filter(Boolean)
  return (
    <div className="historial-fotos">
      <p className="historial-fotos-cantidad">Fotos: {partes.join(' · ')}</p>
      <button
        type="button"
        className="boton boton-secundario"
        aria-expanded={abiertas}
        aria-controls={idFotos}
        // El número distingue los botones de cada entrada para los lectores de pantalla.
        aria-label={`${abiertas ? 'Ocultar' : 'Ver'} fotos de ${entrada.numero}`}
        onClick={() => setAbiertas((antes) => !antes)}
      >
        {abiertas ? 'Ocultar fotos' : 'Ver fotos'}
      </button>
      {abiertas && (
        <div id={idFotos}>
          {incidente && fotosIncidente > 0 && (
            <FotosDelIncidente
              idIncidente={incidente.id}
              titulo="Fotos del incidente"
              permitirAgregar={false}
            />
          )}
          {entrada.clase === 'OT' && fotosCierre > 0 && (
            <FotosDeLaIntervencion idOrden={entrada.id} nivelTitulo={3} />
          )}
        </div>
      )}
    </div>
  )
}

function Dato({ termino, valor }: { termino: string; valor: string }) {
  return (
    <>
      <dt>{termino}</dt>
      <dd>{valor}</dd>
    </>
  )
}
