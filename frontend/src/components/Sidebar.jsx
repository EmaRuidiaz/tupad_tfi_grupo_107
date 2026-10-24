import { NavLink } from 'react-router-dom'
import { menuItems } from '../routes/menuItems.js'

function Sidebar({ abierto, onNavegar }) {
  return (
    <aside className={`sidebar ${abierto ? 'sidebar--abierto' : ''}`}>
      <div className="sidebar__marca">
        <span className="sidebar__logo">🌱</span>
        <span>AgTech</span>
      </div>

      <nav>
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
    </aside>
  )
}

export default Sidebar
