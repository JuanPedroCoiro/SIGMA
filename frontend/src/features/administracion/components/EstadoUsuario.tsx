export function EstadoUsuario({ activo }: { activo: boolean }) {
  return (
    <span className={activo ? 'estado estado-activo' : 'estado estado-inactivo'}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}
