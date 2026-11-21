import type { ChangeEvent } from 'react'
import { useFormContext, useWatch } from 'react-hook-form'
import type { CamposIncidente } from '../../esquemas'
import type { FotoElegida } from '../../fotos'
import { esCategoriaOtra, ETIQUETA_PRIORIDAD, PRIORIDADES, type CategoriaFalla } from '../../types'
import { MensajeError } from './MensajeError'
import { propsError } from './pasos'
import { SelectorFotos } from './SelectorFotos'

const MAXIMO_DESCRIPCION = 2000

type Props = {
  catalogo: readonly CategoriaFalla[]
  cargandoCategorias: boolean
  errorCategorias: boolean
  fotos: readonly FotoElegida[]
  onFotos: (fotos: FotoElegida[]) => void
}

/** Paso 2: categoría, prioridad (sugerida por la categoría), descripción, fotos y tercero. */
export function PasoQuePaso({
  catalogo,
  cargandoCategorias,
  errorCategorias,
  fotos,
  onFotos,
}: Props) {
  const {
    control,
    register,
    setValue,
    formState: { errors },
  } = useFormContext<CamposIncidente>()
  const idCategoria = useWatch({ control, name: 'idCategoriaFalla' })
  const prioridad = useWatch({ control, name: 'prioridad' })
  const esTercero = useWatch({ control, name: 'esTercero' })
  const descripcion = useWatch({ control, name: 'descripcion' })
  const categoria = catalogo.find((c) => String(c.id) === idCategoria)

  return (
    <>
      <div className="campo">
        <label htmlFor="idCategoriaFalla">Categoría de la falla</label>
        <select
          id="idCategoriaFalla"
          {...propsError(errors, 'idCategoriaFalla')}
          {...register('idCategoriaFalla', {
            onChange: (e: ChangeEvent<HTMLSelectElement>) => {
              const elegida = catalogo.find((c) => String(c.id) === e.target.value)
              // RN-01: la categoría propone la prioridad; el actor la confirma o la cambia.
              if (elegida) setValue('prioridad', elegida.prioridadSugerida)
            },
          })}
        >
          <option value="">{cargandoCategorias ? 'Cargando...' : 'Elegí la categoría'}</option>
          {catalogo.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nombre}
            </option>
          ))}
        </select>
        {errorCategorias && (
          <span className="campo-error">
            No se pudieron cargar las categorías. Recargá la página.
          </span>
        )}
        <MensajeError campo="idCategoriaFalla" />
      </div>

      <div className="campo">
        <label htmlFor="prioridad">
          Prioridad <span className="campo-nota">(sugerida según la categoría)</span>
        </label>
        <select id="prioridad" {...propsError(errors, 'prioridad')} {...register('prioridad')}>
          <option value="">Elegí la prioridad</option>
          {PRIORIDADES.map((p) => (
            <option key={p} value={p}>
              {ETIQUETA_PRIORIDAD[p]}
            </option>
          ))}
        </select>
        {categoria ? (
          prioridad === categoria.prioridadSugerida ? (
            <span className="campo-ayuda">
              <span className="etiqueta-sugerida">Sugerida</span> por la categoría «
              {categoria.nombre}». Podés cambiarla.
            </span>
          ) : (
            <span className="campo-ayuda">
              <span className="etiqueta-sugerida">Cambiada</span> La categoría «{categoria.nombre}»
              sugiere {ETIQUETA_PRIORIDAD[categoria.prioridadSugerida]}.
            </span>
          )
        ) : (
          <span className="campo-ayuda">Se sugiere al elegir la categoría.</span>
        )}
        <MensajeError campo="prioridad" />
      </div>

      <div className="campo">
        <label htmlFor="descripcion">Descripción</label>
        <textarea
          id="descripcion"
          rows={4}
          maxLength={MAXIMO_DESCRIPCION}
          {...propsError(errors, 'descripcion')}
          {...register('descripcion')}
        />
        {esCategoriaOtra(categoria) && (
          <span className="campo-ayuda">
            Con la categoría «Otra», detallá la falla en la descripción.
          </span>
        )}
        <span className="campo-ayuda">
          {descripcion.length}/{MAXIMO_DESCRIPCION}
        </span>
        <MensajeError campo="descripcion" />
      </div>

      {/* No son campos del formulario: se suben después de crear el incidente (#71). */}
      <SelectorFotos fotos={fotos} onCambio={onFotos} />

      <label className="registrar-incidente-casilla">
        <input type="checkbox" {...register('esTercero')} />
        La falla es de un tercero
      </label>

      {esTercero && (
        <>
          <p className="campo-ayuda">
            El incidente queda en espera, sin OT ni SLA, hasta que se resuelva desde Pendientes.
          </p>
          <div className="campo">
            <label htmlFor="tercero">Tercero</label>
            <input
              id="tercero"
              autoComplete="off"
              placeholder="Por ejemplo, UTE"
              {...propsError(errors, 'tercero')}
              {...register('tercero')}
            />
            <MensajeError campo="tercero" />
          </div>
          <div className="campo">
            <label htmlFor="causa">Causa (opcional)</label>
            <input
              id="causa"
              autoComplete="off"
              {...propsError(errors, 'causa')}
              {...register('causa')}
            />
            <MensajeError campo="causa" />
          </div>
        </>
      )}
    </>
  )
}
