import { fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw/http'
import { describe, expect, it } from 'vitest'
import { problema } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import { TIPOS_PROBLEMA } from '../../shared/api/problemDetail'
import { iniciarSesionComo, renderizarRuta } from '../../test/renderizarRuta'

describe('inicio de sesión (CU-12)', () => {
  it('login_credencialesCorrectas_entraYVuelveALaRutaPedida', async () => {
    const { router } = renderizarRuta('/estado')

    await iniciarSesionComo('jefatura@sigma.test')

    expect(await screen.findByRole('heading', { name: 'Estado del sistema' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/estado')
    expect(screen.getByRole('banner')).toHaveTextContent('Jefa de Prueba')
  })

  it('login_camposVacios_muestraLosErroresSinLlamarAlBackend', async () => {
    renderizarRuta('/iniciar-sesion')

    fireEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Ingresá tu correo.')).toBeInTheDocument()
    expect(screen.getByText('Ingresá tu contraseña.')).toBeInTheDocument()
  })

  it('login_contrasenaIncorrecta_muestraMensajeGenerico', async () => {
    renderizarRuta('/iniciar-sesion')

    await iniciarSesionComo('jefatura@sigma.test', 'otra-contrasena')

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos.')
  })

  it('login_cuentaDesactivada_indicaContactarALaJefatura', async () => {
    renderizarRuta('/iniciar-sesion')

    await iniciarSesionComo('desactivado@sigma.test')

    expect(await screen.findByRole('alert')).toHaveTextContent('Contactá a la Jefatura')
  })

  it('login_cuentaBloqueada_muestraHastaQueHoraEnMontevideo', async () => {
    servidor.use(
      http.post('/api/v1/sesion', () =>
        problema(423, TIPOS_PROBLEMA.usuarioBloqueado, 'Usuario bloqueado', 'Bloqueada', {
          bloqueadoHasta: '2026-10-27T18:30:00Z',
        }),
      ),
    )
    renderizarRuta('/iniciar-sesion')

    await iniciarSesionComo('bloqueado@sigma.test')

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('bloqueada')
    expect(alerta).toHaveTextContent('15:30')
  })

  it('sesionVencida_pedidoCon401_vuelveAlLoginYDespuesALaRuta', async () => {
    const { router } = renderizarRuta('/estado')
    await iniciarSesionComo('jefatura@sigma.test')
    expect(await screen.findByText(/Backend: ok/)).toBeInTheDocument()

    // El backend venció la sesión: el próximo pedido responde 401.
    servidor.use(
      http.get('/api/v1/ping', () =>
        problema(401, TIPOS_PROBLEMA.sinSesion, 'No autenticado', 'Iniciá sesión.'),
      ),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Tu sesión venció')
    expect(router.state.location.pathname).toBe('/iniciar-sesion')

    servidor.resetHandlers()
    await iniciarSesionComo('jefatura@sigma.test')

    expect(await screen.findByRole('heading', { name: 'Estado del sistema' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/estado')
  })

  it('logout_conSesion_vuelveAlLoginSinAvisoDeVencimiento', async () => {
    const { router } = renderizarRuta('/')
    await iniciarSesionComo('operador@sigma.test')
    expect(await screen.findByRole('heading', { name: /Hola/ })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/iniciar-sesion'))
    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
