import { apiFetch } from './api.js'

function construirQueryTablero({
  estadoLote,
  soloConCosecha,
  fechaDesde,
  fechaHasta,
  alcanceCiclo,
} = {}) {
  const params = new URLSearchParams()
  if (estadoLote && estadoLote !== 'TODOS') {
    params.set('estadoLote', estadoLote)
  }
  if (soloConCosecha) {
    params.set('soloConCosecha', 'true')
  }
  if (fechaDesde) {
    params.set('fechaDesde', fechaDesde)
  }
  if (fechaHasta) {
    params.set('fechaHasta', fechaHasta)
  }
  if (alcanceCiclo && alcanceCiclo !== 'HISTORICO') {
    params.set('alcanceCiclo', alcanceCiclo)
  }
  return params.toString()
}

export function obtenerTableroFinanciero(filtros = {}) {
  const query = construirQueryTablero(filtros)
  return apiFetch(`/tablero-financiero${query ? `?${query}` : ''}`)
}

export function listarReportesFinancierosLotes(filtros = {}) {
  const query = construirQueryTablero(filtros)
  return apiFetch(`/tablero-financiero/lotes${query ? `?${query}` : ''}`)
}

export function obtenerReporteFinancieroLote(loteId, filtros = {}) {
  const query = construirQueryTablero(filtros)
  return apiFetch(`/tablero-financiero/lotes/${loteId}${query ? `?${query}` : ''}`)
}
