import { apiFetch } from './api.js'

// HU-12: registro de cosechas (cierre del ciclo productivo del lote).
export function listarCosechas({ loteId } = {}) {
  return apiFetch(`/cosechas${loteId ? `?loteId=${loteId}` : ''}`)
}

export function obtenerCosecha(id) {
  return apiFetch(`/cosechas/${id}`)
}

export function registrarCosecha(payload) {
  return apiFetch('/cosechas', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

// Catálogo de cultivos (sólo lectura).
export function listarCultivos() {
  return apiFetch('/cultivos')
}
