import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { FormProvider, useForm, useWatch, type FieldErrors } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router-dom'
import {
  obtenerProblemDetail,
  tipoProblema,
  TIPOS_PROBLEMA,
} from '../../../shared/api/problemDetail'
import { IndicadorPasos } from '../../../shared/components/IndicadorPasos'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { esquemaCierre, type CamposCierre, type ValoresCierre } from '../esquemas'
import type { FotoParaCierre } from '../fotosCierre'
import { useCerrarOrden } from '../hooks/useOrden'
import { generarIdOperacion } from '../idOperacion'
import { mensajeErrorEjecucion, type ErrorEjecucion } from '../mensajesEjecucion'
import type { OrdenTrabajoDetalle } from '../types'
import { AccionesPausa } from './AccionesPausa'
import { PasoFotos } from './cierre-tecnico/PasoFotos'
import { PasoTrabajo } from './cierre-tecnico/PasoTrabajo'
import {
  CAMPOS,
  leerPaso,
  PASO_DE_LAS_FOTOS,
  PASO_DE_LOS_CAMPOS,
  TITULO_PASO,
  TITULOS_PASOS,
  type Campo,
  type NumeroPaso,
} from './cierre-tecnico/pasos'
import { ResumenCierre } from './cierre-tecnico/ResumenCierre'
import './FormularioCierre.css'

type Props = {
  orden: Pick<OrdenTrabajoDetalle, 'id' | 'numero' | 'estado'>
}

/** Errores después de los cuales no tiene sentido reintentar: la OT ya no es para cerrar. */
const SIN_REINTENTO: readonly ErrorEjecucion['tipo'][] = ['reasignada', 'ya-cerrada', 'anulada']

const MENSAJE_CAMPOS_RECHAZADOS = 'Revisá los datos marcados.'
const MENSAJE_SIN_FOTOS = 'Agregá al menos una foto.'
const AVISO_PAUSA = 'Vas a perder lo cargado del cierre. ¿Pausar igual?'

function liberar(fotos: readonly FotoParaCierre[]) {
  fotos.forEach((foto) => URL.revokeObjectURL(foto.vistaPrevia))
}

/**
 * Cierre técnico en pasos (CU-3 pasos 6 a 10, HU-07 y HU-08), con el patrón del registro de
 * incidentes (#69): Trabajo, Fotos y Revisar y enviar. El paso va en la URL (`?paso=2`), así
 * «Atrás» del navegador vuelve al anterior; no se puede saltar a un paso sin completar los
 * anteriores.
 *
 * Todo lo cargado vive acá, no en cada paso: los textos (react-hook-form), las fotos y el
 * idOperacion, que se genera una vez por cierre. Si el envío falla (7b), se queda en «Revisar y
 * enviar» y el reintento manda exactamente lo mismo, así el backend no cierra dos veces ni duplica
 * las fotos (R8).
 */
