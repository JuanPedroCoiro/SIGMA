import type { FotoParaCierre } from '../../fotosCierre'
import { FotosCierre } from '../FotosCierre'

type Props = {
  fotos: readonly FotoParaCierre[]
  onCambio: (fotos: FotoParaCierre[]) => void
  /** «Agregá al menos una foto.» o el rechazo del backend (foto-invalida). */
  error: string | null
}

/**
 * Paso 2 del cierre (CU-3 paso 7, HU-08): las fotos con su momento. Las fotos viven en el
 * formulario padre, así no se pierden al cambiar de paso.
 */
export function PasoFotos({ fotos, onCambio, error }: Props) {
  return (
    <div className="cierre-fotos">
      <FotosCierre fotos={fotos} onCambio={onCambio} />
      {error && (
        <p className="campo-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
