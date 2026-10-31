// Reflejan los DTOs del backend (#31); se escriben a mano hasta tener el OpenAPI.

export type Instalacion = {
  id: number
  codigo: string
  nombre: string
  rutaZona: string
  latitud: number
  longitud: number
}

/** Error de una fila de la planilla; `fila` es la línea del archivo, contando el encabezado. */
export type ErrorFila = {
  fila: number
  campo: string
  mensaje: string
}

/** POST /api/v1/instalaciones/importar: 200 solo si no hubo errores (y entonces se importó todo). */
export type ResultadoImportacion = {
  importadas: number
  errores: ErrorFila[]
}
