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

export const TIPOS_MAQUINARIA = [
  { valor: 'TRACTOR', etiqueta: 'Tractor' },
  { valor: 'COSECHADORA', etiqueta: 'Cosechadora' },
  { valor: 'PULVERIZADORA', etiqueta: 'Pulverizadora' },
  { valor: 'SEMBRADORA', etiqueta: 'Sembradora' },
  { valor: 'VEHICULO_APOYO', etiqueta: 'Vehículo de apoyo' },
]

export const ESTADOS_MAQUINARIA = [
  { valor: 'OPERATIVO', etiqueta: 'Operativo' },
  { valor: 'EN_MANTENIMIENTO', etiqueta: 'En mantenimiento' },
  { valor: 'EN_LABOR', etiqueta: 'En labor' },
  { valor: 'FUERA_DE_SERVICIO', etiqueta: 'Fuera de servicio' },
]

export function etiquetaTipoMaquinaria(tipo) {
  return TIPOS_MAQUINARIA.find((t) => t.valor === tipo)?.etiqueta || tipo
}

export function etiquetaEstadoMaquinaria(estado) {
  return ESTADOS_MAQUINARIA.find((e) => e.valor === estado)?.etiqueta || estado
}

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
