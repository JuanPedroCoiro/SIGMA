// Reflejan los DTOs del backend (#31); se escriben a mano hasta tener el OpenAPI.

export type Instalacion = {
  id: number
  codigo: string
  nombre: string
  rutaZona: string
  latitud: number
  longitud: number
}
