import type { ReactNode } from 'react'
import { Modal } from './Modal'

type Props = {
  titulo: string
  children: ReactNode
  textoConfirmar: string
  onConfirmar: () => void
  onCancelar: () => void
  procesando?: boolean
}

/** Confirmación de una acción irreversible o delicada (1.13.1.6.1). Cancelar no cambia nada. */
export function DialogoConfirmacion({
  titulo,
  children,
  textoConfirmar,
  onConfirmar,
  onCancelar,
  procesando = false,
}: Props) {
  return (
    <Modal titulo={titulo} onCerrar={onCancelar}>
      {children}
      <div className="modal-acciones">
        <button type="button" className="boton boton-secundario" onClick={onCancelar}>
          Cancelar
        </button>
        <button
          type="button"
          className="boton boton-peligro"
          onClick={onConfirmar}
          disabled={procesando}
        >
          {textoConfirmar}
        </button>
      </div>
    </Modal>
  )
}
