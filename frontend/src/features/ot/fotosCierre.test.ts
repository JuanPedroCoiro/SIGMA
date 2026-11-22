import { describe, expect, it } from 'vitest'
import { comprimirFoto, medidasReducidas } from './fotosCierre'

describe('compresión de las fotos del cierre (#90)', () => {
  it('medidasReducidas_reduceElLadoMayorA1600SinDeformar', () => {
    expect(medidasReducidas(4000, 3000)).toEqual({ ancho: 1600, alto: 1200 })
    expect(medidasReducidas(3000, 4000)).toEqual({ ancho: 1200, alto: 1600 })
  })

  it('medidasReducidas_noAgrandaLasChicas', () => {
    expect(medidasReducidas(800, 600)).toEqual({ ancho: 800, alto: 600 })
  })

  it('comprimirFoto_sinSoporteDelNavegador_devuelveLaOriginal', async () => {
    // jsdom no tiene createImageBitmap ni canvas: se envía la original.
    const original = new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0])], 'a.jpg', {
      type: 'image/jpeg',
    })

    expect(await comprimirFoto(original)).toBe(original)
  })
})
