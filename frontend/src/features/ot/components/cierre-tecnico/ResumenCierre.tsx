import type { ReactNode } from 'react'
import { useFormContext, useWatch } from 'react-hook-form'
import type { CamposCierre } from '../../esquemas'
import type { FotoParaCierre } from '../../fotosCierre'
import { ETIQUETA_MOMENTO, MOMENTOS_FOTO } from '../../types'
import { MiniaturasFotos } from '../MiniaturasFotos'
import type { NumeroPaso } from './pasos'

type Props = {
  fotos: readonly FotoParaCierre[]
  onEditar: (paso: Exclude<NumeroPaso, 3>) => void
}

const SIN_DATO = '—'

/** Paso 3 del cierre: lo cargado, con «Editar» por sección. Se confirma desde acá. */
export function ResumenCierre({ fotos, onEditar }: Props) {
  const { control } = useFormContext<CamposCierre>()
  const valores = useWatch({ control })
  const texto = (valor: string | undefined) => (valor ?? '').trim()

  return (
    <div className="resumen-cierre">
      <Seccion titulo="Trabajo" onEditar={() => onEditar(1)}>
        <dl>
          <div>
            <dt>Diagnóstico</dt>
            <dd>{texto(valores.diagnostico) || SIN_DATO}</dd>
          </div>
          <div>
            <dt>Solución</dt>
            <dd>{texto(valores.solucion) || SIN_DATO}</dd>
          </div>
          <div>
            <dt>Observaciones</dt>
            <dd>{texto(valores.observaciones) || 'Sin observaciones'}</dd>
          </div>
        </dl>
      </Seccion>
      <Seccion titulo="Fotos" onEditar={() => onEditar(2)}>
        {MOMENTOS_FOTO.map((momento) => {
          const delMomento = fotos.filter((foto) => foto.momento === momento)
          if (delMomento.length === 0) return null
          return (
            <div key={momento} className="resumen-cierre-momento">
              <h5>
                {ETIQUETA_MOMENTO[momento]} ({delMomento.length})
              </h5>
              <MiniaturasFotos
                fotos={delMomento}
                etiqueta={`Fotos: ${ETIQUETA_MOMENTO[momento]}`}
              />
            </div>
          )
        })}
      </Seccion>
      <p className="alerta alerta-aviso">
        Al confirmar, la OT queda cerrada técnicamente, pendiente de validación de la Jefatura.
      </p>
    </div>
  )
}

function Seccion({
  titulo,
  onEditar,
  children,
}: {
  titulo: string
  onEditar: () => void
  children: ReactNode
}) {
  return (
    <section className="resumen-cierre-seccion" aria-label={titulo}>
      <div className="resumen-cierre-encabezado">
        <h4>{titulo}</h4>
        <button
          type="button"
          className="boton boton-secundario"
          aria-label={`Editar ${titulo}`}
          onClick={onEditar}
        >
          Editar
        </button>
      </div>
      {children}
    </section>
  )
}