export function FormularioCierre({ orden }: Props) {
  const [parametros, setParametros] = useSearchParams()
  const cerrar = useCerrarOrden(orden.id)
  const [fotos, setFotos] = useState<FotoParaCierre[]>([])
  const [idOperacion, setIdOperacion] = useState(generarIdOperacion)
  const [errorFotos, setErrorFotos] = useState<string | null>(null)
  /** El error del último envío, que se muestra en «Revisar y enviar». */
  const [error, setError] = useState<ErrorEjecucion | null>(null)
  /** Un rechazo del backend que llevó a un paso anterior: se muestra en ese paso. */
  const [alerta, setAlerta] = useState<{ paso: NumeroPaso; mensaje: string } | null>(null)
  /** El paso más avanzado al que se llegó con los anteriores completos. */
  const [pasoMaximo, setPasoMaximo] = useState<NumeroPaso>(1)
  /** Si se vino desde el resumen con «Editar»: el botón principal vuelve al resumen. */
  const [editando, setEditando] = useState(false)
  /** Si ya se intentó avanzar desde el paso 1: desde ahí, los errores se corrigen al escribir. */
  const [intentado, setIntentado] = useState(false)

  const formulario = useForm<CamposCierre, unknown, ValoresCierre>({
    resolver: zodResolver(esquemaCierre),
    defaultValues: { diagnostico: '', solucion: '', observaciones: '' },
  })
  const { control, handleSubmit, trigger, setError: setErrorCampo, getFieldState } = formulario
  const { setFocus, subscribe } = formulario

  // Al salir sin cerrar se liberan las vistas previas. Se lee la lista en una ref para no
  // liberarlas cada vez que cambia: las que se quitan, FotosCierre las libera al quitarlas.
  const fotosActuales = useRef<FotoParaCierre[]>([])
  useEffect(() => {
    fotosActuales.current = fotos
  }, [fotos])
  useEffect(() => {
    const actuales = fotosActuales
    return () => liberar(actuales.current)
  }, [])

  const pedido = leerPaso(parametros.get('paso'))
  const paso: NumeroPaso = pedido > pasoMaximo ? pasoMaximo : pedido

  // Con una recarga o un link a un paso sin completar los anteriores, se vuelve al que falta.
  useEffect(() => {
    if (pedido > pasoMaximo) setParametros(conPaso(pasoMaximo), { replace: true })
  }, [pedido, pasoMaximo, setParametros])

  // Después de un intento fallido en el paso 1, cada cambio vuelve a validarlo.
  useEffect(() => {
    if (paso !== PASO_DE_LOS_CAMPOS || !intentado) return
    return subscribe({
      formState: { values: true },
      callback: ({ name }) => {
        if (name && CAMPOS.includes(name as Campo)) void trigger(CAMPOS)
      },
    })
  }, [paso, intentado, subscribe, trigger])

  // Al cambiar de paso, el foco va al título del paso o al campo que rechazó el backend.
  const titulo = useRef<HTMLHeadingElement>(null)
  const campoConFoco = useRef<Campo | null>(null)
  const pasoMostrado = useRef(paso)
  useEffect(() => {
    if (pasoMostrado.current === paso) return
    pasoMostrado.current = paso
    if (campoConFoco.current) {
      setFocus(campoConFoco.current)
      campoConFoco.current = null
    } else {
      titulo.current?.focus()
    }
  }, [paso, setFocus])

  // Si ya hay algo cargado, pausar lo pierde (el formulario se desmonta): se pide confirmación.
  const textos = useWatch({ control, name: ['diagnostico', 'solucion', 'observaciones'] })
  const hayAlgoCargado = fotos.length > 0 || textos.some((texto) => (texto ?? '').trim() !== '')

  function irA(destino: NumeroPaso) {
    setParametros(conPaso(destino))
  }

  function cambiarFotos(nuevas: FotoParaCierre[]) {
    setFotos(nuevas)
    if (nuevas.length > 0) setErrorFotos(null)
  }

  async function avanzar() {
    if (paso === PASO_DE_LOS_CAMPOS) {
      const valido = await trigger(CAMPOS, { shouldFocus: true })
      if (!valido) {
        setIntentado(true)
        return
      }
    } else if (fotos.length === 0) {
      // CU-3 paso 7: al menos una foto para seguir.
      setErrorFotos(MENSAJE_SIN_FOTOS)
      return
    }
    setAlerta(null)
    const destino: NumeroPaso = editando || paso === PASO_DE_LAS_FOTOS ? 3 : 2
    if (destino === 3) setEditando(false)
    setPasoMaximo((maximo) => (destino > maximo ? destino : maximo))
    irA(destino)
  }

  function editar(numero: Exclude<NumeroPaso, 3>) {
    setAlerta(null)
    setEditando(true)
    irA(numero)
  }

  /** Lleva al paso de los campos, con el foco en el primero que tiene error. */
  function volverAlCampo(campo: Campo, mensaje: string) {
    setIntentado(true)
    setEditando(true)
    setAlerta({ paso: PASO_DE_LOS_CAMPOS, mensaje })
    campoConFoco.current = campo
    irA(PASO_DE_LOS_CAMPOS)
  }

  async function enviar(valores: ValoresCierre) {
    setError(null)
    setAlerta(null)
    try {
      await cerrar.mutateAsync({ datos: { idOperacion, ...valores }, fotos })
      // Ya se enviaron: se liberan acá (liberar dos veces la misma URL no hace nada). La página
      // pasa a «Enviada, pendiente de validación» y saca el paso de la URL.
      liberar(fotos)
      setFotos([])
    } catch (e) {
      if (tipoProblema(obtenerProblemDetail(e)) === TIPOS_PROBLEMA.validacion) {
        const general = aplicarErroresDelServidor(e, setErrorCampo, CAMPOS)
        const conError = CAMPOS.find((campo) => getFieldState(campo).error)
        if (conError) volverAlCampo(conError, general ?? MENSAJE_CAMPOS_RECHAZADOS)
        else setError({ tipo: 'otro', mensaje: general ?? MENSAJE_CAMPOS_RECHAZADOS })
        return
      }
      const problema = mensajeErrorEjecucion(e)
      if (problema.tipo === 'fotos') {
        setErrorFotos(problema.mensaje)
        setEditando(true)
        irA(PASO_DE_LAS_FOTOS)
        return
      }
      // Ese id ya cerró otra OT: este cierre necesita uno propio.
      if (problema.tipo === 'repetida') setIdOperacion(generarIdOperacion())
      setError(problema)
    }
  }

  // No debería pasar, porque el paso 1 se validó al avanzar; por las dudas, se lleva al campo.
  function invalido(errores: FieldErrors<CamposCierre>) {
    const conError = CAMPOS.find((campo) => errores[campo])
    if (conError) volverAlCampo(conError, MENSAJE_CAMPOS_RECHAZADOS)
  }

  function alEnviar(evento: FormEvent<HTMLFormElement>) {
    if (paso === 3) {
      void handleSubmit(enviar, invalido)(evento)
    } else {
      evento.preventDefault()
      void avanzar()
    }
  }

  const enviando = cerrar.isPending
  const terminado = error !== null && SIN_REINTENTO.includes(error.tipo)
  const etiquetaPrincipal =
    paso === 3
      ? enviando
        ? 'Enviando...'
        : error?.tipo === 'sin-conexion'
          ? 'Reintentar'
          : 'Confirmar cierre técnico'
      : editando
        ? 'Volver al resumen'
        : 'Siguiente'

  return (
    <section className="cierre-tecnico" aria-labelledby="titulo-cierre">
      <h2 id="titulo-cierre">Cierre técnico</h2>
      <div className="cierre-tecnico-pausa">
        <AccionesPausa orden={orden} avisoAntesDePausar={hayAlgoCargado ? AVISO_PAUSA : null} />
      </div>
      <IndicadorPasos paso={paso} pasoMaximo={pasoMaximo} titulos={TITULOS_PASOS} />
      <FormProvider {...formulario}>
        <form
          className="formulario-cierre"
          aria-label="Cierre técnico"
          noValidate
          onSubmit={alEnviar}
        >
          <h3 ref={titulo} tabIndex={-1} className="formulario-cierre-titulo-paso">
            {TITULO_PASO[paso]}
          </h3>
          {alerta?.paso === paso && (
            <p className="alerta alerta-error" role="alert">
              {alerta.mensaje}
            </p>
          )}

          <fieldset className="formulario-cierre-paso" disabled={enviando || terminado}>
            {paso === 1 && <PasoTrabajo />}
            {paso === 2 && <PasoFotos fotos={fotos} onCambio={cambiarFotos} error={errorFotos} />}
            {paso === 3 && <ResumenCierre fotos={fotos} onEditar={editar} />}
          </fieldset>

          {paso === 3 && error && (
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
            <div className="formulario-cierre-navegacion">
              {paso > 1 && (
                <button
                  type="button"
                  className="boton boton-secundario"
                  disabled={enviando}
                  onClick={() => irA((paso - 1) as NumeroPaso)}
                >
                  Anterior
                </button>
              )}
              <button type="submit" className="boton boton-primario" disabled={enviando}>
                {etiquetaPrincipal}
              </button>
            </div>
          )}
        </form>
      </FormProvider>
    </section>
  )
}

/** Cambia solo `paso` en la URL; el paso 1 no lo lleva. */
function conPaso(paso: NumeroPaso) {
  return (actuales: URLSearchParams) => {
    const siguientes = new URLSearchParams(actuales)
    if (paso === 1) siguientes.delete('paso')
    else siguientes.set('paso', String(paso))
    return siguientes
  }
}
