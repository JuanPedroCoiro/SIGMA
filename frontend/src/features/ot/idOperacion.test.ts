import { afterEach, describe, expect, it, vi } from 'vitest'
import { generarIdOperacion } from './idOperacion'

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

describe('identificador de operación del cierre (HU-07)', () => {
  afterEach(() => vi.restoreAllMocks())

  it('conRandomUUID_loUsa', () => {
    const espia = vi.spyOn(crypto, 'randomUUID')

    expect(generarIdOperacion()).toMatch(UUID_V4)
    expect(espia).toHaveBeenCalledOnce()
  })

  it('sinRandomUUID_armaUnV4ConGetRandomValues', () => {
    // Como en http por una IP de la red: el navegador no expone randomUUID. La propiedad propia
    // tapa la del prototipo y al borrarla vuelve la original.
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true })
    try {
      const id = generarIdOperacion()
      expect(id).toMatch(UUID_V4)
      expect(generarIdOperacion()).not.toBe(id)
    } finally {
      delete (crypto as { randomUUID?: unknown }).randomUUID
    }
  })
})
