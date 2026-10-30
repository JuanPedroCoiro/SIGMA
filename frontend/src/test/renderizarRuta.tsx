import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { rutas } from '../app/router'
import { SesionContext, type Sesion } from '../app/sesion/sesion'
import { SesionProvider } from '../app/sesion/SesionProvider'
import { CONTRASENA_SIMULADA } from '../mocks/datos'

/**
 * Renderiza la aplicación en la ruta indicada. Sin `sesion`, usa la sesión real contra la API
 * simulada (MSW); con `sesion`, la inyecta tal cual, sin pedidos.
 */
export function renderizarRuta(ruta: string, sesion?: Sesion) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(rutas, { initialEntries: [ruta] })
  const app = <RouterProvider router={router} />

  render(
    <QueryClientProvider client={queryClient}>
      {sesion ? (
        <SesionContext.Provider value={sesion}>{app}</SesionContext.Provider>
      ) : (
        <SesionProvider>{app}</SesionProvider>
      )}
    </QueryClientProvider>,
  )
  return { router, queryClient }
}

/** Completa y envía el formulario de inicio de sesión, que ya tiene que estar en pantalla. */
export async function iniciarSesionComo(correo: string, contrasena = CONTRASENA_SIMULADA) {
  fireEvent.change(await screen.findByLabelText('Correo'), { target: { value: correo } })
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: contrasena } })
  fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}
