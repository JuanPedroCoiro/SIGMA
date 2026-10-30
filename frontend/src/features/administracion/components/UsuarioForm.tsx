import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { ETIQUETA_ROL, ROLES } from '../../../shared/lib/roles'
import {
  crearEsquemaUsuario,
  LARGO_MINIMO_PASSWORD,
  type CamposUsuario,
  type ValoresUsuario,
} from '../esquemas'
import { useCuadrillas } from '../hooks/useCuadrillas'
import './UsuarioForm.css'

const CAMPOS = ['nombre', 'correo', 'rol', 'idCuadrilla', 'password'] as const

type Props = {
  /** Alta (con contraseña inicial) o edición (sin contraseña, CU-13b). */
  conPassword: boolean
  valoresIniciales?: CamposUsuario
  textoEnviar: string
  urlCancelar: string
  onEnviar: (valores: ValoresUsuario) => Promise<unknown>
}

export function UsuarioForm({
  conPassword,
  valoresIniciales,
  textoEnviar,
  urlCancelar,
  onEnviar,
}: Props) {
  const esquema = useMemo(() => crearEsquemaUsuario(conPassword), [conPassword])
  const cuadrillas = useCuadrillas()
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CamposUsuario, unknown, ValoresUsuario>({
    resolver: zodResolver(esquema),
    defaultValues: valoresIniciales ?? { nombre: '', correo: '', idCuadrilla: '', password: '' },
  })

  const esTecnico = useWatch({ control, name: 'rol' }) === 'TECNICO'
  const idCuadrillaInicial = valoresIniciales?.idCuadrilla
  // Las inactivas no se ofrecen, salvo la que ya tenía el usuario.
  const opcionesCuadrilla = (cuadrillas.data ?? []).filter(
    (c) => c.activa || String(c.id) === idCuadrillaInicial,
  )

  async function enviar(valores: ValoresUsuario) {
    setErrorGeneral(null)
    try {
      await onEnviar(valores)
    } catch (error) {
      setErrorGeneral(aplicarErroresDelServidor(error, setError, CAMPOS))
    }
  }

  function propsError(campo: (typeof CAMPOS)[number]) {
    return {
      'aria-invalid': errors[campo] ? true : undefined,
      'aria-describedby': errors[campo] ? `${campo}-error` : undefined,
    }
  }

  function mensajeError(campo: (typeof CAMPOS)[number]) {
    const mensaje = errors[campo]?.message
    return mensaje ? (
      <span id={`${campo}-error`} className="campo-error">
        {mensaje}
      </span>
    ) : null
  }

  return (
    <form className="usuario-form" noValidate onSubmit={handleSubmit(enviar)}>
      {errorGeneral && (
        <p className="alerta alerta-error" role="alert">
          {errorGeneral}
        </p>
      )}

      <div className="campo">
        <label htmlFor="nombre">Nombre</label>
        <input id="nombre" autoComplete="off" {...propsError('nombre')} {...register('nombre')} />
        {mensajeError('nombre')}
      </div>

      <div className="campo">
        <label htmlFor="correo">Correo (usuario de acceso)</label>
        <input
          id="correo"
          type="email"
          autoComplete="off"
          inputMode="email"
          {...propsError('correo')}
          {...register('correo')}
        />
        {mensajeError('correo')}
      </div>

      <div className="campo">
        <label htmlFor="rol">Rol</label>
        <select id="rol" defaultValue="" {...propsError('rol')} {...register('rol')}>
          <option value="" disabled>
            Elegí un rol
          </option>
          {ROLES.map((rol) => (
            <option key={rol} value={rol}>
              {ETIQUETA_ROL[rol]}
            </option>
          ))}
        </select>
        {mensajeError('rol')}
      </div>

      {esTecnico && (
        <div className="campo">
          <label htmlFor="idCuadrilla">Cuadrilla</label>
          <select id="idCuadrilla" {...propsError('idCuadrilla')} {...register('idCuadrilla')}>
            <option value="">Elegí una cuadrilla</option>
            {opcionesCuadrilla.map((c) => (
              <option key={c.id} value={String(c.id)}>
                {c.nombre}
              </option>
            ))}
          </select>
          {cuadrillas.isError && (
            <span className="campo-error">No se pudieron cargar las cuadrillas.</span>
          )}
          {mensajeError('idCuadrilla')}
        </div>
      )}

      {conPassword && (
        <div className="campo">
          <label htmlFor="password">Contraseña inicial</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            {...propsError('password')}
            {...register('password')}
          />
          <span className="campo-ayuda">
            Mínimo {LARGO_MINIMO_PASSWORD} caracteres. Comunicásela al usuario por un medio seguro.
          </span>
          {mensajeError('password')}
        </div>
      )}

      <div className="usuario-form-acciones">
        <Link to={urlCancelar} className="boton boton-secundario">
          Cancelar
        </Link>
        <button type="submit" className="boton boton-primario" disabled={isSubmitting}>
          {isSubmitting ? 'Guardando...' : textoEnviar}
        </button>
      </div>
    </form>
  )
}
