import { useState, type ChangeEvent } from 'react'
import { TIPOS_FOTO, validarFotosNuevas } from '../fotos'
import { MAXIMO_FOTOS_CIERRE, comprimirFoto, type FotoParaCierre } from '../fotosCierre'
import { ETIQUETA_MOMENTO, MOMENTOS_FOTO, type MomentoFoto } from '../types'
import './FotosCierre.css'

type Props = {
  fotos: readonly FotoParaCierre[]
  onCambio: (fotos: FotoParaCierre[]) => void
  deshabilitado?: boolean
}

/**
 * Fotos del cierre técnico (#90, HU-08, CU-3 pasos 4 y 7): el técnico las saca con la cámara o las
 * elige de la galería (4a) y clasifica cada una como Antes, Durante o Después. Se comprimen al
 * elegirlas. El formulario de cierre (#89) exige al menos una y las envía con cerrarOrden.
 */
export function FotosCierre({ fotos, onCambio, deshabilitado = false }: Props) {
  const [momento, setMomento] = useState<MomentoFoto>('ANTES')
  const [errores, setErrores] = useState<string[]>([])
  const [preparando, setPreparando] = useState(false)
  const completo = fotos.length >= MAXIMO_FOTOS_CIERRE

  async function elegir(evento: ChangeEvent<HTMLInputElement>) {
    const archivos = Array.from(evento.target.files ?? [])
    // Así se puede volver a elegir el mismo archivo después de quitarlo.
    evento.target.value = ''
    if (archivos.length === 0) return
    setPreparando(true)
    const comprimidas = await Promise.all(
      archivos.map((archivo) =>
        TIPOS_FOTO.includes(archivo.type) ? comprimirFoto(archivo) : archivo,
      ),
    )
    setPreparando(false)
    const { aceptadas, errores: rechazos } = validarFotosNuevas(
      fotos.length,
      comprimidas,
      MAXIMO_FOTOS_CIERRE,
    )
    setErrores(rechazos)
    if (aceptadas.length === 0) return
    onCambio([
      ...fotos,
      ...aceptadas.map((archivo) => ({
        archivo,
        momento,
        vistaPrevia: URL.createObjectURL(archivo),
      })),
    ])
  }

  function cambiarMomento(foto: FotoParaCierre, nuevo: MomentoFoto) {
    onCambio(fotos.map((f) => (f === foto ? { ...f, momento: nuevo } : f)))
  }

  function quitar(foto: FotoParaCierre) {
    URL.revokeObjectURL(foto.vistaPrevia)
    setErrores([])
    onCambio(fotos.filter((f) => f !== foto))
  }

  return (
    <fieldset className="fotos-cierre" disabled={deshabilitado}>
      <legend>Fotos</legend>
      <span className="campo-ayuda" id="fotos-cierre-ayuda">
        Al menos una y hasta {MAXIMO_FOTOS_CIERRE}. Indicá si es de antes, durante o después del
        trabajo.
      </span>
      <div className="fotos-cierre-agregar">
        <label htmlFor="fotos-cierre-momento">Momento de las próximas fotos</label>
        <select
          id="fotos-cierre-momento"
          value={momento}
          onChange={(e) => setMomento(e.target.value as MomentoFoto)}
        >
          {MOMENTOS_FOTO.map((m) => (
            <option key={m} value={m}>
              {ETIQUETA_MOMENTO[m]}
            </option>
          ))}
        </select>
        <label htmlFor="fotos-cierre">Sacar o elegir fotos</label>
        <input
          id="fotos-cierre"
          type="file"
          accept={TIPOS_FOTO.join(',')}
          multiple
          disabled={completo || preparando}
          aria-describedby="fotos-cierre-ayuda"
          onChange={(e) => void elegir(e)}
        />
      </div>
      {preparando && <span className="campo-ayuda">Preparando las fotos...</span>}
      {completo && (
        <span className="campo-ayuda">
          Ya hay {MAXIMO_FOTOS_CIERRE} fotos: quitá una para agregar otra.
        </span>
      )}
      {errores.length > 0 && (
        <ul className="campo-error" role="alert">
          {errores.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      )}
      {fotos.length > 0 && (
        <ul className="fotos-cierre-lista" aria-label="Fotos del cierre">
          {fotos.map((foto) => (
            <li key={foto.vistaPrevia}>
              <img src={foto.vistaPrevia} alt={foto.archivo.name} />
              <select
                aria-label={`Momento de ${foto.archivo.name}`}
                value={foto.momento}
                onChange={(e) => cambiarMomento(foto, e.target.value as MomentoFoto)}
              >
                {MOMENTOS_FOTO.map((m) => (
                  <option key={m} value={m}>
                    {ETIQUETA_MOMENTO[m]}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="boton-secundario"
                aria-label={`Quitar ${foto.archivo.name}`}
                onClick={() => quitar(foto)}
              >
                Quitar
              </button>
            </li>
          ))}
        </ul>
      )}
    </fieldset>
  )
}
