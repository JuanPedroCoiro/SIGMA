import { fireEvent, render, screen, within } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { FotoParaCierre } from '../fotosCierre'
import { FotosCierre } from './FotosCierre'

function Prueba() {
  const [fotos, setFotos] = useState<FotoParaCierre[]>([])
  return <FotosCierre fotos={fotos} onCambio={setFotos} />
}

function jpg(nombre: string) {
  return new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2])], nombre, {
    type: 'image/jpeg',
  })
}

function elegir(...archivos: File[]) {
  fireEvent.change(screen.getByLabelText('Sacar o elegir fotos'), {
    target: { files: archivos },
  })
}

describe('fotos del cierre técnico (#90)', () => {
  it('eligeDosComoDespues_lasMuestraConEseMomento', async () => {
    render(<Prueba />)
    fireEvent.change(screen.getByLabelText('Momento de las próximas fotos'), {
      target: { value: 'DESPUES' },
    })

    elegir(jpg('poste.jpg'), jpg('lampara.jpg'))

    const lista = await screen.findByRole('list', { name: 'Fotos del cierre' })
    expect(within(lista).getAllByRole('img')).toHaveLength(2)
    expect(screen.getByLabelText('Momento de poste.jpg')).toHaveValue('DESPUES')
  })

  it('cambiaElMomentoYQuitaUna', async () => {
    render(<Prueba />)
    elegir(jpg('poste.jpg'), jpg('lampara.jpg'))
    const momento = await screen.findByLabelText('Momento de poste.jpg')

    fireEvent.change(momento, { target: { value: 'DURANTE' } })
    fireEvent.click(screen.getByRole('button', { name: 'Quitar lampara.jpg' }))

    expect(screen.getByLabelText('Momento de poste.jpg')).toHaveValue('DURANTE')
    expect(screen.queryByRole('img', { name: 'lampara.jpg' })).not.toBeInTheDocument()
  })

  it('masDeSeis_dejaAfueraLasQueSobranYAvisa', async () => {
    render(<Prueba />)

    elegir(...Array.from({ length: 7 }, (_, i) => jpg(`foto${i}.jpg`)))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Se pueden adjuntar hasta 6 fotos: quedó afuera 1.',
    )
    expect(screen.getAllByRole('img')).toHaveLength(6)
    expect(screen.getByLabelText('Sacar o elegir fotos')).toBeDisabled()
  })

  it('unArchivoQueNoEsImagen_loRechaza', async () => {
    render(<Prueba />)

    elegir(new File(['hola'], 'nota.txt', { type: 'text/plain' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '«nota.txt» no es una imagen JPG, PNG ni WEBP.',
    )
  })
})
