import { useEffect, useId, useRef, type ReactNode } from 'react'
import './Modal.css'

type Props = {
  titulo: string
  onCerrar: () => void
  children: ReactNode
}

/** Ventana modal. Se cierra con Escape o tocando afuera, y lleva el foco al primer control. */
export function Modal({ titulo, onCerrar, children }: Props) {
  const idTitulo = useId()
  const panel = useRef<HTMLDivElement>(null)
  // En una ref para que el efecto corra solo al abrir y no robe el foco en cada render.
  const cerrar = useRef(onCerrar)

  useEffect(() => {
    cerrar.current = onCerrar
  })

  useEffect(() => {
    panel.current?.querySelector<HTMLElement>('input, select, button')?.focus()
    function alPresionar(evento: KeyboardEvent) {
      if (evento.key === 'Escape') cerrar.current()
    }
    document.addEventListener('keydown', alPresionar)
    return () => document.removeEventListener('keydown', alPresionar)
  }, [])

  return (
    <div className="modal-fondo" onClick={() => cerrar.current()}>
      <div
        ref={panel}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        onClick={(evento) => evento.stopPropagation()}
      >
        <h2 id={idTitulo}>{titulo}</h2>
        {children}
      </div>
    </div>
  )
}
