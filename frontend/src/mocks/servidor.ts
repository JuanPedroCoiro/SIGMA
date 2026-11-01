import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** La misma API simulada que en el navegador, para los tests. */
export const servidor = setupServer(...handlers)
