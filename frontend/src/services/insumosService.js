import { apiFetch } from './api.js'

export function listarInsumos(soloStockCritico = false) {
  const query = soloStockCritico ? '?stockCritico=true' : ''
  return apiFetch(`/insumos${query}`)
}

export function obtenerInsumo(id) {
  return apiFetch(`/insumos/${id}`)
}

export function crearInsumo(payload) {
  return apiFetch('/insumos', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarInsumo(id, payload) {
  return apiFetch(`/insumos/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function eliminarInsumo(id) {
  return apiFetch(`/insumos/${id}`, {
    method: 'DELETE',
  })
}

export function registrarAjusteStock(id, payload) {
  return apiFetch(`/insumos/${id}/ajustes`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listarMovimientosInsumo(id) {
  return apiFetch(`/insumos/${id}/movimientos`)
}
