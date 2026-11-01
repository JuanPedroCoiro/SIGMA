import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { esquemaCuadrilla, type CamposCuadrilla, type ValoresCuadrilla } from '../esquemas'
import './UsuarioForm.css'

const CAMPOS = ['nombre', 'zona'] as const

type Props = {
  valoresIniciales?: CamposCuadrilla
  textoEnviar: string
  onEnviar: (valores: ValoresCuadrilla) => Promise<unknown>
}

/** Alta y edición de una cuadrilla. */
export function CuadrillaForm({ valoresIniciales, textoEnviar, onEnviar }: Props) {
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CamposCuadrilla, unknown, ValoresCuadrilla>({
    resolver: zodResolver(esquemaCuadrilla),
    defaultValues: valoresIniciales ?? { nombre: '', zona: '' },
  })

  async function enviar(valores: ValoresCuadrilla) {
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
        <label htmlFor="zona">Zona (opcional)</label>
        <input id="zona" autoComplete="off" {...propsError('zona')} {...register('zona')} />
        {mensajeError('zona')}
      </div>

      <div className="usuario-form-acciones">
        <Link to="/cuadrillas" className="boton boton-secundario">
          Cancelar
        </Link>
        <button type="submit" className="boton boton-primario" disabled={isSubmitting}>
          {isSubmitting ? 'Guardando...' : textoEnviar}
        </button>
      </div>
    </form>
  )
}
