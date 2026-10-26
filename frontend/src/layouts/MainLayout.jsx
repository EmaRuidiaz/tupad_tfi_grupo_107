import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import Sidebar from '../components/Sidebar.jsx'
import { clearSession, getSession } from '../services/api.js'

// Estructura común de todas las pantallas: menú lateral + contenido de la ruta actual.
function MainLayout() {
  const [menuAbierto, setMenuAbierto] = useState(false)
  const [sesion, setSesion] = useState(() => getSession())
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    function sincronizarSesion() {
      setSesion(getSession())
    }
    window.addEventListener('agtech-auth-change', sincronizarSesion)
    window.addEventListener('storage', sincronizarSesion)
    return () => {
      window.removeEventListener('agtech-auth-change', sincronizarSesion)
      window.removeEventListener('storage', sincronizarSesion)
    }
  }, [])

  if (!sesion) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  function manejarCerrarSesion() {
    clearSession()
    navigate('/login', { replace: true })
  }

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

      <Sidebar
        abierto={menuAbierto}
        onNavegar={() => setMenuAbierto(false)}
        sesion={sesion}
        onCerrarSesion={manejarCerrarSesion}
      />

      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}

export default MainLayout
