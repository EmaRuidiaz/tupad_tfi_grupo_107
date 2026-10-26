export const CATEGORIAS_INSUMO = [
  { valor: 'SEMILLA', etiqueta: 'Semilla' },
  { valor: 'FERTILIZANTE', etiqueta: 'Fertilizante' },
  { valor: 'FITOSANITARIO', etiqueta: 'Fitosanitario' },
  { valor: 'COMBUSTIBLE', etiqueta: 'Combustible' },
]

export const UNIDADES_INSUMO = [
  { valor: 'BOLSAS', etiqueta: 'Bolsas' },
  { valor: 'LITROS', etiqueta: 'Litros' },
  { valor: 'KILOGRAMOS', etiqueta: 'Kilogramos' },
  { valor: 'TONELADAS', etiqueta: 'Toneladas' },
]

export function formatearNumero(valor) {
  return Number(valor ?? 0).toLocaleString('es-AR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

export function formatearFecha(iso) {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('es-AR', {
    dateStyle: 'short',
    timeStyle: 'short',
  })
}
