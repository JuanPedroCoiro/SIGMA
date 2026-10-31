import { fireEvent, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { renderizarRuta } from '../../test/renderizarRuta'

// Usuarios e instalaciones de la API simulada (src/mocks/datos.ts).
const ID_JEFATURA = 1
const ID_OPERADOR = 3

function filas() {
  return within(screen.getByRole('table')).getAllByRole('row')
}

describe('instalaciones (HU-02)', () => {
  beforeEach(() => {
    base.idSesion = ID_JEFATURA
  })

  it('listado_variasPaginas_muestraLasFilasYPasaALaSiguiente', async () => {
    const { router } = renderizarRuta('/instalaciones')

    expect(await screen.findByText('Página 1 de 2 (25 en total)')).toBeInTheDocument()
    expect(filas()).toHaveLength(21) // + encabezado
    const primera = filas()[1]
    expect(within(primera).getByText('INST-001')).toBeInTheDocument()
    expect(within(primera).getByText('Cruce Ficticio 1')).toBeInTheDocument()
    expect(within(primera).getByText('Ruta 1')).toBeInTheDocument()
    expect(within(primera).getByText('-34,900000')).toBeInTheDocument()
    expect(within(primera).getByText('-56,200000')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(await screen.findByText('Página 2 de 2 (25 en total)')).toBeInTheDocument()
    expect(filas()).toHaveLength(6)
    expect(screen.getByText('INST-021')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?pagina=2')
  })

  it('listado_sinInstalaciones_muestraElMensajeDeListaVacia', async () => {
    base.instalaciones = []
    renderizarRuta('/instalaciones')

    expect(
      await screen.findByText(
        'Todavía no hay instalaciones cargadas. La Jefatura las importa desde la planilla de instalaciones.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('listado_operador_veLaLista', async () => {
    base.idSesion = ID_OPERADOR
    renderizarRuta('/instalaciones')

    expect(await screen.findByText('INST-001')).toBeInTheDocument()
  })
})
