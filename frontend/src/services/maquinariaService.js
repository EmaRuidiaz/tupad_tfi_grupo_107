import { apiFetch } from './api.js'

export function listarMaquinarias() {
  return apiFetch('/maquinarias')
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
