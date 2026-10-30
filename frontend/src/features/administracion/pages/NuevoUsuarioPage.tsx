import { useNavigate } from 'react-router-dom'
import { UsuarioForm } from '../components/UsuarioForm'
import { useCrearUsuario } from '../hooks/useUsuarios'

/** CU-13a: alta de un usuario con su contraseña inicial. */
export function NuevoUsuarioPage() {
  const navigate = useNavigate()
  const crear = useCrearUsuario()

  return (
    <section>
      <h1>Nuevo usuario</h1>
      <UsuarioForm
        conPassword
        textoEnviar="Crear usuario"
        urlCancelar="/usuarios"
        onEnviar={async (valores) => {
          const creado = await crear.mutateAsync(valores)
          navigate(`/usuarios/${creado.id}`, { state: { mensaje: 'Usuario creado.' } })
        }}
      />
    </section>
  )
}
