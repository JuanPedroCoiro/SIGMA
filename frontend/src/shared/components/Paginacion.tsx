type Props = {
  /** Empieza en 0. */
  pagina: number
  totalPaginas: number
  totalElementos: number
  onCambiar: (pagina: number) => void
}

export function Paginacion({ pagina, totalPaginas, totalElementos, onCambiar }: Props) {
  if (totalPaginas <= 1) {
    return <p className="paginacion-resumen">{totalElementos} en total</p>
  }
  return (
    <nav className="paginacion" aria-label="Paginación">
      <button
        type="button"
        className="boton boton-secundario"
        onClick={() => onCambiar(pagina - 1)}
        disabled={pagina === 0}
      >
        Anterior
      </button>
      <span className="paginacion-resumen">
        Página {pagina + 1} de {totalPaginas} ({totalElementos} en total)
      </span>
      <button
        type="button"
        className="boton boton-secundario"
        onClick={() => onCambiar(pagina + 1)}
        disabled={pagina + 1 >= totalPaginas}
      >
        Siguiente
      </button>
    </nav>
  )
}
