import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Modal } from '../../../shared/components/Modal'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { formatearFechaMontevideo, hoyEnMontevideo } from '../../../shared/lib/fechas'
import { useCuadrillas } from '../../administracion/hooks/useCuadrillas'
import { esquemaAsignacion, type CamposAsignacion, type ValoresAsignacion } from '../esquemas'
import { useAsignarOrdenes, useCargaPorCuadrilla } from '../hooks/useOrden'
import type { EstadoOT } from '../types'

type Orden = {
  id: number
  numero: string
  estado: EstadoOT
  /** Nombre de la cuadrilla actual; null si no está asignada. */
  cuadrilla: string | null
  /** aaaa-mm-dd; null si no está asignada. */
  fechaEjecucion: string | null
}

type Props = {
  ordenes: Orden[]
  onCerrar: () => void
  /** Después de asignar: por ejemplo, para limpiar la selección del tablero. */
  onAsignadas: () => void
}

/**
 * Asignar o reasignar una o más OTs a una cuadrilla con fecha de ejecución (HU-05, CU-2). Todas o
 * ninguna. Muestra cuántas OTs ya tiene cada cuadrilla ese día (3). Si todas las OTs ya están
 * asignadas se titula «Reasignar» (CU-2 5a); si alguna lo está, muestra su asignación actual (#87).
 */
export function DialogoAsignacion({ ordenes, onCerrar, onAsignadas }: Props) {
  const asignar = useAsignarOrdenes()
  const cuadrillas = useCuadrillas()
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CamposAsignacion, unknown, ValoresAsignacion>({
    resolver: zodResolver(esquemaAsignacion),
    defaultValues: { fechaEjecucion: hoyEnMontevideo(), idCuadrilla: '' },
  })
  const fecha = useWatch({ control, name: 'fechaEjecucion' })
  const carga = useCargaPorCuadrilla(fecha)
  const ordenesDeCuadrilla = new Map((carga.data ?? []).map((c) => [c.idCuadrilla, c.ordenes]))
  const activas = (cuadrillas.data ?? []).filter((c) => c.activa)

  async function enviar(valores: ValoresAsignacion) {
    setErrorGeneral(null)
    try {
      await asignar.mutateAsync({ idsOrdenTrabajo: ordenes.map((o) => o.id), ...valores })
      onAsignadas()
    } catch (error) {
      setErrorGeneral(aplicarErroresDelServidor(error, setError, ['fechaEjecucion', 'idCuadrilla']))
    }
  }

  const asignadas = ordenes.filter((o) => o.estado === 'ASIGNADA')
  const reasigna = asignadas.length === ordenes.length
  const verbo = reasigna ? 'Reasignar' : 'Asignar'
  const titulo =
    ordenes.length === 1 ? `${verbo} ${ordenes[0].numero}` : `${verbo} ${ordenes.length} OTs`

  return (
    <Modal titulo={titulo} onCerrar={onCerrar}>
      <form noValidate onSubmit={handleSubmit(enviar)}>
        {ordenes.length > 1 && <p>{ordenes.map((o) => o.numero).join(', ')}</p>}
        {asignadas.length > 0 && (
          <section aria-labelledby="asignacion-actual">
            <h3 id="asignacion-actual" className="campo-ayuda">
              Asignación actual
            </h3>
            <ul>
              {asignadas.map((o) => (
                <li key={o.id}>{textoAsignacionActual(o)}</li>
              ))}
            </ul>
          </section>
        )}
        {errorGeneral && (
          <p className="alerta alerta-error" role="alert">
            {errorGeneral}
          </p>
        )}

        <div className="campo">
          <label htmlFor="asignacion-fecha">Fecha de ejecución</label>
          <input
            id="asignacion-fecha"
            type="date"
            min={hoyEnMontevideo()}
            aria-invalid={errors.fechaEjecucion ? true : undefined}
            aria-describedby={errors.fechaEjecucion ? 'asignacion-fecha-error' : undefined}
            {...register('fechaEjecucion')}
          />
          {errors.fechaEjecucion && (
            <span id="asignacion-fecha-error" className="campo-error">
              {errors.fechaEjecucion.message}
            </span>
          )}
        </div>

        <div className="campo">
          <label htmlFor="asignacion-cuadrilla">Cuadrilla</label>
          <select
            id="asignacion-cuadrilla"
            aria-invalid={errors.idCuadrilla ? true : undefined}
            aria-describedby={
              errors.idCuadrilla ? 'asignacion-cuadrilla-error' : 'asignacion-cuadrilla-ayuda'
            }
            {...register('idCuadrilla')}
          >
            <option value="">Elegí una cuadrilla</option>
            {activas.map((c) => (
              <option key={c.id} value={c.id}>
                {carga.data
                  ? `${c.nombre} — ${textoCarga(ordenesDeCuadrilla.get(c.id) ?? 0)}`
                  : c.nombre}
              </option>
            ))}
          </select>
          <span id="asignacion-cuadrilla-ayuda" className="campo-ayuda">
            Junto a cada cuadrilla, cuántas OTs ya tiene ese día.
          </span>
          {cuadrillas.isError && (
            <span className="campo-error">No se pudieron cargar las cuadrillas.</span>
          )}
          {errors.idCuadrilla && (
            <span id="asignacion-cuadrilla-error" className="campo-error">
              {errors.idCuadrilla.message}
            </span>
          )}
        </div>

        <div className="modal-acciones">
          <button type="button" className="boton boton-secundario" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="boton" disabled={isSubmitting}>
            {isSubmitting ? (reasigna ? 'Reasignando...' : 'Asignando...') : verbo}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function textoAsignacionActual(orden: Orden) {
  const fecha = orden.fechaEjecucion ? formatearFechaMontevideo(orden.fechaEjecucion) : 'sin fecha'
  return `${orden.numero}: ${orden.cuadrilla ?? 'sin cuadrilla'}, ${fecha}`
}

function textoCarga(ordenes: number) {
  return ordenes === 1 ? '1 OT' : `${ordenes} OTs`
}
