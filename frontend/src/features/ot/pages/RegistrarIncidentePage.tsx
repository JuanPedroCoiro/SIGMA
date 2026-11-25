import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { FormProvider, useForm, type FieldErrors } from 'react-hook-form'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { formatearFechaHoraMontevideo } from '../../../shared/lib/fechas'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import type { Instalacion } from '../../activos/types'
import { IndicadorPasos } from '../../../shared/components/IndicadorPasos'
import { PasoDonde } from '../components/registro-incidente/PasoDonde'
import { PasoQuePaso } from '../components/registro-incidente/PasoQuePaso'
import {
  CAMPOS,
  CAMPOS_POR_PASO,
  leerPaso,
  pasoDeCampo,
  TITULO_PASO,
  TITULOS_PASOS,
  type Campo,
  type NumeroPaso,
  type PasoConCampos,
} from '../components/registro-incidente/pasos'
import { ResumenIncidente } from '../components/registro-incidente/ResumenIncidente'
import { crearEsquemaIncidente, type CamposIncidente, type ValoresIncidente } from '../esquemas'
import { mensajeErrorFotos, textoCantidadFotos, type FotoElegida } from '../fotos'
import {
  useCategoriasFalla,
  useRegistrarIncidente,
  useSubirFotosIncidente,
} from '../hooks/useIncidentes'
import type { IncidenteResponse } from '../types'
import './RegistrarIncidentePage.css'

const RUTA = '/incidentes/nuevo'

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

const MENSAJE_CAMPOS_RECHAZADOS = 'Revisá los datos marcados.'

/** El resultado viaja en el estado del historial: Atrás vuelve a un registro nuevo, no a este. */
type EstadoRegistro = {
  resultado: IncidenteResponse
  fotosSubidas: number
  /** Por qué no se subieron las fotos (#71); el incidente igual quedó registrado. */
  errorFotos: string | null
}

/**
 * Registrar un incidente (CU-1, HU-03): Operador y Jefatura. Genera la OT correctiva con su
 * vencimiento, o deja el incidente en espera si la falla es de un tercero (4b). Se carga en pasos
 * (#69): Dónde, Qué pasó y un resumen, donde se confirma. Las fotos se suben después de crear el
 * incidente (#71).
 */
export function RegistrarIncidentePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const estado = location.state as EstadoRegistro | null
  // Los archivos no viajan en el historial: si falló la subida, quedan acá para reintentarla.
  const [fotosSinSubir, setFotosSinSubir] = useState<File[]>([])

  function mostrar(nuevo: EstadoRegistro) {
    navigate(RUTA, { replace: true, state: nuevo })
  }

  return (
    <section className="registrar-incidente">
      <h1>Registrar incidente</h1>
      {estado?.resultado ? (
        <ResultadoRegistro
          estado={estado}
          fotosSinSubir={fotosSinSubir}
          onFotosSubidas={(cantidad) => {
            setFotosSinSubir([])
            mostrar({ ...estado, fotosSubidas: cantidad, errorFotos: null })
          }}
          onErrorFotos={(mensaje) => mostrar({ ...estado, errorFotos: mensaje })}
          onOtro={() => {
            setFotosSinSubir([])
            navigate(RUTA)
          }}
        />
      ) : (
        <AsistenteIncidente
          onRegistrado={(registrado, sinSubir) => {
            setFotosSinSubir(sinSubir)
            mostrar(registrado)
          }}
        />
      )}
    </section>
  )
}

