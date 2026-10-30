import { http, HttpResponse } from 'msw/http'
import { TIPOS_PROBLEMA } from '../../shared/api/problemDetail'
import { base, guardarBase } from '../datos'
import { problema, sinSesion, usuarioEnSesion } from '../problemas'

const BLOQUEO_MINUTOS = 15

// /api/v1/sesion, como en la rama del #19 más lo acordado (423, cuenta desactivada, idCuadrilla).
export const handlersSesion = [
  http.post('/api/v1/sesion', async ({ request }) => {
    const datos = new URLSearchParams(await request.text())
    const correo = (datos.get('correo') ?? '').trim().toLowerCase()
    const usuario = base.usuarios.find((u) => u.correo === correo)

    if (usuario?.bloqueado) {
      const bloqueadoHasta = new Date(Date.now() + BLOQUEO_MINUTOS * 60_000).toISOString()
      return problema(
        423,
        TIPOS_PROBLEMA.usuarioBloqueado,
        'Usuario bloqueado',
        'La cuenta está bloqueada por intentos fallidos.',
        { bloqueadoHasta },
      )
    }
    if (!usuario || usuario.password !== datos.get('contrasena')) {
      return problema(
        401,
        TIPOS_PROBLEMA.credenciales,
        'Credenciales incorrectas',
        'Correo o contraseña incorrectos.',
      )
    }
    // Solo con la contraseña correcta se informa que la cuenta está desactivada.
    if (!usuario.activo) {
      return problema(
        401,
        TIPOS_PROBLEMA.cuentaDesactivada,
        'Cuenta desactivada',
        'Tu cuenta está desactivada. Contactá a la Jefatura.',
      )
    }
    base.idSesion = usuario.id
    guardarBase()
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/v1/sesion', () => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    const { id, nombre, correo, rol, idCuadrilla } = usuario
    return HttpResponse.json({ id, nombre, correo, rol, idCuadrilla })
  }),

  http.delete('/api/v1/sesion', () => {
    base.idSesion = null
    guardarBase()
    return new HttpResponse(null, { status: 204 })
  }),
]
