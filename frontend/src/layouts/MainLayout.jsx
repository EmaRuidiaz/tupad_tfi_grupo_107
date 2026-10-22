import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from '../components/Sidebar.jsx'

// Estructura común de todas las pantallas: menú lateral + contenido de la ruta actual.
function MainLayout() {
  const [menuAbierto, setMenuAbierto] = useState(false)

  return (
    <div className="layout">
      <header className="topbar">
        <button
          type="button"
          className="topbar__toggle"
          aria-label="Abrir menú"
          aria-expanded={menuAbierto}
          onClick={() => setMenuAbierto(!menuAbierto)}
        >
          ☰
        </button>
        <span className="topbar__titulo">AgTech</span>
      </header>

      <Sidebar abierto={menuAbierto} onNavegar={() => setMenuAbierto(false)} />

      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}

export default MainLayout
