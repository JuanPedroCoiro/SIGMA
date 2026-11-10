import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState, type ChangeEvent } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { formatearFechaHoraMontevideo } from '../../../shared/lib/fechas'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { BuscadorInstalacion } from '../../activos/components/BuscadorInstalacion'
import { crearEsquemaIncidente, type CamposIncidente, type ValoresIncidente } from '../esquemas'
import { useCategoriasFalla, useRegistrarIncidente } from '../hooks/useIncidentes'
import {
  esCategoriaOtra,
  ETIQUETA_ORIGEN,
  ETIQUETA_PRIORIDAD,
  ORIGENES_INCIDENTE,
  PRIORIDADES,
  type IncidenteResponse,
} from '../types'
import './RegistrarIncidentePage.css'

const CAMPOS = [
  'idInstalacion',
  'origen',
  'idCategoriaFalla',
  'prioridad',
  'descripcion',
  'tercero',
  'causa',
] as const

type Campo = (typeof CAMPOS)[number]

const MAXIMO_DESCRIPCION = 2000

const VALORES_INICIALES: CamposIncidente = {
  idInstalacion: null,
  // Sin elegir: el esquema pide elegirlos y muestra el mensaje.
  origen: undefined as unknown as CamposIncidente['origen'],
  idCategoriaFalla: '',
  prioridad: '' as CamposIncidente['prioridad'],
  descripcion: '',
  esTercero: false,
  tercero: '',
  causa: '',
}

/**
 * Registrar un incidente (CU-1, HU-03): Operador y Jefatura. Genera la OT correctiva con su
 * vencimiento, o deja el incidente en espera si la falla es de un tercero (4b).
 */
export function RegistrarIncidentePage() {
  const [resultado, setResultado] = useState<IncidenteResponse | null>(null)

  return (
    <section className="registrar-incidente">
      <h1>Registrar incidente</h1>
      {resultado ? (
        <ResultadoRegistro resultado={resultado} onOtro={() => setResultado(null)} />
      ) : (
        <FormularioIncidente onRegistrado={setResultado} />
      )}
    </section>
  )
}

function ResultadoRegistro({
  resultado,
  onOtro,
}: {
  resultado: IncidenteResponse
  onOtro: () => void
}) {
  const ot = resultado.ordenTrabajo
  return (
    <div className="registrar-incidente-resultado">
      <p className="alerta alerta-exito" role="status">
        {ot ? (
          <>
            Se generó la {ot.numero} (incidente {resultado.numero}),{' '}
            {ot.slaDefinido && ot.fechaLimite
              ? `vence a las ${formatearFechaHoraMontevideo(ot.fechaLimite)}.`
              : 'SLA sin definir.'}
          </>
        ) : (
          <>
            El incidente {resultado.numero} quedó en espera de tercero; aparece en{' '}
            <Link to="/pendientes">Pendientes</Link>.
          </>
        )}
      </p>
      <div className="registrar-incidente-acciones">
        {ot && (
          <Link to={`/ordenes/${ot.id}`} className="boton boton-secundario">
            Ver {ot.numero}
          </Link>
        )}
        <button type="button" className="boton boton-primario" onClick={onOtro}>
          Registrar otro incidente
        </button>
      </div>
    </div>
  )
}

