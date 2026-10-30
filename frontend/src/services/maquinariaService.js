import { apiFetch } from './api.js'

export function listarMaquinarias(filtros = {}) {
  const params = new URLSearchParams()
  if (filtros.estado) params.append('estado', filtros.estado)
  if (
    filtros.mantenimientoVencido !== undefined &&
    filtros.mantenimientoVencido !== null
  ) {
    params.append('mantenimientoVencido', String(filtros.mantenimientoVencido))
  }
  const query = params.toString() ? `?${params.toString()}` : ''
  return apiFetch(`/maquinarias${query}`)
}

export function obtenerMaquinaria(id) {
  return apiFetch(`/maquinarias/${id}`)
}

export function crearMaquinaria(payload) {
  return apiFetch('/maquinarias', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarMaquinaria(id, payload) {
  return apiFetch(`/maquinarias/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function cambiarEstadoMaquinaria(id, estado) {
  return apiFetch(`/maquinarias/${id}/estado`, {
    method: 'PATCH',
    body: JSON.stringify({ estado }),
  })
}

export function eliminarMaquinaria(id) {
  return apiFetch(`/maquinarias/${id}`, {
    method: 'DELETE',
  })
}

export function listarMantenimientos(maquinariaId) {
  return apiFetch(`/maquinarias/${maquinariaId}/mantenimientos`)
}

export function registrarMantenimiento(maquinariaId, payload) {
  return apiFetch(`/maquinarias/${maquinariaId}/mantenimientos`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
