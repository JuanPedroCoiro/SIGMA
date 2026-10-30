import { http, HttpResponse } from 'msw/http'
import { sinSesion, usuarioEnSesion } from '../problemas'
import { handlersSesion } from './sesion'
import { handlersUsuarios } from './usuarios'

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

export const handlers = [...handlersSesion, ...handlersUsuarios, handlerPing]
