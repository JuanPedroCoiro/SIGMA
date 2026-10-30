import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useSesion } from '../sesion/useSesion'
import { opcionesParaRol } from './menu'
import './Layout.css'

export function Layout() {
  const sesion = useSesion()
  const [menuAbierto, setMenuAbierto] = useState(false)

  // RutaProtegida garantiza la sesión; el chequeo es para que TypeScript lo sepa.
  if (sesion.estado !== 'autenticado') return null
  const { usuario } = sesion

  return (
    <div className="layout">
      <header className="layout-encabezado">
        <button
          type="button"
          className="layout-boton-menu"
          aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
          aria-expanded={menuAbierto}
          aria-controls="menu-principal"
          onClick={() => setMenuAbierto((abierto) => !abierto)}
        >
          ☰
        </button>
        <span className="layout-marca">SIGMA</span>
        <span className="layout-usuario">{usuario.nombre}</span>
      </header>

      <nav
        id="menu-principal"
        aria-label="Menú principal"
        className={menuAbierto ? 'layout-menu abierto' : 'layout-menu'}
      >
        <ul>
          {opcionesParaRol(usuario.rol).map((opcion) => (
            <li key={opcion.ruta}>
              <NavLink to={opcion.ruta} end onClick={() => setMenuAbierto(false)}>
                {opcion.etiqueta}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <main className="layout-contenido">
        <Outlet />
      </main>
    </div>
  )
}
