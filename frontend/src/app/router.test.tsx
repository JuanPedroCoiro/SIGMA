import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderizarRuta } from '../test/renderizarRuta'
import type { Sesion } from './sesion/sesion'

vi.mock('../features/estado/api/estadoApi', () => ({
  consultarPing: vi.fn().mockResolvedValue({
    estado: 'UP',
    baseDeDatos: 'UP',
    hora: '2026-10-27T12:00:00Z',
  }),
}))

const autenticado: Sesion = {
  estado: 'autenticado',
  usuario: { nombre: 'Ana Pérez', rol: 'JEFATURA' },
}

describe('router', () => {
  it('rutaProtegida_sesionAnonima_redirigeAlInicioDeSesion', async () => {
    renderizarRuta('/', { estado: 'anonimo' })

    expect(await screen.findByRole('heading', { name: 'Inicio de sesión' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('rutaProtegida_sesionAutenticada_muestraLayoutYContenido', async () => {
    renderizarRuta('/', autenticado)

    expect(screen.getByRole('banner')).toHaveTextContent('Ana Pérez')
    expect(screen.getByRole('navigation', { name: 'Menú principal' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Estado del sistema' })).toHaveClass('active')
    expect(await screen.findByText('Backend: UP')).toBeInTheDocument()
  })

  it('rutaInexistente_cualquierSesion_muestraNoEncontrada', () => {
    renderizarRuta('/no-existe', autenticado)

    expect(screen.getByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument()
  })
})
