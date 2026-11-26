import { apiFetch } from './api.js'

export function listarCompras() {
  return apiFetch('/compras')
}

export function obtenerCompra(id) {
  return apiFetch(`/compras/${id}`)
}

export function registrarCompra(payload) {
  return apiFetch('/compras', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listarProveedores() {
  return apiFetch('/proveedores')
}

export function crearProveedor(payload) {
  return apiFetch('/proveedores', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarProveedor(id, payload) {
  return apiFetch(`/proveedores/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function eliminarProveedor(id) {
  return apiFetch(`/proveedores/${id}`, {
    method: 'DELETE',
  })
}
