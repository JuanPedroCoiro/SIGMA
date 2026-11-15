import { NUMEROS_PASO, TITULO_PASO, type NumeroPaso } from './pasos'

type Props = {
  paso: NumeroPaso
  /** El paso más avanzado al que se llegó: los anteriores ya están completos. */
  pasoMaximo: NumeroPaso
}

/** «Paso 2 de 3 · Qué pasó», y en pantallas anchas los tres pasos en fila. */
export function IndicadorPasos({ paso, pasoMaximo }: Props) {
  return (
    <div className="indicador-pasos">
      <p className="indicador-pasos-actual">
        Paso {paso} de {NUMEROS_PASO.length}
        <span className="indicador-pasos-titulo"> · {TITULO_PASO[paso]}</span>
      </p>
      <ol className="indicador-pasos-lista" aria-hidden="true">
        {NUMEROS_PASO.map((numero) => (
          <li
            key={numero}
            className={
              numero === paso
                ? 'indicador-paso-actual'
                : numero < pasoMaximo
                  ? 'indicador-paso-completo'
                  : undefined
            }
          >
            <span className="indicador-paso-numero">{numero}</span>
            {TITULO_PASO[numero]}
          </li>
        ))}
      </ol>
    </div>
  )
}
