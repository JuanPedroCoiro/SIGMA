import { z } from 'zod'
import { ROLES } from '../../shared/lib/roles'

// Las mismas reglas que el Request del backend (CU-13a y CU-13b).

export const LARGO_MINIMO_PASSWORD = 10

const password = z
  .string()
  .min(
    LARGO_MINIMO_PASSWORD,
    `La contraseña tiene que tener al menos ${LARGO_MINIMO_PASSWORD} caracteres.`,
  )

/** Datos del usuario; con `conPassword`, también la contraseña inicial (alta). */
export function crearEsquemaUsuario(conPassword: boolean) {
  return z
    .object({
      nombre: z.string().trim().min(1, 'Ingresá el nombre.').max(150, 'Máximo 150 caracteres.'),
      correo: z
        .string()
        .trim()
        .min(1, 'Ingresá el correo.')
        .max(255, 'Máximo 255 caracteres.')
        .pipe(z.email('Ingresá un correo válido, por ejemplo nombre@ciemsa.com.')),
      rol: z.enum(ROLES, { error: 'Elegí un rol.' }),
      // El select devuelve texto: '' es «sin cuadrilla».
      idCuadrilla: z.string().transform((valor) => (valor ? Number(valor) : null)),
      password: conPassword ? password : z.string(),
    })
    .refine((datos) => datos.rol !== 'TECNICO' || datos.idCuadrilla !== null, {
      path: ['idCuadrilla'],
      message: 'Elegí la cuadrilla del técnico.',
      // Se informa junto con los demás errores, no recién cuando el resto está bien.
      when: () => true,
    })
    .transform((datos) => ({
      ...datos,
      // Solo el técnico lleva cuadrilla (CU-13a 4b).
      idCuadrilla: datos.rol === 'TECNICO' ? datos.idCuadrilla : null,
    }))
}

export type CamposUsuario = z.input<ReturnType<typeof crearEsquemaUsuario>>
export type ValoresUsuario = z.output<ReturnType<typeof crearEsquemaUsuario>>

export const esquemaPassword = z.object({ password })

export type CamposPassword = z.infer<typeof esquemaPassword>
