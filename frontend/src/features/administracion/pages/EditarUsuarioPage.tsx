import { useNavigate, useParams } from 'react-router-dom'
import { UsuarioForm } from '../components/UsuarioForm'
import { useModificarUsuario, useUsuario } from '../hooks/useUsuarios'

/** CU-13b: edición de datos, rol y cuadrilla. La contraseña se cambia aparte. */
export function EditarUsuarioPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const usuario = useUsuario(id)
  const modificar = useModificarUsuario(id)

  if (usuario.isPending) return <p>Cargando...</p>
  if (usuario.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        No se encontró el usuario.
      </p>
    )
  }

  const { nombre, correo, rol, idCuadrilla } = usuario.data
  return (
    <section>
      <h1>Editar usuario</h1>
      <UsuarioForm
        conPassword={false}
        valoresIniciales={{
          nombre,
          correo,
          rol,
          idCuadrilla: idCuadrilla ? String(idCuadrilla) : '',
          password: '',
        }}
        textoEnviar="Guardar cambios"
        urlCancelar={`/usuarios/${id}`}
        onEnviar={async ({ nombre, correo, rol, idCuadrilla }) => {
          await modificar.mutateAsync({ nombre, correo, rol, idCuadrilla })
          navigate(`/usuarios/${id}`, { state: { mensaje: 'Cambios guardados.' } })
        }}
      />
    </section>
  )
}
