import { useSearchParams } from 'react-router-dom'

/**
 * Página actual de un listado, guardada en la URL (`?pagina=2`) para que funcionen el botón
 * «atrás» y los enlaces. En la URL empieza en 1; `pagina` empieza en 0, como en la API.
 */
export function usePaginado() {
  const [parametros, setParametros] = useSearchParams()
  const enUrl = Number(parametros.get('pagina'))
  const pagina = Number.isInteger(enUrl) && enUrl > 1 ? enUrl - 1 : 0

  function irAPagina(nueva: number) {
    setParametros((actuales) => {
      const siguientes = new URLSearchParams(actuales)
      if (nueva <= 0) siguientes.delete('pagina')
      else siguientes.set('pagina', String(nueva + 1))
      return siguientes
    })
  }

  return { pagina, irAPagina }
}
