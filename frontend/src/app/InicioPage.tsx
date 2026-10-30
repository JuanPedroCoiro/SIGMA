import { ETIQUETA_ROL } from '../shared/lib/roles'
import { useSesion } from './sesion/useSesion'

export function InicioPage() {
  const sesion = useSesion()
  if (sesion.estado !== 'autenticado') return null
  const { usuario } = sesion

  return (
    <section>
      <h1>Hola, {usuario.nombre}</h1>
      <p>
        Ingresaste como <strong>{ETIQUETA_ROL[usuario.rol]}</strong>. Elegí una opción del menú para
        empezar.
      </p>
    </section>
  )
}
