import type { ReactNode } from 'react'
import { useFormContext, useWatch } from 'react-hook-form'
import { etiquetaInstalacion } from '../../../activos/etiquetas'
import type { Instalacion } from '../../../activos/types'
import type { CamposIncidente } from '../../esquemas'
import {
  ETIQUETA_ORIGEN,
  ETIQUETA_PRIORIDAD,
  type CategoriaFalla,
  type Prioridad,
} from '../../types'
import type { FotoElegida } from '../../fotos'
import { MiniaturasFotos } from '../MiniaturasFotos'
import type { PasoConCampos } from './pasos'

type Props = {
  instalacion: Instalacion | null
  catalogo: readonly CategoriaFalla[]
  fotos: readonly FotoElegida[]
  onEditar: (paso: PasoConCampos) => void
}

type Seccion = {
  paso: PasoConCampos
  titulo: string
  filas: { etiqueta: string; valor: string }[]
  contenido?: ReactNode
}

const SIN_DATO = '—'

function textoPrioridad(prioridad: Prioridad | '' | undefined, categoria?: CategoriaFalla) {
  if (!prioridad) return SIN_DATO
  const etiqueta = ETIQUETA_PRIORIDAD[prioridad]
  if (!categoria) return etiqueta
  return prioridad === categoria.prioridadSugerida
    ? `${etiqueta} (sugerida por la categoría)`
    : `${etiqueta} (cambiada; la categoría sugiere ${ETIQUETA_PRIORIDAD[categoria.prioridadSugerida]})`
}

/** Paso 3: todo lo cargado, con «Editar» por sección. Se confirma desde acá. */
export function ResumenIncidente({ instalacion, catalogo, fotos, onEditar }: Props) {
  const { control } = useFormContext<CamposIncidente>()
  const valores = useWatch({ control })
  const categoria = catalogo.find((c) => String(c.id) === valores.idCategoriaFalla)
  const tercero = valores.esTercero ? (valores.tercero ?? '').trim() : ''
  const causa = (valores.causa ?? '').trim()

  const secciones: Seccion[] = [
    {
      paso: 1,
      titulo: 'Dónde',
      filas: [
        {
          etiqueta: 'Instalación',
          valor: instalacion ? etiquetaInstalacion(instalacion) : SIN_DATO,
        },
        { etiqueta: 'Origen', valor: valores.origen ? ETIQUETA_ORIGEN[valores.origen] : SIN_DATO },
      ],
    },
    {
      paso: 2,
      titulo: 'Qué pasó',
      filas: [
        { etiqueta: 'Categoría', valor: categoria?.nombre ?? SIN_DATO },
        { etiqueta: 'Prioridad', valor: textoPrioridad(valores.prioridad, categoria) },
        { etiqueta: 'Descripción', valor: (valores.descripcion ?? '').trim() || SIN_DATO },
        { etiqueta: 'Falla de un tercero', valor: tercero ? `Sí: ${tercero}` : 'No' },
        ...(tercero && causa ? [{ etiqueta: 'Causa', valor: causa }] : []),
      ],
    },
    {
      paso: 2,
      titulo: 'Fotos',
      filas: fotos.length === 0 ? [{ etiqueta: 'Fotos', valor: 'Sin fotos' }] : [],
      contenido: <MiniaturasFotos fotos={fotos} />,
    },
  ]

  return (
    <div className="resumen-incidente">
      {secciones.map((seccion) => (
        <section key={seccion.titulo} className="resumen-incidente-seccion">
          <div className="resumen-incidente-encabezado">
            <h3>{seccion.titulo}</h3>
            <button
              type="button"
              className="boton boton-secundario"
              aria-label={`Editar ${seccion.titulo}`}
              onClick={() => onEditar(seccion.paso)}
            >
              Editar
            </button>
          </div>
          {seccion.filas.length > 0 && (
            <dl>
              {seccion.filas.map((fila) => (
                <div key={fila.etiqueta}>
                  <dt>{fila.etiqueta}</dt>
                  <dd>{fila.valor}</dd>
                </div>
              ))}
            </dl>
          )}
          {seccion.contenido}
        </section>
      ))}
      <p className="alerta alerta-aviso">
        {tercero
          ? 'Al confirmar, el incidente queda en espera de tercero, sin OT ni SLA, hasta que se resuelva desde Pendientes.'
          : 'Al confirmar, se genera la OT correctiva con su vencimiento.'}
      </p>
    </div>
  )
}
