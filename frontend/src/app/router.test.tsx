import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderizarRuta } from '../test/renderizarRuta'
import type { Sesion } from './sesion/sesion'

function sesionDe(rol: 'JEFATURA' | 'TECNICO'): Sesion {
  return {
    estado: 'autenticado',
    usuario: { id: 1, nombre: 'Ana Pérez', correo: 'ana@sigma.test', rol, idCuadrilla: null },
  }
}

describe('router', () => {
  it('rutaProtegida_sesionAnonima_redirigeAlInicioDeSesion', async () => {
    renderizarRuta('/', { estado: 'anonimo' })

    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('rutaProtegida_sesionAutenticada_muestraLayoutYContenido', () => {
    renderizarRuta('/', sesionDe('JEFATURA'))

    expect(screen.getByRole('banner')).toHaveTextContent('Ana Pérez')
    expect(screen.getByRole('navigation', { name: 'Menú principal' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveClass('active')
    expect(screen.getByRole('heading', { name: 'Hola, Ana Pérez' })).toBeInTheDocument()
  })

  it('menu_jefatura_veUsuariosYEstado', () => {
    renderizarRuta('/', sesionDe('JEFATURA'))

    expect(screen.getByRole('link', { name: 'Usuarios' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Estado del sistema' })).toBeInTheDocument()
  })

  it('menu_tecnico_soloVeInicio', () => {
    renderizarRuta('/', sesionDe('TECNICO'))

    expect(screen.getByRole('link', { name: 'Inicio' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Usuarios' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Estado del sistema' })).not.toBeInTheDocument()
  })

  it('rutaDeJefatura_tecnico_loRedirigeAlInicio', () => {
    const { router } = renderizarRuta('/usuarios', sesionDe('TECNICO'))

    expect(router.state.location.pathname).toBe('/')
    expect(screen.getByRole('heading', { name: 'Hola, Ana Pérez' })).toBeInTheDocument()
  })

  it('rutaInexistente_cualquierSesion_muestraNoEncontrada', () => {
    renderizarRuta('/no-existe', sesionDe('JEFATURA'))

    expect(screen.getByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument()
  })
})
