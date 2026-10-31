import { Paginacion } from '../../../shared/components/Paginacion'
import { usePaginado } from '../../../shared/hooks/usePaginado'
import { formatearCoordenada } from '../coordenadas'
import { useInstalaciones } from '../hooks/useInstalaciones'
import './InstalacionesPage.css'

/** HU-02: listado paginado de instalaciones, de solo lectura. */
export function InstalacionesPage() {
  const { pagina, irAPagina } = usePaginado()
  const instalaciones = useInstalaciones(pagina)

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Instalaciones</h1>
      </div>

      {instalaciones.isPending && <p>Cargando instalaciones...</p>}
      {instalaciones.isError && (
        <p className="alerta alerta-error" role="alert">
          No se pudo cargar el listado de instalaciones. Volvé a intentar en unos minutos.
        </p>
      )}

      {instalaciones.data?.totalElementos === 0 && (
        <p>
          Todavía no hay instalaciones cargadas. La Jefatura las importa desde la planilla de
          instalaciones.
        </p>
      )}

      {instalaciones.data && instalaciones.data.totalElementos > 0 && (
        <>
          <table className="tabla-instalaciones">
            <thead>
              <tr>
                <th scope="col">Código</th>
                <th scope="col">Nombre</th>
                <th scope="col">Ruta o zona</th>
                <th scope="col">Latitud</th>
                <th scope="col">Longitud</th>
              </tr>
            </thead>
            <tbody>
              {instalaciones.data.contenido.map((instalacion) => (
                <tr key={instalacion.id}>
                  <td data-etiqueta="Código">{instalacion.codigo}</td>
                  <td data-etiqueta="Nombre">{instalacion.nombre}</td>
                  <td data-etiqueta="Ruta o zona">{instalacion.rutaZona}</td>
                  <td data-etiqueta="Latitud" className="numero">
                    {formatearCoordenada(instalacion.latitud)}
                  </td>
                  <td data-etiqueta="Longitud" className="numero">
                    {formatearCoordenada(instalacion.longitud)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Paginacion
            pagina={instalaciones.data.pagina}
            totalPaginas={instalaciones.data.totalPaginas}
            totalElementos={instalaciones.data.totalElementos}
            onCambiar={irAPagina}
          />
        </>
      )}
    </section>
  )
}
