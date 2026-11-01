import { useNavigate } from 'react-router-dom'
import { CuadrillaForm } from '../components/CuadrillaForm'
import { useCrearCuadrilla } from '../hooks/useCuadrillas'

/** Alta de una cuadrilla por la Jefatura. */
export function NuevaCuadrillaPage() {
  const navigate = useNavigate()
  const crear = useCrearCuadrilla()

  return (
    <section>
      <h1>Nueva cuadrilla</h1>
      <CuadrillaForm
        textoEnviar="Crear cuadrilla"
        onEnviar={async (valores) => {
          const creada = await crear.mutateAsync(valores)
          navigate('/cuadrillas', { state: { mensaje: `Cuadrilla ${creada.nombre} creada.` } })
        }}
      />
    </section>
  )
}
