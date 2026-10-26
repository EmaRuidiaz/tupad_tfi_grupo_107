import { NavLink } from 'react-router-dom'
import { menuItems } from '../routes/menuItems.js'

function Sidebar({ abierto, onNavegar, sesion, onCerrarSesion }) {
  return (
    <aside className={`sidebar ${abierto ? 'sidebar--abierto' : ''}`}>
      <div className="sidebar__marca">
        <span className="sidebar__logo">🌱</span>
        <span>AgTech</span>
      </div>

      <nav className="sidebar__nav">
        <ul className="sidebar__menu">
          {menuItems.map((item) => (
            <li key={item.path}>
              <NavLink
                to={item.path}
                end={item.end}
                onClick={onNavegar}
                className={({ isActive }) =>
                  `sidebar__link ${isActive ? 'sidebar__link--activo' : ''}`
                }
              >
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      {sesion && (
        <div className="sidebar__usuario">
          <div className="sidebar__usuario-info">
            <span className="sidebar__usuario-nombre">{sesion.username}</span>
            <span className="sidebar__usuario-rol">{sesion.rol}</span>
          </div>
          <button
            type="button"
            className="sidebar__salir"
            onClick={onCerrarSesion}
          >
            Cerrar sesión
          </button>
        </div>
      )}
    </aside>
  )
}

export default Sidebar
