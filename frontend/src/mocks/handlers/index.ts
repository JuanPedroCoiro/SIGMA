import { http, HttpResponse } from 'msw/http'
import { sinSesion, usuarioEnSesion } from '../problemas'
import { handlersCategorias } from './categorias'
import { handlersCuadrillas } from './cuadrillas'
import { handlersInstalaciones } from './instalaciones'
import { handlersOrdenes } from './ordenes'
import { handlersSesion } from './sesion'
import { handlersUsuarios } from './usuarios'
import { handlersIncidentes } from './incidentes'
import { handlersPendientes } from './pendientes'
import { handlersInicio } from './inicio'

const handlerPing = http.get('/api/v1/ping', () => {
  // El ping real es público; acá se exige sesión para poder probar la sesión vencida desde
  // «Estado del sistema».
  if (!usuarioEnSesion()) return sinSesion()
  return HttpResponse.json({
    estado: 'ok',
    baseDeDatos: 'API simulada (sin base de datos)',
    hora: new Date().toISOString(),
  })
})

export const handlers = [
  ...handlersSesion,
  ...handlersUsuarios,
  ...handlersCuadrillas,
  ...handlersInstalaciones,
  ...handlersOrdenes,
  ...handlersPendientes,
  ...handlersInicio,
  ...handlersIncidentes,
  ...handlersCategorias,
  handlerPing,
]
