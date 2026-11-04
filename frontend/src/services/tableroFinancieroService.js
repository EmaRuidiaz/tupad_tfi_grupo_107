import { apiFetch } from './api.js'

export function obtenerTableroFinanciero({ estadoLote, soloConCosecha } = {}) {
  const params = new URLSearchParams()
  if (estadoLote && estadoLote !== 'TODOS') {
    params.set('estadoLote', estadoLote)
  }
  if (soloConCosecha) {
    params.set('soloConCosecha', 'true')
  }
  const query = params.toString()
  return apiFetch(`/tablero-financiero${query ? `?${query}` : ''}`)
}

export function listarReportesFinancierosLotes({
  estadoLote,
  soloConCosecha,
} = {}) {
  const params = new URLSearchParams()
  if (estadoLote && estadoLote !== 'TODOS') {
    params.set('estadoLote', estadoLote)
  }
  if (soloConCosecha) {
    params.set('soloConCosecha', 'true')
  }
  const query = params.toString()
  return apiFetch(`/tablero-financiero/lotes${query ? `?${query}` : ''}`)
}

export function obtenerReporteFinancieroLote(loteId) {
  return apiFetch(`/tablero-financiero/lotes/${loteId}`)
}
