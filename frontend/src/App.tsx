import { useEffect, useState } from 'react'
import { cliente } from './shared/api/cliente'

type PingResponse = {
  estado: string
  baseDeDatos: string
  hora: string
}

type Resultado =
  { tipo: 'cargando' } | { tipo: 'ok'; datos: PingResponse } | { tipo: 'error'; mensaje: string }

function consultarPing(): Promise<Resultado> {
  return cliente
    .get<PingResponse>('/ping')
    .then((res): Resultado => ({ tipo: 'ok', datos: res.data }))
    .catch((): Resultado => ({
      tipo: 'error',
      mensaje: 'No se pudo conectar con el backend. ¿Está levantado en el puerto 8080?',
    }))
}

function App() {
  const [resultado, setResultado] = useState<Resultado>({ tipo: 'cargando' })

  useEffect(() => {
    consultarPing().then(setResultado)
  }, [])

  function reintentar() {
    setResultado({ tipo: 'cargando' })
    consultarPing().then(setResultado)
  }

  return (
    <main style={{ padding: '2rem', fontFamily: 'system-ui, sans-serif' }}>
      <h1>SIGMA: prueba de conexión</h1>

      {resultado.tipo === 'cargando' && <p>Consultando el backend...</p>}

      {resultado.tipo === 'ok' && (
        <ul>
          <li>Backend: {resultado.datos.estado}</li>
          <li>Base de datos: {resultado.datos.baseDeDatos}</li>
          <li>Hora del servidor: {new Date(resultado.datos.hora).toLocaleString('es-UY')}</li>
        </ul>
      )}

      {resultado.tipo === 'error' && <p style={{ color: 'crimson' }}>{resultado.mensaje}</p>}

      <button type="button" onClick={reintentar}>
        Reintentar
      </button>
    </main>
  )
}

export default App
