import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { rutas } from '../app/router'
import { SesionContext, type Sesion } from '../app/sesion/sesion'

/** Renderiza la aplicación en la ruta indicada, con la sesión que pida el test. */
export function renderizarRuta(ruta: string, sesion: Sesion) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(rutas, { initialEntries: [ruta] })

  return render(
    <QueryClientProvider client={queryClient}>
      <SesionContext.Provider value={sesion}>
        <RouterProvider router={router} />
      </SesionContext.Provider>
    </QueryClientProvider>,
  )
}
