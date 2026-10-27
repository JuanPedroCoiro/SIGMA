import { Link } from 'react-router-dom'
import { Paginacion } from '../../../shared/components/Paginacion'
import { usePaginado } from '../../../shared/hooks/usePaginado'
import { ETIQUETA_ROL } from '../../../shared/lib/roles'
import { EstadoUsuario } from '../components/EstadoUsuario'
import { useCuadrillas } from '../hooks/useCuadrillas'
import { useUsuarios } from '../hooks/useUsuarios'
import './UsuariosPage.css'

/** CU-13c: listado paginado de usuarios. */
export function UsuariosPage() {
  const { pagina, irAPagina } = usePaginado()
  const usuarios = useUsuarios(pagina)
  const cuadrillas = useCuadrillas()
  const nombreCuadrilla = new Map((cuadrillas.data ?? []).map((c) => [c.id, c.nombre]))

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Usuarios</h1>
        <Link to="/usuarios/nuevo" className="boton boton-primario">
          Nuevo usuario
        </Link>
      </div>

      {usuarios.isPending && <p>Cargando usuarios...</p>}
      {usuarios.isError && (
        <p className="alerta alerta-error" role="alert">
          No se pudo cargar el listado de usuarios. Volvé a intentar en unos minutos.
        </p>
      )}

      {usuarios.data && (
        <>
          <table className="tabla-usuarios">
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col">Correo</th>
                <th scope="col">Rol</th>
                <th scope="col">Cuadrilla</th>
                <th scope="col">Estado</th>
              </tr>
            </thead>
            <tbody>
              {usuarios.data.contenido.map((usuario) => (
                <tr key={usuario.id}>
                  <td data-etiqueta="Nombre">
                    <Link to={`/usuarios/${usuario.id}`}>{usuario.nombre}</Link>
                  </td>
                  <td data-etiqueta="Correo">{usuario.correo}</td>
                  <td data-etiqueta="Rol">{ETIQUETA_ROL[usuario.rol]}</td>
                  <td data-etiqueta="Cuadrilla">
                    {usuario.idCuadrilla ? (nombreCuadrilla.get(usuario.idCuadrilla) ?? '—') : '—'}
                  </td>
                  <td data-etiqueta="Estado">
                    <EstadoUsuario activo={usuario.activo} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Paginacion
            pagina={usuarios.data.pagina}
            totalPaginas={usuarios.data.totalPaginas}
            totalElementos={usuarios.data.totalElementos}
            onCambiar={irAPagina}
          />
        </>
      )}
    </section>
  )
}
