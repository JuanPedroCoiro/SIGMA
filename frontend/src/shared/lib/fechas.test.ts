import { describe, expect, it } from 'vitest'
import { formatearFechaHoraMontevideo, formatearHoraMontevideo } from './fechas'

describe('formatearFechaHoraMontevideo', () => {
  it('formatear_siempreAgregaLaFecha', () => {
    expect(formatearFechaHoraMontevideo('2026-11-04T00:41:00Z')).toBe('21:41 del 3/11')
  })

  it('formatear_pasadaLaMedianocheUtc_usaElDiaDeMontevideo', () => {
    // 01:00 UTC del 28 todavía es el 27 a las 22:00 en Montevideo.
    expect(formatearFechaHoraMontevideo('2026-10-28T01:00:00Z')).toBe('22:00 del 27/10')
  })
})

describe('formatearHoraMontevideo', () => {
  const ahora = new Date('2026-10-27T15:00:00Z')

  it('formatear_mismoDia_devuelveSoloLaHoraLocal', () => {
    // 18:30 UTC son las 15:30 en Montevideo (UTC-3).
    expect(formatearHoraMontevideo('2026-10-27T18:30:00Z', ahora)).toBe('15:30')
  })

  it('formatear_otroDia_agregaLaFecha', () => {
    expect(formatearHoraMontevideo('2026-10-28T18:30:00Z', ahora)).toBe('15:30 del 28/10')
  })

  it('formatear_pasadaLaMedianocheUtc_usaElDiaDeMontevideo', () => {
    // 01:00 UTC del 28 todavía es el 27 a las 22:00 en Montevideo.
    expect(formatearHoraMontevideo('2026-10-28T01:00:00Z', ahora)).toBe('22:00')
  })
})
