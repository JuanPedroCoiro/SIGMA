import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import { DialogoConfirmacion } from '../../../shared/components/DialogoConfirmacion'
import { EstadoUsuario } from '../components/EstadoUsuario'
import { useCambiarEstadoCuadrilla, useCuadrillas } from '../hooks/useCuadrillas'
import type { Cuadrilla } from '../types'
import './UsuariosPage.css'

type Confirmacion = { cuadrilla: Cuadrilla; accion: 'activar' | 'desactivar' } | null

/** Gestión de cuadrillas por la Jefatura: listado, alta, edición, activar y desactivar. */
export function CuadrillasPage() {
  const location = useLocation()
  const cuadrillas = useCuadrillas()
  const cambiarEstado = useCambiarEstadoCuadrilla()
  const [confirmacion, setConfirmacion] = useState<Confirmacion>(null)
  const [mensaje, setMensaje] = useState<string | null>(
    (location.state as { mensaje?: string } | null)?.mensaje ?? null,
  )
  const [error, setError] = useState<string | null>(null)

  async function confirmar() {
    if (!confirmacion) return
    const { cuadrilla, accion } = confirmacion
    setMensaje(null)
    setError(null)
    try {
      await cambiarEstado.mutateAsync({ id: cuadrilla.id, accion })
      setMensaje(
        `Cuadrilla ${cuadrilla.nombre} ${accion === 'activar' ? 'activada' : 'desactivada'}.`,
      )
    } catch (e) {
      setError(
        obtenerProblemDetail(e)?.detail ?? 'No se pudo completar la acción. Volvé a intentar.',
      )
    } finally {
      setConfirmacion(null)
    }
  }

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Cuadrillas</h1>
        <Link to="/cuadrillas/nueva" className="boton boton-primario">
          Nueva cuadrilla
        </Link>
      </div>

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

      {cuadrillas.isPending && <p>Cargando cuadrillas...</p>}
      {cuadrillas.isError && (
        <p className="alerta alerta-error" role="alert">
          No se pudo cargar el listado de cuadrillas. Volvé a intentar en unos minutos.
        </p>
      )}

      {cuadrillas.data && (
        <table className="tabla-usuarios">
          <thead>
            <tr>
              <th scope="col">Nombre</th>
              <th scope="col">Zona</th>
              <th scope="col">Estado</th>
              <th scope="col">Técnicos activos</th>
              <th scope="col">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {cuadrillas.data.map((cuadrilla) => (
              <tr key={cuadrilla.id}>
                <td data-etiqueta="Nombre">{cuadrilla.nombre}</td>
                <td data-etiqueta="Zona">{cuadrilla.zona ?? '—'}</td>
                <td data-etiqueta="Estado">
                  <EstadoUsuario activo={cuadrilla.activa} />
                </td>
                <td data-etiqueta="Técnicos">{cuadrilla.tecnicosActivos}</td>
                <td data-etiqueta="Acciones">
                  <div className="acciones-fila">
                    <Link
                      to={`/cuadrillas/${cuadrilla.id}/editar`}
                      className="boton boton-secundario"
                      aria-label={`Editar ${cuadrilla.nombre}`}
                    >
                      Editar
                    </Link>
                    <button
                      type="button"
                      className={
                        cuadrilla.activa ? 'boton boton-peligro' : 'boton boton-secundario'
                      }
                      aria-label={`${cuadrilla.activa ? 'Desactivar' : 'Activar'} ${cuadrilla.nombre}`}
                      onClick={() =>
                        setConfirmacion({
                          cuadrilla,
                          accion: cuadrilla.activa ? 'desactivar' : 'activar',
                        })
                      }
                    >
                      {cuadrilla.activa ? 'Desactivar' : 'Activar'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {confirmacion && (
        <DialogoConfirmacion
          titulo={
            confirmacion.accion === 'desactivar' ? 'Desactivar cuadrilla' : 'Activar cuadrilla'
          }
          textoConfirmar={confirmacion.accion === 'desactivar' ? 'Desactivar' : 'Activar'}
          procesando={cambiarEstado.isPending}
          onCancelar={() => setConfirmacion(null)}
          onConfirmar={confirmar}
        >
          {confirmacion.accion === 'desactivar' ? (
            <p>
              <strong>{confirmacion.cuadrilla.nombre}</strong> no se va a poder elegir para nuevos
              técnicos hasta que la actives de nuevo. Su historial se conserva.
            </p>
          ) : (
            <p>
              <strong>{confirmacion.cuadrilla.nombre}</strong> se va a poder elegir de nuevo para
              los técnicos.
            </p>
          )}
        </DialogoConfirmacion>
      )}
    </section>
  )
}
