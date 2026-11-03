import { apiFetch } from './api.js'

export function listarLabores({ loteId, estado, tipoLabor } = {}) {
  const params = new URLSearchParams()
  if (loteId) params.set('loteId', String(loteId))
  if (estado) params.set('estado', estado)
  if (tipoLabor) params.set('tipoLabor', tipoLabor)
  const query = params.toString()
  return apiFetch(`/labores${query ? `?${query}` : ''}`)
}

export function obtenerLabor(id) {
  return apiFetch(`/labores/${id}`)
}

export function programarLabor(payload) {
  return apiFetch('/labores', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarLabor(id, payload) {
  return apiFetch(`/labores/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function reprogramarLabor(id, payload) {
  return apiFetch(`/labores/${id}/reprogramar`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function cancelarLabor(id) {
  return apiFetch(`/labores/${id}/cancelar`, {
    method: 'POST',
  })
}
