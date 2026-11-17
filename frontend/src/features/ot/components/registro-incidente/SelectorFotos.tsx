import { useState, type ChangeEvent } from 'react'
import { MAXIMO_FOTOS, TIPOS_FOTO, validarFotosNuevas, type FotoElegida } from '../../fotos'
import { MiniaturasFotos } from './MiniaturasFotos'

type Props = {
  fotos: readonly FotoElegida[]
  onCambio: (fotos: FotoElegida[]) => void
}

/**
 * Fotos del incidente (#71), opcionales. Se eligen acá y se suben después de crear el incidente.
 * En el celular, el selector de archivos de imagen también ofrece la cámara.
 */
export function SelectorFotos({ fotos, onCambio }: Props) {
  const [errores, setErrores] = useState<string[]>([])
  const completo = fotos.length >= MAXIMO_FOTOS

  function elegir(evento: ChangeEvent<HTMLInputElement>) {
    const archivos = Array.from(evento.target.files ?? [])
    // Así se puede volver a elegir el mismo archivo después de quitarlo.
    evento.target.value = ''
    const { aceptadas, errores: rechazos } = validarFotosNuevas(fotos.length, archivos)
    setErrores(rechazos)
    if (aceptadas.length === 0) return
    onCambio([
      ...fotos,
      ...aceptadas.map((archivo) => ({ archivo, vistaPrevia: URL.createObjectURL(archivo) })),
    ])
  }

  function quitar(foto: FotoElegida) {
    URL.revokeObjectURL(foto.vistaPrevia)
    setErrores([])
    onCambio(fotos.filter((f) => f !== foto))
  }

  return (
    <div className="campo selector-fotos">
      <label htmlFor="fotos">
        Fotos <span className="campo-nota">(opcional)</span>
      </label>
      <span className="campo-ayuda" id="fotos-ayuda">
        Hasta {MAXIMO_FOTOS}, JPG, PNG o WEBP de hasta 10 MB cada una. Por ejemplo, la que mandó el
        organismo con el reclamo. En el celular podés sacarlas con la cámara.
      </span>
      <MiniaturasFotos fotos={fotos} onQuitar={quitar} />
      <input
        id="fotos"
        type="file"
        accept={TIPOS_FOTO.join(',')}
        multiple
        disabled={completo}
        aria-describedby="fotos-ayuda"
        onChange={elegir}
      />
      {completo && (
        <span className="campo-ayuda">Ya elegiste {MAXIMO_FOTOS}: quitá una para cambiarla.</span>
      )}
      {errores.length > 0 && (
        <ul className="campo-error selector-fotos-errores" role="alert">
          {errores.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
