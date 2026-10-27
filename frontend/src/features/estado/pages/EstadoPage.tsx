import { usePing } from '../hooks/usePing'

export function EstadoPage() {
  const { data, isPending, isError, isFetching, refetch } = usePing()

  return (
    <section>
      <h1>Estado del sistema</h1>

      {isPending && <p>Consultando el backend...</p>}

      {data && (
        <ul>
          <li>Backend: {data.estado}</li>
          <li>Base de datos: {data.baseDeDatos}</li>
          <li>Hora del servidor: {new Date(data.hora).toLocaleString('es-UY')}</li>
        </ul>
      )}

      {isError && (
        <p style={{ color: 'var(--error)' }}>
          No se pudo conectar con el backend. ¿Está levantado en el puerto 8080?
        </p>
      )}

      <button type="button" onClick={() => refetch()} disabled={isFetching}>
        Reintentar
      </button>
    </section>
  )
}
