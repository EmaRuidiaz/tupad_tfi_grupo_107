import { apiFetch } from './api.js'

export function listarReglasClimaticas(tipoLabor) {
  const query = tipoLabor
    ? `?tipoLabor=${encodeURIComponent(tipoLabor)}`
    : ''
  return apiFetch(`/reglas-climaticas${query}`)
}

export function obtenerReglaClimatica(id) {
  return apiFetch(`/reglas-climaticas/${id}`)
}

export function crearReglaClimatica(payload) {
  return apiFetch('/reglas-climaticas', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarReglaClimatica(id, payload) {
  return apiFetch(`/reglas-climaticas/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function eliminarReglaClimatica(id) {
  return apiFetch(`/reglas-climaticas/${id}`, {
    method: 'DELETE',
  })
}
