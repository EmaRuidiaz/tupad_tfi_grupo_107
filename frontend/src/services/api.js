const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'
const STORAGE_KEY = 'agtech_session'

export function getSession() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw)
    if (!session?.token) return null
    if (session.expiresAt && new Date(session.expiresAt) <= new Date()) {
      clearSession()
      return null
    }
    return session
  } catch {
    return null
  }
}

export function saveSession(authResponse) {
  const rolLimpio = (authResponse.roles?.[0] || '').replace(/^ROLE_/, '')
  const session = {
    token: authResponse.token,
    username: authResponse.username,
    roles: authResponse.roles || [],
    rol: rolLimpio,
    expiresAt: authResponse.expiresAt,
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  window.dispatchEvent(new Event('agtech-auth-change'))
  return session
}

export function clearSession() {
  localStorage.removeItem(STORAGE_KEY)
  window.dispatchEvent(new Event('agtech-auth-change'))
}

export function isAdmin() {
  const session = getSession()
  return session?.rol === 'ADMIN' || session?.roles?.includes('ROLE_ADMIN')
}

export function tieneAlgunRol(...roles) {
  const session = getSession()
  return roles.some(
    (rol) => session?.rol === rol || session?.roles?.includes(`ROLE_${rol}`),
  )
}

export async function login(username, password) {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(data?.message || 'Error al iniciar sesión')
  }

  return saveSession(data)
}

export async function apiFetch(path, options = {}) {
  const session = getSession()
  const headers = {
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(options.headers || {}),
  }

  if (session?.token) {
    headers.Authorization = `Bearer ${session.token}`
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  })

  if (response.status === 401) {
    clearSession()
    const error = new Error('Sesión expirada o no autenticada. Iniciá sesión nuevamente.')
    error.status = 401
    throw error
  }

  if (response.status === 204) {
    return null
  }

  const data = await response.json().catch(() => null)

  if (!response.ok) {
    const error = new Error(
      data?.message || `Error HTTP ${response.status}`,
    )
    error.status = response.status
    throw error
  }

  return data
}
