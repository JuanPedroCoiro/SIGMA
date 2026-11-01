import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, type Location } from 'react-router-dom'
import { z } from 'zod'
import { useSesion } from '../../../app/sesion/useSesion'
import { useIniciarSesion } from '../hooks/useIniciarSesion'
import { mensajeErrorInicioSesion } from '../mensajesError'
import './InicioSesionPage.css'

const esquema = z.object({
  correo: z.string().trim().min(1, 'Ingresá tu correo.').pipe(z.email('Ingresá un correo válido.')),
  contrasena: z.string().min(1, 'Ingresá tu contraseña.'),
})

type Campos = z.infer<typeof esquema>

/** Lo que deja RutaProtegida al redirigir hasta acá. */
type EstadoRedireccion = { desde?: Location; expirada?: boolean } | null

export function InicioSesionPage() {
  const sesion = useSesion()
  const location = useLocation()
  const estado = location.state as EstadoRedireccion
  const iniciarSesion = useIniciarSesion()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Campos>({ resolver: zodResolver(esquema) })

  if (sesion.estado === 'autenticado') {
    const destino = estado?.desde ? estado.desde.pathname + estado.desde.search : '/'
    return <Navigate to={destino} replace />
  }

  const avisoExpirada = estado?.expirada && !iniciarSesion.isError

  return (
    <main className="inicio-sesion">
      <form
        className="inicio-sesion-tarjeta"
        noValidate
        onSubmit={handleSubmit((campos) => iniciarSesion.mutate(campos))}
      >
        <h1>SIGMA</h1>
        <p className="inicio-sesion-subtitulo">Ingresá con tu correo y tu contraseña.</p>

        {avisoExpirada && (
          <p className="alerta alerta-aviso" role="status">
            Tu sesión venció por inactividad. Volvé a ingresar para continuar.
          </p>
        )}
        {iniciarSesion.isError && (
          <p className="alerta alerta-error" role="alert">
            {mensajeErrorInicioSesion(iniciarSesion.error)}
          </p>
        )}

        <div className="campo">
          <label htmlFor="correo">Correo</label>
          <input
            id="correo"
            type="email"
            autoComplete="username"
            inputMode="email"
            aria-invalid={errors.correo ? true : undefined}
            aria-describedby={errors.correo ? 'correo-error' : undefined}
            {...register('correo')}
          />
          {errors.correo && (
            <span id="correo-error" className="campo-error">
              {errors.correo.message}
            </span>
          )}
        </div>

        <div className="campo">
          <label htmlFor="contrasena">Contraseña</label>
          <input
            id="contrasena"
            type="password"
            autoComplete="current-password"
            aria-invalid={errors.contrasena ? true : undefined}
            aria-describedby={errors.contrasena ? 'contrasena-error' : undefined}
            {...register('contrasena')}
          />
          {errors.contrasena && (
            <span id="contrasena-error" className="campo-error">
              {errors.contrasena.message}
            </span>
          )}
        </div>

        <button type="submit" className="boton boton-primario" disabled={iniciarSesion.isPending}>
          {iniciarSesion.isPending ? 'Ingresando...' : 'Ingresar'}
        </button>
      </form>
    </main>
  )
}
