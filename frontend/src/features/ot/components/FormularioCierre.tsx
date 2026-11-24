import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { useForm, type UseFormRegisterReturn } from 'react-hook-form'
import { Link } from 'react-router-dom'
import {
  obtenerProblemDetail,
  tipoProblema,
  TIPOS_PROBLEMA,
} from '../../../shared/api/problemDetail'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { esquemaCierre, type CamposCierre, type ValoresCierre } from '../esquemas'
import type { FotoParaCierre } from '../fotosCierre'
import { useCerrarOrden } from '../hooks/useOrden'
import { generarIdOperacion } from '../idOperacion'
import { mensajeErrorEjecucion, type ErrorEjecucion } from '../mensajesEjecucion'
import type { OrdenTrabajoDetalle } from '../types'
import { FotosCierre } from './FotosCierre'
import './FormularioCierre.css'

type Props = {
  orden: Pick<OrdenTrabajoDetalle, 'id' | 'numero'>
}

const CAMPOS = ['diagnostico', 'solucion', 'observaciones'] as const

/** Errores después de los cuales no tiene sentido reintentar: la OT ya no es para cerrar. */
const SIN_REINTENTO: readonly ErrorEjecucion['tipo'][] = ['reasignada', 'ya-cerrada', 'anulada']

function liberar(fotos: readonly FotoParaCierre[]) {
  fotos.forEach((foto) => URL.revokeObjectURL(foto.vistaPrevia))
}

/**
 * Cierre técnico (CU-3 pasos 6 a 10, HU-07 y HU-08): diagnóstico, solución, observaciones y al
 * menos una foto. El idOperacion se genera una vez por cierre y queda con las fotos en el estado
 * del formulario: si el envío falla (7b), el formulario se conserva y el reintento manda
 * exactamente lo mismo, así el backend no cierra dos veces ni duplica las fotos (R8).
 */
export function FormularioCierre({ orden }: Props) {
  const cerrar = useCerrarOrden(orden.id)
  const [fotos, setFotos] = useState<FotoParaCierre[]>([])
  const [idOperacion, setIdOperacion] = useState(generarIdOperacion)
  const [errorFotos, setErrorFotos] = useState<string | null>(null)
  const [error, setError] = useState<ErrorEjecucion | null>(null)
  const {
    register,
    handleSubmit,
    setError: setErrorCampo,
    formState: { errors },
  } = useForm<CamposCierre, unknown, ValoresCierre>({
    resolver: zodResolver(esquemaCierre),
    defaultValues: { diagnostico: '', solucion: '', observaciones: '' },
  })

  // Las vistas previas que queden se liberan al salir de la pantalla sin cerrar.
  const fotosActuales = useRef(fotos)
  useEffect(() => {
    fotosActuales.current = fotos
  }, [fotos])
  useEffect(() => () => liberar(fotosActuales.current), [])

  function cambiarFotos(nuevas: FotoParaCierre[]) {
    setFotos(nuevas)
    if (nuevas.length > 0) setErrorFotos(null)
  }

  /** CU-3 paso 7: al menos una foto. Se revisa junto con los campos, para marcar todo de una vez. */
  function faltanFotos() {
    if (fotos.length > 0) return false
    setErrorFotos('Agregá al menos una foto.')
    return true
  }

  async function enviar(valores: ValoresCierre) {
    if (faltanFotos()) return
    setError(null)
    try {
      await cerrar.mutateAsync({ datos: { idOperacion, ...valores }, fotos })
      // Ya se enviaron: se liberan acá (liberar dos veces la misma URL no hace nada).
      liberar(fotos)
      setFotos([])
    } catch (e) {
      if (tipoProblema(obtenerProblemDetail(e)) === TIPOS_PROBLEMA.validacion) {
        const general = aplicarErroresDelServidor(e, setErrorCampo, CAMPOS)
        setError(general ? { tipo: 'otro', mensaje: general } : null)
        return
      }
      const problema = mensajeErrorEjecucion(e)
      if (problema.tipo === 'fotos') {
        setErrorFotos(problema.mensaje)
        return
      }
      // Ese id ya cerró otra OT: este cierre necesita uno propio.
      if (problema.tipo === 'repetida') setIdOperacion(generarIdOperacion())
      setError(problema)
    }
  }

  const terminado = error !== null && SIN_REINTENTO.includes(error.tipo)
  const enviando = cerrar.isPending

  return (
    <form
      className="formulario-cierre"
      aria-labelledby="titulo-cierre"
      noValidate
      onSubmit={handleSubmit(enviar, faltanFotos)}
    >
      <h2 id="titulo-cierre">Cierre técnico</h2>
      <fieldset className="formulario-cierre-campos" disabled={enviando || terminado}>
        <Campo
          id="diagnostico"
          etiqueta="Diagnóstico"
          ayuda="Qué encontraste."
          error={errors.diagnostico?.message}
          registro={register('diagnostico')}
          obligatorio
        />
        <Campo
          id="solucion"
          etiqueta="Solución"
          ayuda="Qué hiciste para resolverlo."
          error={errors.solucion?.message}
          registro={register('solucion')}
          obligatorio
        />
        <Campo
          id="observaciones"
          etiqueta="Observaciones"
          ayuda="Opcional."
          error={errors.observaciones?.message}
          registro={register('observaciones')}
        />
      </fieldset>

      <FotosCierre fotos={fotos} onCambio={cambiarFotos} deshabilitado={enviando || terminado} />
      {errorFotos && (
        <p className="campo-error" role="alert">
          {errorFotos}
        </p>
      )}

      {error && (
        <p className="alerta alerta-error" role="alert">
          {error.mensaje}
          {terminado && (
            <>
              {' '}
              <Link to="/mis-ots">Volver a Mis OTs</Link>
            </>
          )}
        </p>
      )}

      {!terminado && (
        <button type="submit" className="boton boton-primario boton-grande" disabled={enviando}>
          {enviando
            ? 'Enviando...'
            : error?.tipo === 'sin-conexion'
              ? 'Reintentar'
              : 'Confirmar cierre técnico'}
        </button>
      )}
    </form>
  )
}

type PropsCampo = {
  id: (typeof CAMPOS)[number]
  etiqueta: string
  ayuda: string
  error: string | undefined
  registro: UseFormRegisterReturn
  obligatorio?: boolean
}

function Campo({ id, etiqueta, ayuda, error, registro, obligatorio = false }: PropsCampo) {
  const idCampo = `cierre-${id}`
  return (
    <div className="campo">
      <label htmlFor={idCampo}>{etiqueta}</label>
      <textarea
        id={idCampo}
        rows={3}
        aria-required={obligatorio || undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${idCampo}-error` : `${idCampo}-ayuda`}
        {...registro}
      />
      {error ? (
        <span id={`${idCampo}-error`} className="campo-error">
          {error}
        </span>
      ) : (
        <span id={`${idCampo}-ayuda`} className="campo-ayuda">
          {ayuda}
        </span>
      )}
    </div>
  )
}
