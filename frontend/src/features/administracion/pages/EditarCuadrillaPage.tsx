import { Link, useNavigate, useParams } from 'react-router-dom'
import { CuadrillaForm } from '../components/CuadrillaForm'
import { useCuadrillas, useModificarCuadrilla } from '../hooks/useCuadrillas'

/** Edición del nombre y la zona de una cuadrilla. Los datos salen del catálogo ya cargado. */
export function EditarCuadrillaPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const cuadrillas = useCuadrillas()
  const modificar = useModificarCuadrilla(id)

  if (cuadrillas.isPending) return <p>Cargando...</p>
  const cuadrilla = cuadrillas.data?.find((c) => c.id === id)
  if (!cuadrilla) {
    return (
      <p className="alerta alerta-error" role="alert">
        No se encontró la cuadrilla. <Link to="/cuadrillas">Volver al listado</Link>
      </p>
    )
  }

  return (
    <section>
      <h1>Editar cuadrilla</h1>
      <CuadrillaForm
        valoresIniciales={{ nombre: cuadrilla.nombre, zona: cuadrilla.zona ?? '' }}
        textoEnviar="Guardar cambios"
        onEnviar={async (valores) => {
          await modificar.mutateAsync(valores)
          navigate('/cuadrillas', { state: { mensaje: 'Cambios guardados.' } })
        }}
      />
    </section>
  )
}
