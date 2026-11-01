import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Modal } from '../../../shared/components/Modal'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { esquemaPassword, LARGO_MINIMO_PASSWORD, type CamposPassword } from '../esquemas'

type Props = {
  nombreUsuario: string
  onRestablecer: (password: string) => Promise<unknown>
  onCerrar: () => void
}

/** CU-13b: la Jefatura escribe la contraseña nueva. También desbloquea la cuenta. */
export function RestablecerPasswordDialogo({ nombreUsuario, onRestablecer, onCerrar }: Props) {
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CamposPassword>({
    resolver: zodResolver(esquemaPassword),
    defaultValues: { password: '' },
  })

  async function enviar({ password }: CamposPassword) {
    setErrorGeneral(null)
    try {
      await onRestablecer(password)
    } catch (error) {
      setErrorGeneral(aplicarErroresDelServidor(error, setError, ['password']))
    }
  }

  return (
    <Modal titulo="Restablecer contraseña" onCerrar={onCerrar}>
      <form className="usuario-form" noValidate onSubmit={handleSubmit(enviar)}>
        <p>
          Escribí la contraseña nueva de <strong>{nombreUsuario}</strong>. Si la cuenta estaba
          bloqueada, queda desbloqueada.
        </p>
        {errorGeneral && (
          <p className="alerta alerta-error" role="alert">
            {errorGeneral}
          </p>
        )}
        <div className="campo">
          <label htmlFor="password-nueva">Contraseña nueva</label>
          <input
            id="password-nueva"
            type="password"
            autoComplete="new-password"
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={errors.password ? 'password-nueva-error' : 'password-nueva-ayuda'}
            {...register('password')}
          />
          <span id="password-nueva-ayuda" className="campo-ayuda">
            Mínimo {LARGO_MINIMO_PASSWORD} caracteres.
          </span>
          {errors.password && (
            <span id="password-nueva-error" className="campo-error">
              {errors.password.message}
            </span>
          )}
        </div>
        <div className="modal-acciones">
          <button type="button" className="boton boton-secundario" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="boton boton-primario" disabled={isSubmitting}>
            {isSubmitting ? 'Guardando...' : 'Restablecer'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
