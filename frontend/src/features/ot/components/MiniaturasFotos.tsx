import type { FotoElegida } from '../fotos'
import './MiniaturasFotos.css'

type Props = {
  fotos: readonly FotoElegida[]
  /** Nombre accesible de la lista; por defecto, «Fotos elegidas». */
  etiqueta?: string
  /** Sin esto, solo se muestran (en el resumen). */
  onQuitar?: (foto: FotoElegida) => void
}

/**
 * Las fotos elegidas, con su vista previa: en el registro de incidentes (#71) y en el resumen del
 * cierre técnico (HU-07).
 */
export function MiniaturasFotos({ fotos, etiqueta = 'Fotos elegidas', onQuitar }: Props) {
  if (fotos.length === 0) return null
  return (
    <ul className="fotos-miniaturas" aria-label={etiqueta}>
      {fotos.map((foto) => (
        <li key={foto.vistaPrevia}>
          <img src={foto.vistaPrevia} alt={foto.archivo.name} />
          {onQuitar && (
            <button
              type="button"
              className="boton boton-secundario"
              aria-label={`Quitar ${foto.archivo.name}`}
              onClick={() => onQuitar(foto)}
            >
              Quitar
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}
