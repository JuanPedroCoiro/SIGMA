import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import './index.css'
import { Providers } from './app/Providers'
import { router } from './app/router'

// API simulada solo en `npm run dev:simulado`. Con import.meta.env.DEV en false el build descarta
// esta rama, y con ella MSW y los datos ficticios.
async function prepararApi() {
  if (import.meta.env.DEV && import.meta.env.VITE_API_SIMULADA === 'true') {
    const { activarApiSimulada } = await import('./mocks/activar')
    await activarApiSimulada()
  }
}

prepararApi().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Providers>
        <RouterProvider router={router} />
      </Providers>
    </StrictMode>,
  )
})
