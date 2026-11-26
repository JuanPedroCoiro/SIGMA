import { useState, type ChangeEvent } from 'react'
import { useSesion } from '../../../app/sesion/useSesion'
import {
  MAXIMO_FOTOS,
  TIPOS_FOTO,
  mensajeErrorFotos,
  textoCantidadFotos,
  validarFotosNuevas,
} from '../fotos'
import { useFotosIncidente, useSubirFotosIncidente } from '../hooks/useIncidentes'
import '../pages/OrdenDetallePage.css'

type Props = {
  idIncidente: number
  /** Por defecto, «Fotos» (en el detalle de la OT, dentro de la sección del incidente). */
  titulo?: string
  /** Si Operador y Jefatura pueden agregar fotos acá. En el historial (HU-10), no. */
  permitirAgregar?: boolean
}

/**
 * Las fotos del incidente (#71, #85): en el detalle de la OT y en el historial de la instalación
 * (HU-10). Las ven Operador, Jefatura y el técnico que puede ver la OT o, sin OT, el incidente del
 * historial (el backend lo controla). Operador y Jefatura pueden agregar fotos después del
 * registro, hasta el máximo: por ejemplo, si falló la subida al registrar. Quien no puede agregar,
 * sin fotos, no ve la sección.
 */
export function FotosDelIncidente({
  idIncidente,
  titulo = 'Fotos',
  permitirAgregar = true,
}: Props) {
  const sesion = useSesion()
  const autenticado = sesion.estado === 'autenticado'
  const puedeSubir =
    permitirAgregar &&
    sesion.estado === 'autenticado' &&
    (sesion.usuario.rol === 'OPERADOR' || sesion.usuario.rol === 'JEFATURA')
  const fotos = useFotosIncidente(idIncidente, autenticado)

  if (!autenticado || fotos.isPending) return null
  if (fotos.isError) {
    return <p className="campo-ayuda">No se pudieron cargar las fotos del incidente.</p>
  }
  if (fotos.data.length === 0 && !puedeSubir) return null
  return (
    <>
      <h3 className="orden-fotos-titulo">{titulo}</h3>
      {fotos.data.length > 0 ? (
        <ul className="orden-fotos" aria-label="Fotos del incidente">
          {fotos.data.map((foto) => (
            <li key={foto.id}>
              <a href={foto.url} target="_blank" rel="noreferrer">
                <img src={foto.url} alt={foto.nombreOriginal} loading="lazy" />
              </a>
            </li>
          ))}
        </ul>
      ) : (
        <p className="campo-ayuda">El incidente no tiene fotos.</p>
      )}
      {puedeSubir && <AgregarFotos idIncidente={idIncidente} cantidadActual={fotos.data.length} />}
    </>
  )
}

/**
 * Agrega fotos a un incidente ya registrado (#85): se suben apenas se eligen. Las mismas
 * validaciones que en el registro; el backend vuelve a validar y controla el máximo.
 */
function AgregarFotos({
  idIncidente,
  cantidadActual,
}: {
  idIncidente: number
  cantidadActual: number
}) {
  const subir = useSubirFotosIncidente()
  const [errores, setErrores] = useState<string[]>([])
  const [aviso, setAviso] = useState<string | null>(null)
  const completo = cantidadActual >= MAXIMO_FOTOS

  function elegir(evento: ChangeEvent<HTMLInputElement>) {
    const archivos = Array.from(evento.target.files ?? [])
    // Así se puede volver a elegir el mismo archivo si la subida falló.
    evento.target.value = ''
    const { aceptadas, errores: rechazos } = validarFotosNuevas(cantidadActual, archivos)
    setErrores(rechazos)
    setAviso(null)
    if (aceptadas.length === 0) return
    subir.mutate(
      { idIncidente, archivos: aceptadas },
      {
        onSuccess: (subidas) => setAviso(`Se adjuntaron ${textoCantidadFotos(subidas.length)}.`),
        onError: (error) =>
          setErrores([...rechazos, `No se pudieron subir las fotos: ${mensajeErrorFotos(error)}`]),
      },
    )
  }

  return (
    <div className="campo orden-fotos-agregar">
      <label htmlFor="fotos-nuevas">Agregar fotos</label>
      <span className="campo-ayuda" id="fotos-nuevas-ayuda">
        Hasta {MAXIMO_FOTOS} en total, JPG, PNG o WEBP de hasta 10 MB cada una.
      </span>
      <input
        id="fotos-nuevas"
        type="file"
        accept={TIPOS_FOTO.join(',')}
        multiple
        disabled={completo || subir.isPending}
        aria-describedby="fotos-nuevas-ayuda"
        onChange={elegir}
      />
      {subir.isPending && <span className="campo-ayuda">Subiendo...</span>}
      {completo && (
        <span className="campo-ayuda">El incidente ya tiene {MAXIMO_FOTOS} fotos, el máximo.</span>
      )}
      {aviso && <p role="status">{aviso}</p>}
      {errores.length > 0 && (
        <ul className="campo-error" role="alert">
          {errores.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
