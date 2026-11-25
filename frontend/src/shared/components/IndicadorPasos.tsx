import './IndicadorPasos.css'

type Props = {
  /** Empieza en 1. */
  paso: number
  /** El paso más avanzado al que se llegó: los anteriores ya están completos. */
  pasoMaximo: number
  /** El título de cada paso, en orden: el primero es el del paso 1. */
  titulos: readonly string[]
}

/**
 * «Paso 2 de 3 · Qué pasó», y en pantallas anchas todos los pasos en fila. Lo usan los
 * asistentes en pasos: el registro de incidentes (#69) y el cierre técnico (HU-07).
 */
export function IndicadorPasos({ paso, pasoMaximo, titulos }: Props) {
  return (
    <div className="indicador-pasos">
      <p className="indicador-pasos-actual">
        Paso {paso} de {titulos.length}
        <span className="indicador-pasos-titulo"> · {titulos[paso - 1]}</span>
      </p>
      <ol className="indicador-pasos-lista" aria-hidden="true">
        {titulos.map((titulo, i) => {
          const numero = i + 1
          return (
            <li
              key={titulo}
              className={
                numero === paso
                  ? 'indicador-paso-actual'
                  : numero < pasoMaximo
                    ? 'indicador-paso-completo'
                    : undefined
              }
            >
              <span className="indicador-paso-numero">{numero}</span>
              {titulo}
            </li>
          )
        })}
      </ol>
    </div>
  )
}
