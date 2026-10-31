import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { renderizarRuta } from '../../test/renderizarRuta'

// Usuarios e instalaciones de la API simulada (src/mocks/datos.ts).
const ID_JEFATURA = 1
const ID_OPERADOR = 3

function filas() {
  return within(screen.getByRole('table')).getAllByRole('row')
}

/** Abre el diálogo de importación y sube una planilla con este contenido. */
async function importarPlanilla(contenido: string) {
  fireEvent.click(await screen.findByRole('button', { name: 'Importar planilla' }))
  const dialogo = screen.getByRole('dialog', { name: 'Importar planilla' })
  const archivo = new File([contenido], 'instalaciones.csv', { type: 'text/csv' })
  fireEvent.change(within(dialogo).getByLabelText('Planilla (.csv)'), {
    target: { files: [archivo] },
  })
  fireEvent.click(within(dialogo).getByRole('button', { name: 'Importar' }))
  return dialogo
}

const ENCABEZADO = 'codigo;nombre;ruta_zona;latitud;longitud'

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

  it('listado_operador_veLaListaPeroNoPuedeImportar', async () => {
    base.idSesion = ID_OPERADOR
    renderizarRuta('/instalaciones')

    expect(await screen.findByText('INST-001')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Importar planilla' })).not.toBeInTheDocument()
  })

  it('importar_planillaValida_informaLaCantidadYRefrescaLaLista', async () => {
    renderizarRuta('/instalaciones')
    expect(await screen.findByText('Página 1 de 2 (25 en total)')).toBeInTheDocument()

    const dialogo = await importarPlanilla(
      [
        ENCABEZADO,
        'INST-101;Cruce Nuevo 1;Ruta 9;-34,5;-55,9',
        'INST-102;Cruce Nuevo 2;Ruta 9;-34,6;-55,8',
      ].join('\n'),
    )

    expect(await within(dialogo).findByRole('status')).toHaveTextContent(
      'Se importaron 2 instalaciones.',
    )
    expect(await screen.findByText('Página 1 de 2 (27 en total)')).toBeInTheDocument()

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Cerrar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('importar_planillaConErrores_muestraCadaErrorPorFilaYNoImporta', async () => {
    renderizarRuta('/instalaciones')

    const dialogo = await importarPlanilla(
      [
        ENCABEZADO,
        'INST-101;;Ruta 9;-34,5;-55,9',
        'INST-001;Cruce Repetido;Ruta 9;-34,6;-55,8',
      ].join('\n'),
    )

    const alerta = await within(dialogo).findByRole('alert')
    expect(alerta).toHaveTextContent('La planilla tiene 2 errores')
    const errores = within(alerta).getAllByRole('row').slice(1)
    expect(errores).toHaveLength(2)
    expect(errores[0]).toHaveTextContent('2nombreFalta el valor de nombre.')
    expect(errores[1]).toHaveTextContent('3codigoYa existe una instalación con el código INST-001.')
    expect(base.instalaciones).toHaveLength(25)
  })
})
