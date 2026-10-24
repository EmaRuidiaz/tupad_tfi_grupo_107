import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { getSession, login } from '../services/api.js'

// Accesos rápidos con los usuarios semilla de database/data.sql.
// Sólo existen en desarrollo: en el build de producción `import.meta.env.DEV` es `false`
// y Vite elimina estos datos del bundle, por lo que la contraseña no queda publicada.
const USUARIOS_DEMO = import.meta.env.DEV
  ? [
      { username: 'admin', rol: 'ADMIN (ABM completo)' },
      { username: 'agronomo1', rol: 'AGRONOMO (Consulta)' },
      { username: 'operario1', rol: 'OPERARIO (Consulta)' },
    ]
  : []
const PASSWORD_DEMO = import.meta.env.DEV ? 'agtech123' : ''

function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState('')

  const sesionActual = getSession()
  const destino = location.state?.from?.pathname || '/'

  if (sesionActual) {
    return <Navigate to={destino} replace />
  }

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setCargando(true)
    try {
      await login(username.trim(), password)
      navigate(destino, { replace: true })
    } catch (err) {
      setError(err.message || 'No se pudo iniciar sesión')
    } finally {
      setCargando(false)
    }
  }

  function completarDemo(usuarioDemo) {
    setUsername(usuarioDemo)
    setPassword(PASSWORD_DEMO)
    setError('')
  }

  return (
    <div className="login-contenedor">
      <div className="login-tarjeta">
        <div className="login-encabezado">
          <span className="login-logo">🌱</span>
          <h1>Sistema AgTech</h1>
          <p>Iniciá sesión con tus credenciales para continuar</p>
        </div>

        {error && (
          <div className="alerta alerta--error" role="alert">
            {error}
          </div>
        )}

        <form onSubmit={manejarSubmit} className="formulario">
          <div className="campo">
            <label htmlFor="username">Usuario</label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Ej: admin"
              required
              autoComplete="username"
            />
          </div>

          <div className="campo">
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              autoComplete="current-password"
            />
          </div>

          <button
            type="submit"
            className="btn btn--primario btn--bloque"
            disabled={cargando}
          >
            {cargando ? 'Ingresando...' : 'Iniciar sesión'}
          </button>
        </form>

        {USUARIOS_DEMO.length > 0 && (
          <div className="login-demo">
            <p className="login-demo__titulo">
              Acceso rápido de desarrollo (clave: {PASSWORD_DEMO}):
            </p>
            <div className="login-demo__botones">
              {USUARIOS_DEMO.map((u) => (
                <button
                  key={u.username}
                  type="button"
                  className="btn btn--secundario btn--chico"
                  onClick={() => completarDemo(u.username)}
                >
                  {u.rol}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default Login
