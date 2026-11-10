import { useState, type Ref } from 'react'
import { useDebounce } from '../../../shared/hooks/useDebounce'
import { MINIMO_BUSQUEDA, useBuscarInstalaciones } from '../hooks/useInstalaciones'
import type { Instalacion } from '../types'
import './BuscadorInstalacion.css'

type Props = {
  id: string
  /** Id de la instalación elegida, o null. */
  value: number | null
  onChange: (id: number | null) => void
  onBlur?: () => void
  inputRef?: Ref<HTMLInputElement>
  invalido?: boolean
  /** Id del mensaje de error, para aria-describedby. */
  idError?: string
}

function etiquetaInstalacion(instalacion: Pick<Instalacion, 'codigo' | 'nombre'>) {
  return `${instalacion.codigo} — ${instalacion.nombre}`
}

/**
 * Elegir una instalación activa buscándola por código o nombre (HU-03). Busca mientras se escribe,
 * con una demora para no pedir en cada tecla. Elegir en el mapa queda para HU-11.
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
  const [elegida, setElegida] = useState<Instalacion | null>(null)
  const buscado = useDebounce(texto.trim())
  const busqueda = useBuscarInstalaciones(buscado)
  const buscando = buscado.length >= MINIMO_BUSQUEDA

  // El formulario manda: si se limpió (por ejemplo, al registrar otro), se vuelve a buscar.
  const mostrada = value !== null && elegida?.id === value ? elegida : null

  function elegir(instalacion: Instalacion) {
    setElegida(instalacion)
    setTexto('')
    onChange(instalacion.id)
  }

  function cambiar() {
    setElegida(null)
    onChange(null)
  }

  if (mostrada) {
    return (
      <div className="buscador-instalacion-elegida">
        <span id={id}>{etiquetaInstalacion(mostrada)}</span>
        <button type="button" className="boton boton-secundario" onClick={cambiar}>
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
