import { useState, type Ref } from 'react'
import { useDebounce } from '../../../shared/hooks/useDebounce'
import { etiquetaInstalacion } from '../etiquetas'
import { MINIMO_BUSQUEDA, useBuscarInstalaciones } from '../hooks/useInstalaciones'
import type { Instalacion } from '../types'
import './BuscadorInstalacion.css'

type Props = {
  id: string
  /** La instalación elegida, o null. La guarda quien usa el buscador. */
  value: Instalacion | null
  onChange: (instalacion: Instalacion | null) => void
  onBlur?: () => void
  inputRef?: Ref<HTMLInputElement>
  invalido?: boolean
  /** Id del mensaje de error, para aria-describedby. */
  idError?: string
}

/**
 * Elegir una instalación activa buscándola por código o nombre (HU-03). Busca mientras se escribe,
 * con una demora para no pedir en cada tecla. Es controlado: la instalación elegida la guarda quien
 * lo usa, así no se pierde si el buscador se desmonta (registro en pasos, #69) y el mapa (HU-11)
 * puede elegir la misma.
 */
export function BuscadorInstalacion({
  id,
  value,
  onChange,
  onBlur,
  inputRef,
  invalido,
  idError,
}: Props) {
  const [texto, setTexto] = useState('')
  const buscado = useDebounce(texto.trim())
  const busqueda = useBuscarInstalaciones(buscado)
  const buscando = buscado.length >= MINIMO_BUSQUEDA

  function elegir(instalacion: Instalacion) {
    setTexto('')
    onChange(instalacion)
  }

  if (value) {
    return (
      <div className="buscador-instalacion-elegida">
        <span id={id}>{etiquetaInstalacion(value)}</span>
        <button type="button" className="boton boton-secundario" onClick={() => onChange(null)}>
          Cambiar
        </button>
      </div>
    )
  }

  const resultados = buscando ? (busqueda.data?.contenido ?? []) : []

  return (
    <div className="buscador-instalacion">
      <input
        id={id}
        ref={inputRef}
        type="search"
        autoComplete="off"
        placeholder="Código o nombre"
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
        onBlur={onBlur}
        aria-invalid={invalido ? true : undefined}
        aria-describedby={idError}
      />
      {buscando && busqueda.isError && (
        <p className="campo-ayuda">No se pudo buscar. Volvé a intentar.</p>
      )}
      {buscando && busqueda.isSuccess && resultados.length === 0 && (
        <p className="campo-ayuda">No hay instalaciones activas que coincidan con «{buscado}».</p>
      )}
      {resultados.length > 0 && (
        <ul className="buscador-instalacion-resultados" aria-label="Instalaciones encontradas">
          {resultados.map((instalacion) => (
            <li key={instalacion.id}>
              <button type="button" onClick={() => elegir(instalacion)}>
                {etiquetaInstalacion(instalacion)}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
