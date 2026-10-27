import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw/http'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { problema } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import { TIPOS_PROBLEMA } from '../../shared/api/problemDetail'
import { renderizarRuta } from '../../test/renderizarRuta'

// Usuarios de la API simulada (src/mocks/datos.ts).
const ID_JEFATURA = 1
const ID_TECNICO = 2
const ID_OPERADOR = 3
const ID_INACTIVO = 6

function escribir(etiqueta: string, valor: string) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } })
}

async function completarNuevoUsuario(datos: {
  nombre?: string
  correo?: string
  rol?: string
  password?: string
}) {
  await screen.findByRole('heading', { name: 'Nuevo usuario' })
  if (datos.nombre !== undefined) escribir('Nombre', datos.nombre)
  if (datos.correo !== undefined) escribir('Correo (usuario de acceso)', datos.correo)
  if (datos.rol !== undefined) escribir('Rol', datos.rol)
  if (datos.password !== undefined) escribir('Contraseña inicial', datos.password)
}

describe('usuarios (CU-13)', () => {
  beforeEach(() => {
    base.idSesion = ID_JEFATURA
  })

  // --- c. Listado y detalle ---

  it('listado_variasPaginas_muestraVeinteYPasaALaSiguiente', async () => {
    const { router } = renderizarRuta('/usuarios')

    expect(await screen.findByText('Página 1 de 2 (32 en total)')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(21) // + encabezado

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(await screen.findByText('Página 2 de 2 (32 en total)')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(13)
    expect(router.state.location.search).toBe('?pagina=2')
  })

  it('detalle_tecnico_muestraCorreoRolCuadrillaYEstado', async () => {
    renderizarRuta(`/usuarios/${ID_TECNICO}`)

    expect(await screen.findByRole('heading', { name: 'Técnico de Prueba' })).toBeInTheDocument()
    expect(screen.getByText('tecnico@sigma.test')).toBeInTheDocument()
    expect(screen.getByText('Técnico')).toBeInTheDocument()
    expect(await screen.findByText('Cuadrilla Norte')).toBeInTheDocument()
    expect(screen.getByText('Activo')).toBeInTheDocument()
  })

  // --- a. Alta ---

  it('nuevo_camposVacios_muestraLosErroresDeCadaCampo', async () => {
    renderizarRuta('/usuarios/nuevo')
    await completarNuevoUsuario({})

    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    expect(await screen.findByText('Ingresá el nombre.')).toBeInTheDocument()
    expect(screen.getByText('Ingresá el correo.')).toBeInTheDocument()
    expect(screen.getByText('Elegí un rol.')).toBeInTheDocument()
    expect(screen.getByText(/al menos 10 caracteres/)).toBeInTheDocument()
  })

  it('nuevo_tecnicoSinCuadrilla_pideLaCuadrilla', async () => {
    renderizarRuta('/usuarios/nuevo')
    await completarNuevoUsuario({
      nombre: 'Nuevo Técnico',
      correo: 'nuevo.tecnico@sigma.test',
      rol: 'TECNICO',
      password: 'Clave.Segura.1',
    })

    expect(await screen.findByLabelText('Cuadrilla')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    expect(await screen.findByText('Elegí la cuadrilla del técnico.')).toBeInTheDocument()
  })

  it('nuevo_correoDuplicado_muestraElErrorJuntoAlCorreo', async () => {
    renderizarRuta('/usuarios/nuevo')
    await completarNuevoUsuario({
      nombre: 'Otra Jefa',
      correo: 'jefatura@sigma.test',
      rol: 'JEFATURA',
      password: 'Clave.Segura.1',
    })

    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    const correo = screen.getByLabelText('Correo (usuario de acceso)')
    await waitFor(() => expect(correo).toHaveAttribute('aria-invalid', 'true'))
    expect(correo).toHaveAccessibleDescription('Ya existe un usuario con ese correo.')
  })

  it('nuevo_validacionDelBackend_muestraCadaErrorJuntoASuCampo', async () => {
    servidor.use(
      http.post('/api/v1/usuarios', () =>
        problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Revisá los campos.', {
          errores: [{ campo: 'nombre', mensaje: 'El nombre tiene caracteres no permitidos.' }],
        }),
      ),
    )
    renderizarRuta('/usuarios/nuevo')
    await completarNuevoUsuario({
      nombre: 'Nombre Raro',
      correo: 'raro@sigma.test',
      rol: 'OPERADOR',
      password: 'Clave.Segura.1',
    })

    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    expect(await screen.findByText('El nombre tiene caracteres no permitidos.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('nuevo_datosValidos_creaYMuestraElDetalle', async () => {
    const { router } = renderizarRuta('/usuarios/nuevo')
    await completarNuevoUsuario({
      nombre: 'Nueva Operadora',
      correo: 'Nueva.Operadora@sigma.test',
      rol: 'OPERADOR',
      password: 'Clave.Segura.1',
    })

    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    expect(await screen.findByRole('heading', { name: 'Nueva Operadora' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Usuario creado.')
    expect(screen.getByText('nueva.operadora@sigma.test')).toBeInTheDocument()
    expect(router.state.location.pathname).toMatch(/^\/usuarios\/\d+$/)
  })

  // --- b. Edición y restablecer contraseña ---

  it('editar_usuario_noPideContrasenaYGuardaLosCambios', async () => {
    renderizarRuta(`/usuarios/${ID_OPERADOR}/editar`)

    const nombre = await screen.findByLabelText('Nombre')
    expect(nombre).toHaveValue('Operador de Prueba')
    expect(screen.queryByLabelText(/Contraseña/)).not.toBeInTheDocument()

    fireEvent.change(nombre, { target: { value: 'Operador Renombrado' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByRole('heading', { name: 'Operador Renombrado' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Cambios guardados.')
  })

  it('restablecer_contrasenaNueva_laGuardaYAvisa', async () => {
    renderizarRuta(`/usuarios/${ID_OPERADOR}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Restablecer contraseña' }))
    const dialogo = screen.getByRole('dialog', { name: 'Restablecer contraseña' })
    fireEvent.change(within(dialogo).getByLabelText('Contraseña nueva'), {
      target: { value: 'Nueva.Clave.2026' },
    })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Restablecer' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Contraseña restablecida')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(base.usuarios.find((u) => u.id === ID_OPERADOR)?.password).toBe('Nueva.Clave.2026')
  })

  it('restablecer_contrasenaCorta_noLlamaAlBackend', async () => {
    renderizarRuta(`/usuarios/${ID_OPERADOR}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Restablecer contraseña' }))
    const dialogo = screen.getByRole('dialog')
    fireEvent.change(within(dialogo).getByLabelText('Contraseña nueva'), {
      target: { value: 'corta' },
    })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Restablecer' }))

    expect(await within(dialogo).findByText(/al menos 10 caracteres/)).toBeInTheDocument()
    expect(base.usuarios.find((u) => u.id === ID_OPERADOR)?.password).toBe('Prueba.1234')
  })

  // --- d. Desactivar y activar ---

  it('desactivar_cancelar_noCambiaNada', async () => {
    renderizarRuta(`/usuarios/${ID_OPERADOR}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Desactivar' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('Activo')).toBeInTheDocument()
    expect(base.usuarios.find((u) => u.id === ID_OPERADOR)?.activo).toBe(true)
  })

  it('desactivar_confirmar_quedaInactivoYOfreceActivar', async () => {
    renderizarRuta(`/usuarios/${ID_OPERADOR}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Desactivar' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desactivar' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Usuario desactivado.')
    expect(await screen.findByText('Inactivo')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Activar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Desactivar' })).not.toBeInTheDocument()
  })

  it('detalle_propioUsuario_noOfreceDesactivar', async () => {
    renderizarRuta(`/usuarios/${ID_JEFATURA}`)

    expect(await screen.findByRole('heading', { name: 'Jefa de Prueba' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Restablecer contraseña' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Desactivar' })).not.toBeInTheDocument()
  })

  it('activar_usuarioInactivo_quedaActivo', async () => {
    renderizarRuta(`/usuarios/${ID_INACTIVO}`)

    fireEvent.click(await screen.findByRole('button', { name: 'Activar' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Usuario activado.')
    expect(await screen.findByText('Activo')).toBeInTheDocument()
    expect(base.usuarios.find((u) => u.id === ID_INACTIVO)?.activo).toBe(true)
  })
})
