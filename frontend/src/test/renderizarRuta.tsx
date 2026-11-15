import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { rutas } from '../app/router'
import { SesionContext, type Sesion } from '../app/sesion/sesion'
import { SesionProvider } from '../app/sesion/SesionProvider'
import { CONTRASENA_SIMULADA } from '../mocks/datos'

/** Los QueryClient del test en curso. `descartarQueryClients` los vacía entre tests. */
const queryClients: QueryClient[] = []

/**
 * Renderiza la aplicación en la ruta indicada. Sin `sesion`, usa la sesión real contra la API
 * simulada (MSW); con `sesion`, la inyecta tal cual, sin pedidos.
 */
export function renderizarRuta(ruta: string, sesion?: Sesion) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  queryClients.push(queryClient)
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

/** Plazo para que terminen los pedidos de un test ya desmontado. Es un tope, no una espera fija. */
const PLAZO_PEDIDOS_MS = 5000

/**
 * Espera a que terminen las consultas y mutaciones en curso y vacía los QueryClient. Se llama
 * después de desmontar y antes de reiniciar la base simulada: si no, un pedido de un test
 * terminado escribe en la base del siguiente o lee de ella (#66). Los pedidos no se cancelan
 * al desmontar, porque las APIs no pasan `signal` a axios.
 */
export async function descartarQueryClients() {
  const clientes = queryClients.splice(0)
  const enCurso = () => clientes.reduce((n, c) => n + c.isFetching() + c.isMutating(), 0)
  try {
    await waitFor(
      () => {
        if (enCurso() > 0) throw new Error()
      },
      { timeout: PLAZO_PEDIDOS_MS },
    )
  } catch {
    throw new Error(
      `Quedaron ${enCurso()} pedidos en curso ${PLAZO_PEDIDOS_MS} ms después de terminar el ` +
        'test. Se cortó acá para que no se mezclen con los datos del test siguiente (#66).',
    )
  } finally {
    clientes.forEach((c) => c.clear())
  }
}

/** Completa y envía el formulario de inicio de sesión, que ya tiene que estar en pantalla. */
export async function iniciarSesionComo(correo: string, contrasena = CONTRASENA_SIMULADA) {
  fireEvent.change(await screen.findByLabelText('Correo'), { target: { value: correo } })
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: contrasena } })
  fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}
