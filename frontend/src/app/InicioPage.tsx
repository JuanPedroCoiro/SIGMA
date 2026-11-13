import { Link } from 'react-router-dom'
import { TarjetasInicio } from '../features/pendientes/components/TarjetasInicio'
import { ETIQUETA_ROL } from '../shared/lib/roles'
import { useSesion } from './sesion/useSesion'

/** Saludo y, según el rol, un resumen con enlaces a lo más importante (#72). */
export function InicioPage() {
  const sesion = useSesion()
  if (sesion.estado !== 'autenticado') return null
  const { usuario } = sesion
  const puedeRegistrar = usuario.rol === 'JEFATURA' || usuario.rol === 'OPERADOR'

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Hola, {usuario.nombre}</h1>
        {puedeRegistrar && (
          <Link to="/incidentes/nuevo" className="boton boton-primario">
            Registrar incidente
          </Link>
        )}
      </div>
      <p>
        Ingresaste como <strong>{ETIQUETA_ROL[usuario.rol]}</strong>.
      </p>
      <TarjetasInicio />
    </section>
  )
}
