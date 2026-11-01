import { Link } from 'react-router-dom'

export function NoEncontradaPage() {
  return (
    <main style={{ padding: '2rem' }}>
      <h1>Página no encontrada</h1>
      <p>
        La dirección no existe. <Link to="/">Volver al inicio</Link>
      </p>
    </main>
  )
}