function FormularioIncidente({
  onRegistrado,
}: {
  onRegistrado: (resultado: IncidenteResponse) => void
}) {
  const registrar = useRegistrarIncidente()
  const categorias = useCategoriasFalla()
  const catalogo = useMemo(() => categorias.data ?? [], [categorias.data])
  const esquema = useMemo(() => crearEsquemaIncidente(catalogo), [catalogo])
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CamposIncidente, unknown, ValoresIncidente>({
    resolver: zodResolver(esquema),
    defaultValues: VALORES_INICIALES,
  })
  const idCategoria = useWatch({ control, name: 'idCategoriaFalla' })
  const esTercero = useWatch({ control, name: 'esTercero' })
  const descripcion = useWatch({ control, name: 'descripcion' })
  const categoria = catalogo.find((c) => String(c.id) === idCategoria)

  async function enviar(valores: ValoresIncidente) {
    setErrorGeneral(null)
    try {
      onRegistrado(await registrar.mutateAsync(valores))
    } catch (error) {
      setErrorGeneral(aplicarErroresDelServidor(error, setError, CAMPOS))
    }
  }

  function propsError(campo: Campo) {
    return {
      'aria-invalid': errors[campo] ? true : undefined,
      'aria-describedby': errors[campo] ? `${campo}-error` : undefined,
    }
  }

  function mensajeError(campo: Campo) {
    const mensaje = errors[campo]?.message
    return mensaje ? (
      <span id={`${campo}-error`} className="campo-error">
        {mensaje}
      </span>
    ) : null
  }

  return (
    <form className="registrar-incidente-form" noValidate onSubmit={handleSubmit(enviar)}>
      {errorGeneral && (
        <p className="alerta alerta-error" role="alert">
          {errorGeneral}
        </p>
      )}

      <div className="campo">
        <label htmlFor="idInstalacion">Instalación</label>
        <Controller
          control={control}
          name="idInstalacion"
          render={({ field }) => (
            <BuscadorInstalacion
              id="idInstalacion"
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              inputRef={field.ref}
              invalido={Boolean(errors.idInstalacion)}
              idError={errors.idInstalacion ? 'idInstalacion-error' : undefined}
            />
          )}
        />
        {mensajeError('idInstalacion')}
      </div>

      <fieldset className="registrar-incidente-opciones" {...propsError('origen')}>
        <legend>Origen</legend>
        {ORIGENES_INCIDENTE.map((origen) => (
          <label key={origen}>
            <input type="radio" value={origen} {...register('origen')} />
            {ETIQUETA_ORIGEN[origen]}
          </label>
        ))}
        {mensajeError('origen')}
      </fieldset>

      <div className="campo">
        <label htmlFor="idCategoriaFalla">Categoría de la falla</label>
        <select
          id="idCategoriaFalla"
          {...propsError('idCategoriaFalla')}
          {...register('idCategoriaFalla', {
            onChange: (e: ChangeEvent<HTMLSelectElement>) => {
              const elegida = catalogo.find((c) => String(c.id) === e.target.value)
              // RN-01: la categoría propone la prioridad; el actor la confirma o la cambia.
              if (elegida) setValue('prioridad', elegida.prioridadSugerida)
            },
          })}
        >
          <option value="">{categorias.isPending ? 'Cargando...' : 'Elegí la categoría'}</option>
          {catalogo.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nombre}
            </option>
          ))}
        </select>
        {categorias.isError && (
          <span className="campo-error">
            No se pudieron cargar las categorías. Recargá la página.
          </span>
        )}
        {mensajeError('idCategoriaFalla')}
      </div>

      <div className="campo">
        <label htmlFor="prioridad">Prioridad</label>
        <select id="prioridad" {...propsError('prioridad')} {...register('prioridad')}>
          <option value="">Elegí la prioridad</option>
          {PRIORIDADES.map((p) => (
            <option key={p} value={p}>
              {ETIQUETA_PRIORIDAD[p]}
            </option>
          ))}
        </select>
        {categoria && (
          <span className="campo-ayuda">
            Propuesta por la categoría: {ETIQUETA_PRIORIDAD[categoria.prioridadSugerida]}. Podés
            cambiarla.
          </span>
        )}
        {mensajeError('prioridad')}
      </div>

      <div className="campo">
        <label htmlFor="descripcion">Descripción</label>
        <textarea
          id="descripcion"
          rows={4}
          maxLength={MAXIMO_DESCRIPCION}
          {...propsError('descripcion')}
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
        {mensajeError('descripcion')}
      </div>

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
              {...propsError('tercero')}
              {...register('tercero')}
            />
            {mensajeError('tercero')}
          </div>
          <div className="campo">
            <label htmlFor="causa">Causa (opcional)</label>
            <input id="causa" autoComplete="off" {...propsError('causa')} {...register('causa')} />
            {mensajeError('causa')}
          </div>
        </>
      )}

      <div className="registrar-incidente-acciones">
        <button type="submit" className="boton boton-primario" disabled={isSubmitting}>
          {isSubmitting ? 'Registrando...' : 'Registrar incidente'}
        </button>
      </div>
    </form>
  )
}