function ResultadoRegistro({
  estado: { resultado, fotosSubidas, errorFotos },
  fotosSinSubir,
  onFotosSubidas,
  onErrorFotos,
  onOtro,
}: {
  estado: EstadoRegistro
  fotosSinSubir: readonly File[]
  onFotosSubidas: (cantidad: number) => void
  onErrorFotos: (mensaje: string) => void
  onOtro: () => void
}) {
  const subirFotos = useSubirFotosIncidente()
  const ot = resultado.ordenTrabajo

  async function reintentar() {
    try {
      const subidas = await subirFotos.mutateAsync({
        idIncidente: resultado.id,
        archivos: fotosSinSubir,
      })
      onFotosSubidas(subidas.length)
    } catch (error) {
      onErrorFotos(mensajeErrorFotos(error))
    }
  }

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
        {fotosSubidas > 0 && ` Se adjuntaron ${textoCantidadFotos(fotosSubidas)}.`}
      </p>
      {errorFotos && (
        <div className="alerta alerta-error registrar-incidente-error-fotos" role="alert">
          <p>El incidente quedó registrado, pero no se pudieron subir las fotos. {errorFotos}</p>
          {fotosSinSubir.length > 0 && (
            <button
              type="button"
              className="boton boton-secundario"
              disabled={subirFotos.isPending}
              onClick={() => void reintentar()}
            >
              {subirFotos.isPending ? 'Subiendo...' : 'Reintentar subir las fotos'}
            </button>
          )}
        </div>
      )}
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

/**
 * Un solo formulario para los tres pasos: cada paso valida sus campos antes de avanzar y los
 * valores quedan aunque el paso se desmonte. El paso va en la URL (`?paso=2`), así Atrás en el
 * navegador vuelve al paso anterior; no se puede saltar a un paso sin completar los anteriores.
 */
function AsistenteIncidente({
  onRegistrado,
}: {
  /** `sinSubir`: las fotos que no se pudieron subir, para reintentar. */
  onRegistrado: (estado: EstadoRegistro, sinSubir: File[]) => void
}) {
  const [parametros, setParametros] = useSearchParams()
  const registrar = useRegistrarIncidente()
  const subirFotos = useSubirFotosIncidente()
  const [fotos, setFotos] = useState<FotoElegida[]>([])

  // Al salir del registro se liberan las vistas previas. Se lee la lista en una ref para no
  // liberarlas cada vez que cambia: las que se quitan se liberan al quitarlas.
  const fotosActuales = useRef<FotoElegida[]>([])
  useEffect(() => {
    fotosActuales.current = fotos
  }, [fotos])
  useEffect(() => {
    const actuales = fotosActuales
    return () => actuales.current.forEach((foto) => URL.revokeObjectURL(foto.vistaPrevia))
  }, [])
  const categorias = useCategoriasFalla()
  const catalogo = useMemo(() => categorias.data ?? [], [categorias.data])
  const esquema = useMemo(() => crearEsquemaIncidente(catalogo), [catalogo])
  const formulario = useForm<CamposIncidente, unknown, ValoresIncidente>({
    resolver: zodResolver(esquema),
    defaultValues: VALORES_INICIALES,
  })
  const {
    handleSubmit,
    trigger,
    setError,
    getFieldState,
    setFocus,
    subscribe,
    formState: { isSubmitting },
  } = formulario

  const [instalacion, setInstalacion] = useState<Instalacion | null>(null)
  /** El paso más avanzado al que se llegó con los anteriores válidos. */
  const [pasoMaximo, setPasoMaximo] = useState<NumeroPaso>(1)
  /** Si se vino desde el resumen con «Editar»: el botón principal vuelve al resumen. */
  const [editando, setEditando] = useState(false)
  /** Pasos en los que ya se intentó avanzar: desde ahí, los errores se corrigen al escribir. */
  const [intentados, setIntentados] = useState<ReadonlySet<PasoConCampos>>(() => new Set())
  const [alerta, setAlerta] = useState<{ paso: NumeroPaso; mensaje: string } | null>(null)

  const pedido = leerPaso(parametros.get('paso'))
  const paso: NumeroPaso = pedido > pasoMaximo ? pasoMaximo : pedido

  // Con una recarga o un link a un paso sin completar los anteriores, se vuelve al que falta.
  useEffect(() => {
    if (pedido > pasoMaximo) setParametros(parametrosDe(pasoMaximo), { replace: true })
  }, [pedido, pasoMaximo, setParametros])

  // Después de un intento fallido, cada cambio vuelve a validar el paso.
  useEffect(() => {
    if (paso === 3 || !intentados.has(paso)) return
    const campos: readonly string[] = CAMPOS_POR_PASO[paso]
    return subscribe({
      formState: { values: true },
      callback: ({ name }) => {
        if (name && campos.includes(name)) void trigger(CAMPOS_POR_PASO[paso])
      },
    })
  }, [paso, intentados, subscribe, trigger])

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

  function irA(destino: NumeroPaso) {
    setParametros(parametrosDe(destino))
  }

  function marcarIntentado(numero: PasoConCampos) {
    setIntentados((anteriores) => new Set(anteriores).add(numero))
  }

  async function avanzar(desde: PasoConCampos) {
    const valido = await trigger(CAMPOS_POR_PASO[desde], { shouldFocus: true })
    if (!valido) {
      marcarIntentado(desde)
      return
    }
    setAlerta(null)
    const destino: NumeroPaso = editando || desde === 2 ? 3 : 2
    if (destino === 3) setEditando(false)
    setPasoMaximo((maximo) => (destino > maximo ? destino : maximo))
    irA(destino)
  }

  function editar(numero: PasoConCampos) {
    setAlerta(null)
    setEditando(true)
    irA(numero)
  }

  /** Lleva al paso del primer campo con error (de la validación o del backend). */
  function volverAlCampo(campo: Campo, mensaje: string) {
    const destino = pasoDeCampo(campo)
    marcarIntentado(destino)
    setEditando(true)
    setAlerta({ paso: destino, mensaje })
    campoConFoco.current = campo
    irA(destino)
  }

  async function enviar(valores: ValoresIncidente) {
    setAlerta(null)
    let resultado: IncidenteResponse
    try {
      resultado = await registrar.mutateAsync(valores)
    } catch (error) {
      const general = aplicarErroresDelServidor(error, setError, CAMPOS)
      const conError = CAMPOS.find((campo) => getFieldState(campo).error)
      if (conError) {
        volverAlCampo(conError, general ?? MENSAJE_CAMPOS_RECHAZADOS)
      } else {
        setAlerta({ paso: 3, mensaje: general ?? MENSAJE_CAMPOS_RECHAZADOS })
      }
      return
    }

    // #71: si la subida falla, el incidente ya quedó creado; se avisa y se puede reintentar.
    const archivos = fotos.map((foto) => foto.archivo)
    if (archivos.length === 0) {
      onRegistrado({ resultado, fotosSubidas: 0, errorFotos: null }, [])
      return
    }
    try {
      const subidas = await subirFotos.mutateAsync({ idIncidente: resultado.id, archivos })
      onRegistrado({ resultado, fotosSubidas: subidas.length, errorFotos: null }, [])
    } catch (error) {
      onRegistrado({ resultado, fotosSubidas: 0, errorFotos: mensajeErrorFotos(error) }, archivos)
    }
  }

  // No debería pasar, porque cada paso se validó al avanzar; por las dudas, se lleva al campo.
  function invalido(errores: FieldErrors<CamposIncidente>) {
    const conError = CAMPOS.find((campo) => errores[campo])
    if (conError) volverAlCampo(conError, MENSAJE_CAMPOS_RECHAZADOS)
  }

  function alEnviar(evento: FormEvent<HTMLFormElement>) {
    if (paso === 3) {
      void handleSubmit(enviar, invalido)(evento)
    } else {
      evento.preventDefault()
      void avanzar(paso)
    }
  }

  const etiquetaPrincipal =
    paso === 3
      ? isSubmitting
        ? subirFotos.isPending
          ? 'Subiendo fotos...'
          : 'Registrando...'
        : 'Confirmar'
      : editando
        ? 'Volver al resumen'
        : 'Siguiente'

  return (
    <FormProvider {...formulario}>
      <IndicadorPasos paso={paso} pasoMaximo={pasoMaximo} titulos={TITULOS_PASOS} />
      <form className="registrar-incidente-form" noValidate onSubmit={alEnviar}>
        <h2 ref={titulo} tabIndex={-1} className="registrar-incidente-titulo-paso">
          {TITULO_PASO[paso]}
        </h2>
        {alerta?.paso === paso && (
          <p className="alerta alerta-error" role="alert">
            {alerta.mensaje}
          </p>
        )}

        {paso === 1 && <PasoDonde instalacion={instalacion} onInstalacion={setInstalacion} />}
        {paso === 2 && (
          <PasoQuePaso
            catalogo={catalogo}
            cargandoCategorias={categorias.isPending}
            errorCategorias={categorias.isError}
            fotos={fotos}
            onFotos={setFotos}
          />
        )}
        {paso === 3 && (
          <ResumenIncidente
            instalacion={instalacion}
            catalogo={catalogo}
            fotos={fotos}
            onEditar={editar}
          />
        )}

        <div className="registrar-incidente-navegacion">
          {paso > 1 && (
            <button
              type="button"
              className="boton boton-secundario"
              onClick={() => irA((paso - 1) as NumeroPaso)}
            >
              Anterior
            </button>
          )}
          <button type="submit" className="boton boton-primario" disabled={isSubmitting}>
            {etiquetaPrincipal}
          </button>
        </div>
      </form>
    </FormProvider>
  )
}

function parametrosDe(paso: NumeroPaso): Record<string, string> {
  return paso === 1 ? {} : { paso: String(paso) }
}
