import { Controller, useFormContext, useWatch } from 'react-hook-form'
import { BuscadorInstalacion } from '../../../activos/components/BuscadorInstalacion'
import { MapaInstalaciones } from '../../../activos/components/MapaInstalaciones'
import { useInstalacionesMapa } from '../../../activos/hooks/useInstalaciones'
import type { Instalacion } from '../../../activos/types'
import type { CamposIncidente } from '../../esquemas'
import { ETIQUETA_ORIGEN, ORIGENES_INCIDENTE } from '../../types'
import { MensajeError } from './MensajeError'
import { propsError } from './pasos'

type Props = {
  /** La instalación elegida: la guarda la página para el resumen y para volver a este paso. */
  instalacion: Instalacion | null
  onInstalacion: (instalacion: Instalacion | null) => void
}

/** Solo las que se pueden ubicar; las que no tienen coordenadas se eligen con el buscador. */
function conCoordenadas(instalaciones: Instalacion[]) {
  return instalaciones.filter((i) => Number.isFinite(i.latitud) && Number.isFinite(i.longitud))
}

/** Paso 1: la instalación y el origen del incidente, con el mapa al costado (#70). */
export function PasoDonde({ instalacion, onInstalacion }: Props) {
  const {
    control,
    register,
    setValue,
    formState: { errors },
  } = useFormContext<CamposIncidente>()
  const mapa = useInstalacionesMapa()
  const elegida = useWatch({ control, name: 'idInstalacion' }) === null ? null : instalacion

  /** Lo mismo que elegirla en el buscador. */
  function elegirEnElMapa(elegidaEnElMapa: Instalacion) {
    onInstalacion(elegidaEnElMapa)
    setValue('idInstalacion', elegidaEnElMapa.id, { shouldValidate: true })
  }

  return (
    <div className="registrar-incidente-donde">
      <div className="registrar-incidente-campos">
        <div className="campo">
          <label htmlFor="idInstalacion">Instalación</label>
          <Controller
            control={control}
            name="idInstalacion"
            render={({ field }) => (
              <BuscadorInstalacion
                id="idInstalacion"
                value={field.value === null ? null : instalacion}
                onChange={(elegida) => {
                  onInstalacion(elegida)
                  field.onChange(elegida?.id ?? null)
                }}
                onBlur={field.onBlur}
                inputRef={field.ref}
                invalido={Boolean(errors.idInstalacion)}
                idError={errors.idInstalacion ? 'idInstalacion-error' : undefined}
              />
            )}
          />
          <MensajeError campo="idInstalacion" />
        </div>

        <fieldset className="registrar-incidente-opciones" {...propsError(errors, 'origen')}>
          <legend>Origen</legend>
          {ORIGENES_INCIDENTE.map((origen) => (
            <label key={origen}>
              <input type="radio" value={origen} {...register('origen')} />
              {ETIQUETA_ORIGEN[origen]}
            </label>
          ))}
          <MensajeError campo="origen" />
        </fieldset>
      </div>
      <div className="registrar-incidente-mapa">
        <MapaInstalaciones
          instalaciones={conCoordenadas(mapa.data ?? [])}
          elegida={elegida}
          onElegir={elegirEnElMapa}
        />
        {mapa.isError && (
          <p className="campo-ayuda">
            No se pudo cargar el mapa. Podés buscar la instalación por código o nombre.
          </p>
        )}
      </div>
    </div>
  )
}
