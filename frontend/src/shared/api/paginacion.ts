/** Respuesta de los listados paginados (`?page&size&sort`); `pagina` empieza en 0. */
export type PageResponse<T> = {
  contenido: T[]
  pagina: number
  tamanio: number
  totalElementos: number
  totalPaginas: number
}

export type ParametrosPagina = {
  page: number
  size?: number
  sort?: string
}
