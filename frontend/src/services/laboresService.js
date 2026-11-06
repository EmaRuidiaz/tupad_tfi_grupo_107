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

export function iniciarLabor(id) {
  return apiFetch(`/labores/${id}/iniciar`, {
    method: 'POST',
  })
}

export function completarLabor(id, payload = {}) {
  return apiFetch(`/labores/${id}/completar`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

// HU-10: valida el clima actual del lote contra la regla del tipo de labor (CU-04, RN-01).
export function validarClimaLabor(id) {
  return apiFetch(`/labores/${id}/validar-clima`, {
    method: 'POST',
  })
}

// RN-11: aprobación manual cuando el servicio climático no responde y no hay caché.
export function autorizarLaborManualmente(id, justificacion) {
  return apiFetch(`/labores/${id}/autorizacion-manual`, {
    method: 'POST',
    body: JSON.stringify({ justificacion }),
  })
}
