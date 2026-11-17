import type { FotoElegida } from '../../fotos'

type Props = {
  fotos: readonly FotoElegida[]
  /** Sin esto, solo se muestran (en el resumen). */
  onQuitar?: (foto: FotoElegida) => void
}

/** Las fotos elegidas en el registro (#71), con su vista previa. */
export function MiniaturasFotos({ fotos, onQuitar }: Props) {
  if (fotos.length === 0) return null
  return (
    <ul className="fotos-miniaturas" aria-label="Fotos elegidas">
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
