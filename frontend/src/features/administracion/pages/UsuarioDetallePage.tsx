import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { useSesion } from '../../../app/sesion/useSesion'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import { DialogoConfirmacion } from '../../../shared/components/DialogoConfirmacion'
import { ETIQUETA_ROL } from '../../../shared/lib/roles'
import { EstadoUsuario } from '../components/EstadoUsuario'
import { RestablecerPasswordDialogo } from '../components/RestablecerPasswordDialogo'
import { useCuadrillas } from '../hooks/useCuadrillas'
import { useCambiarEstadoUsuario, useRestablecerPassword, useUsuario } from '../hooks/useUsuarios'
import './UsuarioDetallePage.css'

type Dialogo = 'desactivar' | 'restablecer' | null

/** CU-13c (detalle), CU-13b (restablecer contraseña) y CU-13d (desactivar y activar). */
export function UsuarioDetallePage() {
  const id = Number(useParams().id)
  const location = useLocation()
  const sesion = useSesion()
  const usuario = useUsuario(id)
  const cuadrillas = useCuadrillas()
  const restablecer = useRestablecerPassword(id)
  const cambiarEstado = useCambiarEstadoUsuario(id)
  const [dialogo, setDialogo] = useState<Dialogo>(null)
  const [mensaje, setMensaje] = useState<string | null>(
    (location.state as { mensaje?: string } | null)?.mensaje ?? null,
  )
  const [error, setError] = useState<string | null>(null)

  if (usuario.isPending) return <p>Cargando...</p>
  if (usuario.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        No se encontró el usuario. <Link to="/usuarios">Volver al listado</Link>
      </p>
    )
  }

  const datos = usuario.data
  // La Jefatura no puede desactivar su propia cuenta.
  const esUnoMismo = sesion.estado === 'autenticado' && sesion.usuario.id === datos.id
  const cuadrilla = cuadrillas.data?.find((c) => c.id === datos.idCuadrilla)

  async function ejecutar(accion: () => Promise<unknown>, exito: string) {
    setMensaje(null)
    setError(null)
    try {
      await accion()
      setMensaje(exito)
    } catch (e) {
      setError(
        obtenerProblemDetail(e)?.detail ?? 'No se pudo completar la acción. Volvé a intentar.',
      )
    } finally {
      setDialogo(null)
    }
  }

  return (
    <section className="usuario-detalle">
      <p>
        <Link to="/usuarios">← Usuarios</Link>
      </p>
      <h1>{datos.nombre}</h1>

      {mensaje && (
        <p className="alerta alerta-exito" role="status">
          {mensaje}
        </p>
      )}
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}

      <dl className="usuario-datos">
        <dt>Correo (usuario de acceso)</dt>
        <dd>{datos.correo}</dd>
        <dt>Rol</dt>
        <dd>{ETIQUETA_ROL[datos.rol]}</dd>
        <dt>Cuadrilla</dt>
        <dd>{cuadrilla?.nombre ?? '—'}</dd>
        <dt>Estado</dt>
        <dd>
          <EstadoUsuario activo={datos.activo} />
        </dd>
      </dl>

      <div className="usuario-acciones">
        <Link to={`/usuarios/${datos.id}/editar`} className="boton boton-secundario">
          Editar
        </Link>
        <button
          type="button"
          className="boton boton-secundario"
          onClick={() => setDialogo('restablecer')}
        >
          Restablecer contraseña
        </button>
        {datos.activo && !esUnoMismo && (
          <button
            type="button"
            className="boton boton-peligro"
            onClick={() => setDialogo('desactivar')}
          >
            Desactivar
          </button>
        )}
        {!datos.activo && (
          <button
            type="button"
            className="boton boton-primario"
            disabled={cambiarEstado.isPending}
            onClick={() =>
              ejecutar(() => cambiarEstado.mutateAsync('activar'), 'Usuario activado.')
            }
          >
            Activar
          </button>
        )}
      </div>

      {dialogo === 'desactivar' && (
        <DialogoConfirmacion
          titulo="Desactivar usuario"
          textoConfirmar="Desactivar"
          procesando={cambiarEstado.isPending}
          onCancelar={() => setDialogo(null)}
          onConfirmar={() =>
            ejecutar(() => cambiarEstado.mutateAsync('desactivar'), 'Usuario desactivado.')
          }
        >
          <p>
            <strong>{datos.nombre}</strong> no va a poder ingresar al sistema hasta que lo actives
            de nuevo. Su historial se conserva.
          </p>
        </DialogoConfirmacion>
      )}

      {dialogo === 'restablecer' && (
        <RestablecerPasswordDialogo
          nombreUsuario={datos.nombre}
          onCerrar={() => setDialogo(null)}
          onRestablecer={async (password) => {
            await restablecer.mutateAsync(password)
            setDialogo(null)
            setError(null)
            setMensaje('Contraseña restablecida. Comunicale la nueva al usuario.')
          }}
        />
      )}
    </section>
  )
}
