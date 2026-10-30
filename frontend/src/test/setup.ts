import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { reiniciarBaseSimulada } from '../mocks/datos'
import { servidor } from '../mocks/servidor'

// Los tests usan la misma API simulada que `npm run dev:simulado`. Un pedido sin handler falla.
beforeAll(() => servidor.listen({ onUnhandledFrame: 'error' }))

afterEach(() => {
  cleanup()
  servidor.resetHandlers()
  reiniciarBaseSimulada()
})

afterAll(() => servidor.close())
