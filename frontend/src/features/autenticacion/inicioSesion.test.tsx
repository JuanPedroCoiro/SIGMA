import { notifyManager } from '@tanstack/react-query'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { isAxiosError } from 'axios'
import { http, HttpResponse } from 'msw/http'
import { describe, expect, it } from 'vitest'
import { base, guardarBase } from '../../mocks/datos'
import { problema } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import { cliente } from '../../shared/api/cliente'
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

  it('logout_avisosDeReactQueryDemorados_vaDirectoAlLoginSinPasarPorElInicio', async () => {
    const { router } = renderizarRuta('/')
    await iniciarSesionComo('operador@sigma.test')
    expect(await screen.findByRole('list', { name: 'Resumen' })).toBeInTheDocument()
    const rutas: string[] = []
    const desuscribir = router.subscribe((estado) => rutas.push(estado.location.pathname))
    // Como en una máquina lenta: la navegación al login se renderiza antes de que React Query
    // avise que la sesión pasó a null (#66).
    notifyManager.setScheduler((avisar) => setTimeout(avisar, 100))

    try {
      fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
      await waitFor(() => expect(screen.getByRole('button', { name: 'Ingresar' })).toBeVisible())
    } finally {
      notifyManager.setScheduler((avisar) => setTimeout(avisar, 0))
      desuscribir()
    }

    expect(rutas).not.toContain('/')
    expect(router.state.location.pathname).toBe('/iniciar-sesion')
  })

  it('logout_pedidoQueRespondeDuranteElLogout_noAvisaVencimiento', async () => {
    // El orden que antes mostraba «Tu sesión venció» al salir (#66): el resumen del inicio sale
    // antes del logout y su 401 llega mientras el logout sigue en curso.
    let soltarResumen!: () => void
    const resumenRetenido = new Promise<void>((resolver) => (soltarResumen = resolver))
    // Se registra después del interceptor de la app, así que corre cuando la app ya vio el 401.
    let avisarRespuesta!: () => void
    const resumenRespondido = new Promise<void>((resolver) => (avisarRespuesta = resolver))
    const interceptor = cliente.interceptors.response.use(undefined, (error: unknown) => {
      if (isAxiosError(error) && error.config?.url === '/inicio/resumen') avisarRespuesta()
      return Promise.reject(error)
    })
    servidor.use(
      // Sin respuesta propia: al soltarlo sigue al handler de siempre, que con la sesión ya
      // cerrada responde 401.
      http.get('/api/v1/inicio/resumen', async () => {
        await resumenRetenido
      }),
      // El backend cierra la sesión, pero su respuesta espera a que llegue el 401 del resumen.
      http.delete('/api/v1/sesion', async () => {
        base.idSesion = null
        guardarBase()
        soltarResumen()
        await resumenRespondido
        return new HttpResponse(null, { status: 204 })
      }),
    )

    try {
      const { router } = renderizarRuta('/')
      await iniciarSesionComo('operador@sigma.test')
      expect(await screen.findByRole('heading', { name: /Hola/ })).toBeInTheDocument()
      fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

      await waitFor(() => expect(router.state.location.pathname).toBe('/iniciar-sesion'))
      expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
    } finally {
      cliente.interceptors.response.eject(interceptor)
    }
  })
})
