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