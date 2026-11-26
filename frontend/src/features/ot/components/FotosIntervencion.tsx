import { useId } from 'react'
import { useFotosOrden } from '../hooks/useOrden'
import { ETIQUETA_MOMENTO, MOMENTOS_FOTO } from '../types'
import '../pages/OrdenDetallePage.css'

type Props = {
  idOrden: number
  /** El nivel del título; el de cada momento es el siguiente. Por defecto, 2 (detalle de la OT). */
  nivelTitulo?: 2 | 3
}

/**
 * Las fotos del cierre técnico (#90), agrupadas por momento: en el detalle de la OT y en el
 * historial de la instalación (HU-10). Las ve quien puede ver la OT. Sin fotos (la OT todavía no
 * se cerró), no se muestra nada.
 */
export function FotosDeLaIntervencion({ idOrden, nivelTitulo = 2 }: Props) {
  const fotos = useFotosOrden(idOrden)
  const idTitulo = useId()
  const Titulo = nivelTitulo === 2 ? 'h2' : 'h3'
  const TituloMomento = nivelTitulo === 2 ? 'h3' : 'h4'

  if (fotos.isPending) return null
  if (fotos.isError) {
    return <p className="campo-ayuda">No se pudieron cargar las fotos de la intervención.</p>
  }
  if (fotos.data.length === 0) return null
  return (
    <section aria-labelledby={idTitulo}>
      <Titulo id={idTitulo}>Fotos de la intervención</Titulo>
      {MOMENTOS_FOTO.map((momento) => {
        const delMomento = fotos.data.filter((foto) => foto.momento === momento)
        if (delMomento.length === 0) return null
        return (
          <div key={momento}>
            <TituloMomento className="orden-fotos-titulo">
              {ETIQUETA_MOMENTO[momento]}
            </TituloMomento>
            <ul className="orden-fotos" aria-label={`Fotos: ${ETIQUETA_MOMENTO[momento]}`}>
              {delMomento.map((foto) => (
                <li key={foto.id}>
                  <a href={foto.url} target="_blank" rel="noreferrer">
                    <img src={foto.url} alt={foto.nombreOriginal} loading="lazy" />
                  </a>
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </section>
  )
}
