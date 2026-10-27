import { apiFetch } from './api.js'

export function listarLotes() {
  return apiFetch('/lotes')
}

export function obtenerLote(id) {
  return apiFetch(`/lotes/${id}`)
}

export function crearLote(payload) {
  return apiFetch('/lotes', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarLote(id, payload) {
  return apiFetch(`/lotes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function eliminarLote(id) {
  return apiFetch(`/lotes/${id}`, {
    method: 'DELETE',
  })
}

export function cambiarEstadoLote(id, payload) {
  return apiFetch(`/lotes/${id}/estado`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}
