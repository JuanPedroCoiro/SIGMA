import { useEffect, useState } from 'react'

/** El valor, pero recién cuando deja de cambiar durante `demora` ms (para no pedir en cada tecla). */
export function useDebounce<T>(valor: T, demora = 300): T {
  const [demorado, setDemorado] = useState(valor)
  useEffect(() => {
    const temporizador = setTimeout(() => setDemorado(valor), demora)
    return () => clearTimeout(temporizador)
  }, [valor, demora])
  return demorado
}
