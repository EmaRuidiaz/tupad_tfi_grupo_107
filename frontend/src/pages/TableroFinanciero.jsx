import { useCallback, useEffect, useMemo, useState } from 'react'
import DetalleFinancieroLoteModal from '../components/tablero/DetalleFinancieroLoteModal.jsx'
import { getSession, tieneAlgunRol } from '../services/api.js'
import { obtenerTableroFinanciero } from '../services/tableroFinancieroService.js'
import {
  ESTADOS_LOTE,
  etiquetaEstadoLote,
  formatearFechaSimple,
  formatearNumero,
} from '../utils/formatters.js'

const FILTROS_ESTADO_ROI = [
  { valor: 'TODOS', etiqueta: 'Todos los estados de ROI' },
  { valor: 'CALCULADO', etiqueta: 'Con ROI calculado' },
  { valor: 'PENDIENTE', etiqueta: 'Pendiente de cosecha' },
  { valor: 'SIN_COSTOS', etiqueta: 'Sin costos registrados' },
]

const OPCIONES_ALCANCE_CICLO = [
  {
    valor: 'HISTORICO',
    etiqueta: 'Acumulado histórico (todas las campañas)',
  },
  {
    valor: 'ULTIMO_CICLO',
    etiqueta: 'Último ciclo productivo (RN-12)',
  },
]

const OPCIONES_ORDEN = [
  { valor: 'LOTE_ASC', etiqueta: 'Ordenar por: Lote (#ID)' },
  { valor: 'ROI_DESC', etiqueta: 'Ordenar por: Mayor ROI (%)' },
  { valor: 'ROI_ASC', etiqueta: 'Ordenar por: Menor ROI (%)' },
  { valor: 'MARGEN_DESC', etiqueta: 'Ordenar por: Mayor Margen Neto ($)' },
  { valor: 'COSTO_DESC', etiqueta: 'Ordenar por: Mayor Costo Total ($)' },
  { valor: 'COSTO_HA_DESC', etiqueta: 'Ordenar por: Mayor Costo / ha ($/ha)' },
]

function claseEtiquetaEstadoRoi(reporte) {
  if (reporte.estadoRoi === 'CALCULADO') {
    return Number(reporte.roiPct) >= 0
      ? 'etiqueta etiqueta--ok'
      : 'etiqueta etiqueta--critica'
  }
  if (reporte.estadoRoi === 'PENDIENTE') {
    return 'etiqueta etiqueta--advertencia'
  }
  return 'etiqueta etiqueta--info'
}

function escaparCeldaCsv(valor) {
  if (valor === null || valor === undefined) return ''
  const texto = String(valor).replace(/"/g, '""')
  return /[",;\n]/.test(texto) ? `"${texto}"` : texto
}

function exportarLotesCsv(lotes, alcanceCiclo) {
  const encabezados = [
    'ID Lote',
    'Lote',
    'Superficie (ha)',
    'Estado Lote',
    'Tipo Suelo',
    'Labores Completadas',
    'Horas Maquina',
    'Costo Insumos ($)',
    'Costo Maquinaria ($)',
    'Costo Total Acumulado ($)',
    'Costo por ha ($/ha)',
    'Cosecha Registrada',
    'Cultivo',
    'Fecha Ultima Cosecha',
    'Produccion (t)',
    'Rinde (t/ha)',
    'Ingreso Bruto ($)',
    'Margen Neto ($)',
    'Margen por ha ($/ha)',
    'ROI (%)',
    'Estado ROI',
    'Alcance Ciclo',
  ]

  const filas = lotes.map((item) => [
    item.loteId,
    item.loteNombre,
    item.superficieHa ?? 0,
    etiquetaEstadoLote(item.estadoLote),
    item.tipoSuelo || '',
    item.cantidadLaboresCompletadas ?? 0,
    item.horasMaquinaTotales ?? 0,
    item.costoInsumosAcumulado ?? 0,
    item.costoMaquinariaAcumulado ?? 0,
    item.costosTotalesAcumulados ?? 0,
    item.costoPorHa ?? 0,
    item.cosechaRegistrada ? 'SI' : 'NO',
    item.cultivoNombre || '',
    item.fechaUltimaCosecha || '',
    item.toneladasTotales ?? '',
    item.rindeTonPorHa ?? '',
    item.ingresoBrutoTotal ?? '',
    item.margenNeto ?? '',
    item.margenPorHa ?? '',
    item.roiPct ?? '',
    item.estadoRoi || '',
    alcanceCiclo,
  ])

  const contenido = [
    encabezados.map(escaparCeldaCsv).join(','),
    ...filas.map((fila) => fila.map(escaparCeldaCsv).join(',')),
  ].join('\r\n')

  const blob = new Blob([`\uFEFF${contenido}`], {
    type: 'text/csv;charset=utf-8;',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  const hoy = new Date().toISOString().slice(0, 10)
  link.download = `tablero_financiero_agtech_${alcanceCiclo.toLowerCase()}_${hoy}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function TableroFinanciero() {
  const sesion = getSession()
  const tienePermiso = tieneAlgunRol('ADMIN', 'AGRONOMO')

  const [tablero, setTablero] = useState(null)
  const [cargando, setCargando] = useState(tienePermiso)
  const [errorGlobal, setErrorGlobal] = useState('')

  const [busqueda, setBusqueda] = useState('')
  const [estadoLoteFiltro, setEstadoLoteFiltro] = useState('TODOS')
  const [estadoRoiFiltro, setEstadoRoiFiltro] = useState('TODOS')
  const [soloConCosecha, setSoloConCosecha] = useState(false)
  const [alcanceCiclo, setAlcanceCiclo] = useState('HISTORICO')
  const [fechaDesde, setFechaDesde] = useState('')
  const [fechaHasta, setFechaHasta] = useState('')
  const [ordenTabla, setOrdenTabla] = useState('LOTE_ASC')

  const [loteDetalle, setLoteDetalle] = useState(null)

  const recargarTablero = useCallback(
    async (override = {}) => {
      if (!tienePermiso) return
      const desde =
        override.fechaDesde !== undefined ? override.fechaDesde : fechaDesde
      const hasta =
        override.fechaHasta !== undefined ? override.fechaHasta : fechaHasta
      const alcance =
        override.alcanceCiclo !== undefined
          ? override.alcanceCiclo
          : alcanceCiclo

      if (desde && hasta && desde > hasta) {
        setErrorGlobal(
          'La fecha inicial (Desde) no puede ser posterior a la fecha final (Hasta).',
        )
        return
      }

      setErrorGlobal('')
      setCargando(true)
      try {
        const datos = await obtenerTableroFinanciero({
          fechaDesde: desde || undefined,
          fechaHasta: hasta || undefined,
          alcanceCiclo: alcance,
        })
        setTablero(datos)
      } catch (err) {
        setErrorGlobal(
          err.message || 'No se pudo cargar el tablero financiero.',
        )
      } finally {
        setCargando(false)
      }
    },
    [tienePermiso, fechaDesde, fechaHasta, alcanceCiclo],
  )

  useEffect(() => {
    if (!tienePermiso) {
      return
    }
    if (fechaDesde && fechaHasta && fechaDesde > fechaHasta) {
      return
    }
    let cancelado = false
    obtenerTableroFinanciero({
      fechaDesde: fechaDesde || undefined,
      fechaHasta: fechaHasta || undefined,
      alcanceCiclo,
    })
      .then((datos) => {
        if (!cancelado) {
          setErrorGlobal('')
          setTablero(datos)
        }
      })
      .catch((err) => {
        if (!cancelado) {
          setErrorGlobal(
            err.message || 'No se pudo cargar el tablero financiero.',
          )
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
  }, [tienePermiso, fechaDesde, fechaHasta, alcanceCiclo])

  const lotes = useMemo(() => tablero?.lotes ?? [], [tablero])

  const lotesFiltrados = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    const filtrados = lotes.filter((item) => {
      if (soloConCosecha && !item.cosechaRegistrada) {
        return false
      }
      if (estadoLoteFiltro !== 'TODOS' && item.estadoLote !== estadoLoteFiltro) {
        return false
      }
      if (estadoRoiFiltro !== 'TODOS' && item.estadoRoi !== estadoRoiFiltro) {
        return false
      }
      if (texto) {
        const coincideNombre = item.loteNombre?.toLowerCase().includes(texto)
        const coincideSuelo = item.tipoSuelo?.toLowerCase().includes(texto)
        const coincideCultivo = item.cultivoNombre
          ?.toLowerCase()
          .includes(texto)
        if (!coincideNombre && !coincideSuelo && !coincideCultivo) {
          return false
        }
      }
      return true
    })

    const copia = [...filtrados]
    copia.sort((a, b) => {
      if (ordenTabla === 'ROI_DESC') {
        const roiA =
          a.roiPct !== null && a.roiPct !== undefined
            ? Number(a.roiPct)
            : Number.NEGATIVE_INFINITY
        const roiB =
          b.roiPct !== null && b.roiPct !== undefined
            ? Number(b.roiPct)
            : Number.NEGATIVE_INFINITY
        return roiB - roiA
      }
      if (ordenTabla === 'ROI_ASC') {
        const roiA =
          a.roiPct !== null && a.roiPct !== undefined
            ? Number(a.roiPct)
            : Number.POSITIVE_INFINITY
        const roiB =
          b.roiPct !== null && b.roiPct !== undefined
            ? Number(b.roiPct)
            : Number.POSITIVE_INFINITY
        return roiA - roiB
      }
      if (ordenTabla === 'MARGEN_DESC') {
        const mA =
          a.margenNeto !== null && a.margenNeto !== undefined
            ? Number(a.margenNeto)
            : Number.NEGATIVE_INFINITY
        const mB =
          b.margenNeto !== null && b.margenNeto !== undefined
            ? Number(b.margenNeto)
            : Number.NEGATIVE_INFINITY
        return mB - mA
      }
      if (ordenTabla === 'COSTO_DESC') {
        return (
          Number(b.costosTotalesAcumulados || 0) -
          Number(a.costosTotalesAcumulados || 0)
        )
      }
      if (ordenTabla === 'COSTO_HA_DESC') {
        return Number(b.costoPorHa || 0) - Number(a.costoPorHa || 0)
      }
      return Number(a.loteId || 0) - Number(b.loteId || 0)
    })

    return copia
  }, [
    lotes,
    soloConCosecha,
    estadoLoteFiltro,
    estadoRoiFiltro,
    busqueda,
    ordenTabla,
  ])

  const maximoMontoEscala = useMemo(() => {
    const valores = lotesFiltrados.flatMap((l) => [
      Number(l.costosTotalesAcumulados || 0),
      Number(l.ingresoBrutoTotal || 0),
    ])
    const max = valores.length > 0 ? Math.max(...valores) : 0
    return max > 0 ? max : 1
  }, [lotesFiltrados])

  const hayFiltrosActivos =
    Boolean(busqueda.trim()) ||
    estadoLoteFiltro !== 'TODOS' ||
    estadoRoiFiltro !== 'TODOS' ||
    soloConCosecha ||
    alcanceCiclo !== 'HISTORICO' ||
    Boolean(fechaDesde) ||
    Boolean(fechaHasta) ||
    ordenTabla !== 'LOTE_ASC'

  function limpiarFiltros() {
    setBusqueda('')
    setEstadoLoteFiltro('TODOS')
    setEstadoRoiFiltro('TODOS')
    setSoloConCosecha(false)
    setAlcanceCiclo('HISTORICO')
    setFechaDesde('')
    setFechaHasta('')
    setOrdenTabla('LOTE_ASC')
    setErrorGlobal('')
  }

  if (!tienePermiso) {
    return (
      <section className="pagina">
        <div className="pagina__encabezado">
          <div>
            <h1>Tablero Financiero y ROI</h1>
            <p className="pagina__subtitulo">
              Reporte consolidado por lote de ingresos brutos, costos históricos
              acumulados, margen neto y retorno de inversión (HU-13 / RF-08).
            </p>
          </div>
        </div>
        <div className="alerta alerta--advertencia" role="alert">
          Acceso restringido: según la matriz de permisos por rol (RF-01), el
          Tablero Financiero y las métricas de ROI están habilitados únicamente
          para los perfiles <strong>ADMIN</strong> y <strong>AGRONOMO</strong>{' '}
          (tu rol actual es <strong>{sesion?.rol || 'OPERARIO'}</strong>).
        </div>
      </section>
    )
  }

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Tablero Financiero y ROI</h1>
          <p className="pagina__subtitulo">
            Consolidación económica por lote: comparación de Ingresos Brutos de
            cosecha vs. Costos Totales Acumulados de labores completadas,
            Margen Neto y Retorno de Inversión (HU-13 / RF-08 / RN-12).
          </p>
        </div>

        <div className="pagina__acciones">
          <button
            type="button"
            className="btn btn--secundario"
            onClick={() => exportarLotesCsv(lotesFiltrados, alcanceCiclo)}
            disabled={cargando || lotesFiltrados.length === 0}
          >
            Exportar CSV ({lotesFiltrados.length})
          </button>
          <button
            type="button"
            className="btn btn--secundario"
            onClick={() => recargarTablero()}
            disabled={cargando}
          >
            Actualizar métricas
          </button>
        </div>
      </div>

      {tablero && (
        <div className="tarjetas-resumen">
          <div className="tarjeta-kpi">
            <span className="tarjeta-kpi__titulo">
              Costos totales acumulados
            </span>
            <strong className="tarjeta-kpi__valor">
              $ {formatearNumero(tablero.costosTotalesAcumulados)}
            </strong>
            <span className="tarjeta-kpi__detalle">
              {tablero.totalLaboresCompletadas} labor
              {tablero.totalLaboresCompletadas === 1 ? '' : 'es'} completada
              {tablero.totalLaboresCompletadas === 1 ? '' : 's'} · Prom.: ${' '}
              {formatearNumero(tablero.costoPromedioPorHa)}/ha
            </span>
          </div>

          <div className="tarjeta-kpi">
            <span className="tarjeta-kpi__titulo">
              Composición de costos
            </span>
            <strong className="tarjeta-kpi__valor">
              $ {formatearNumero(tablero.costoInsumosTotal)}
            </strong>
            <span className="tarjeta-kpi__detalle">
              En insumos consumidos · Maquinaria: ${' '}
              {formatearNumero(tablero.costoMaquinariaTotal)}
            </span>
          </div>

          <div className="tarjeta-kpi">
            <span className="tarjeta-kpi__titulo">
              Ingresos brutos de cosecha
            </span>
            <strong className="tarjeta-kpi__valor">
              $ {formatearNumero(tablero.ingresosBrutosTotales)}
            </strong>
            <span className="tarjeta-kpi__detalle">
              {tablero.lotesConCosecha} de {tablero.totalLotes} lotes cosechados
              {tablero.lotesConCosecha > 0
                ? ` · ${formatearNumero(tablero.toneladasTotalesCosechadas)} t`
                : ' · Sin cosechas aún'}
            </span>
          </div>

          <div className="tarjeta-kpi">
            <span className="tarjeta-kpi__titulo">
              Margen neto y ROI (lotes cosechados)
            </span>
            <strong
              className={`tarjeta-kpi__valor ${
                tablero.lotesConCosecha > 0
                  ? Number(tablero.margenNetoLotesCosechados) >= 0
                    ? 'texto-exito'
                    : 'texto-peligro'
                  : ''
              }`}
            >
              {tablero.lotesConCosecha > 0
                ? `$ ${formatearNumero(tablero.margenNetoLotesCosechados)}`
                : 'Pendiente'}
            </strong>
            <span className="tarjeta-kpi__detalle">
              {tablero.roiLotesCosechadosPct != null
                ? `ROI: ${formatearNumero(tablero.roiLotesCosechadosPct)} % · Balance global: $ ${formatearNumero(tablero.margenNetoTotal)}`
                : tablero.lotesConCosecha > 0
                  ? 'Sin costos registrados en lotes cosechados'
                  : `${tablero.lotesSinCosecha} lote${tablero.lotesSinCosecha === 1 ? '' : 's'} en campaña activa`}
            </span>
          </div>
        </div>
      )}

      {errorGlobal && (
        <div className="alerta alerta--error" role="alert">
          {errorGlobal}
        </div>
      )}

      <div className="barra-filtros">
        <input
          type="search"
          className="input-filtro"
          placeholder="Buscar por nombre de lote, suelo o cultivo..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />

        <select
          className="select-filtro"
          value={alcanceCiclo}
          onChange={(e) => {
            setCargando(true)
            setAlcanceCiclo(e.target.value)
          }}
          aria-label="Seleccionar alcance de ciclo productivo"
        >
          {OPCIONES_ALCANCE_CICLO.map((op) => (
            <option key={op.valor} value={op.valor}>
              {op.etiqueta}
            </option>
          ))}
        </select>

        <select
          className="select-filtro"
          value={estadoLoteFiltro}
          onChange={(e) => setEstadoLoteFiltro(e.target.value)}
          aria-label="Filtrar por estado agronómico del lote"
        >
          <option value="TODOS">Todos los estados de lote</option>
          {ESTADOS_LOTE.map((e) => (
            <option key={e.valor} value={e.valor}>
              {e.etiqueta}
            </option>
          ))}
        </select>

        <select
          className="select-filtro"
          value={estadoRoiFiltro}
          onChange={(e) => setEstadoRoiFiltro(e.target.value)}
          aria-label="Filtrar por estado de ROI"
        >
          {FILTROS_ESTADO_ROI.map((f) => (
            <option key={f.valor} value={f.valor}>
              {f.etiqueta}
            </option>
          ))}
        </select>

        <select
          className="select-filtro"
          value={ordenTabla}
          onChange={(e) => setOrdenTabla(e.target.value)}
          aria-label="Ordenar lotes"
        >
          {OPCIONES_ORDEN.map((op) => (
            <option key={op.valor} value={op.valor}>
              {op.etiqueta}
            </option>
          ))}
        </select>

        <label className="check-filtro" title="Fecha inicial del período">
          <span className="texto-chico texto-suave">Desde:</span>
          <input
            type="date"
            className="input-filtro"
            style={{ minWidth: '140px', padding: '0.35rem 0.5rem' }}
            value={fechaDesde}
            onChange={(e) => {
              const nuevoDesde = e.target.value
              if (nuevoDesde && fechaHasta && nuevoDesde > fechaHasta) {
                setErrorGlobal(
                  'La fecha inicial (Desde) no puede ser posterior a la fecha final (Hasta).',
                )
              } else {
                setCargando(true)
              }
              setFechaDesde(nuevoDesde)
            }}
          />
        </label>

        <label className="check-filtro" title="Fecha final del período">
          <span className="texto-chico texto-suave">Hasta:</span>
          <input
            type="date"
            className="input-filtro"
            style={{ minWidth: '140px', padding: '0.35rem 0.5rem' }}
            value={fechaHasta}
            onChange={(e) => {
              const nuevoHasta = e.target.value
              if (fechaDesde && nuevoHasta && fechaDesde > nuevoHasta) {
                setErrorGlobal(
                  'La fecha inicial (Desde) no puede ser posterior a la fecha final (Hasta).',
                )
              } else {
                setCargando(true)
              }
              setFechaHasta(nuevoHasta)
            }}
          />
        </label>

        <label className="check-filtro">
          <input
            type="checkbox"
            checked={soloConCosecha}
            onChange={(e) => setSoloConCosecha(e.target.checked)}
          />
          <span>Solo con cosecha ({tablero?.lotesConCosecha ?? 0})</span>
        </label>

        {hayFiltrosActivos && (
          <button
            type="button"
            className="btn btn--secundario btn--chico"
            onClick={limpiarFiltros}
          >
            Limpiar filtros
          </button>
        )}
      </div>

      {cargando ? (
        <p className="aviso">Calculando métricas financieras por lote...</p>
      ) : lotesFiltrados.length === 0 ? (
        <p className="aviso">
          No se encontraron lotes para los criterios seleccionados.
        </p>
      ) : (
        <>
          <div className="grafico-financiero">
            <div className="grafico-financiero__encabezado">
              <div>
                <h2 className="grafico-financiero__titulo">
                  Comparativa visual por lote: Ingresos Brutos vs. Costos
                  Acumulados
                </h2>
                <p className="texto-suave texto-chico" style={{ margin: 0 }}>
                  Escala relativa al mayor monto registrado ($
                  {' '}
                  {formatearNumero(maximoMontoEscala)}) · Modo:{' '}
                  <strong>
                    {alcanceCiclo === 'ULTIMO_CICLO'
                      ? 'Último ciclo productivo (RN-12)'
                      : 'Acumulado histórico'}
                  </strong>
                  . Hacé clic en «Desglose» para inspeccionar órdenes por tipo
                  de labor o simular punto de equilibrio.
                </p>
              </div>
              <div className="grafico-financiero__leyenda">
                <span className="leyenda-item">
                  <span className="leyenda-color leyenda-color--insumos" />
                  Costo insumos
                </span>
                <span className="leyenda-item">
                  <span className="leyenda-color leyenda-color--maquinaria" />
                  Costo maquinaria
                </span>
                <span className="leyenda-item">
                  <span className="leyenda-color leyenda-color--ingreso" />
                  Ingreso bruto cosecha
                </span>
              </div>
            </div>

            <div className="grafico-financiero__filas">
              {lotesFiltrados.map((item) => {
                const costoInsumos = Number(item.costoInsumosAcumulado || 0)
                const costoMaq = Number(item.costoMaquinariaAcumulado || 0)
                const costoTotal = Number(item.costosTotalesAcumulados || 0)
                const ingresoTotal = Number(item.ingresoBrutoTotal || 0)

                const pctInsumos = Math.min(
                  100,
                  (costoInsumos / maximoMontoEscala) * 100,
                )
                const pctMaq = Math.min(
                  100 - pctInsumos,
                  (costoMaq / maximoMontoEscala) * 100,
                )
                const pctIngreso = Math.min(
                  100,
                  (ingresoTotal / maximoMontoEscala) * 100,
                )

                return (
                  <div key={item.loteId} className="grafico-lote">
                    <div className="grafico-lote__cabecera">
                      <div>
                        <strong>{item.loteNombre}</strong>{' '}
                        <span className="texto-suave texto-chico">
                          ({formatearNumero(item.superficieHa)} ha ·{' '}
                          {etiquetaEstadoLote(item.estadoLote)})
                        </span>
                      </div>
                      <span className={claseEtiquetaEstadoRoi(item)}>
                        {item.estadoRoi === 'CALCULADO'
                          ? `ROI: ${formatearNumero(item.roiPct)} %`
                          : item.mensajeRoi}
                      </span>
                    </div>

                    <div className="grafico-lote__barras">
                      <div className="grafico-barra-fila">
                        <span className="grafico-barra-etiqueta">Costos</span>
                        <div className="grafico-barra-pista">
                          {costoTotal > 0 ? (
                            <>
                              <div
                                className="grafico-barra-segmento grafico-barra-segmento--insumos"
                                style={{ width: `${pctInsumos}%` }}
                                title={`Insumos: $ ${formatearNumero(costoInsumos)}`}
                              />
                              <div
                                className="grafico-barra-segmento grafico-barra-segmento--maquinaria"
                                style={{ width: `${pctMaq}%` }}
                                title={`Maquinaria: $ ${formatearNumero(costoMaq)}`}
                              />
                            </>
                          ) : null}
                        </div>
                        <span className="grafico-barra-valor">
                          $ {formatearNumero(costoTotal)}
                        </span>
                      </div>

                      <div className="grafico-barra-fila">
                        <span className="grafico-barra-etiqueta">Ingresos</span>
                        <div className="grafico-barra-pista">
                          {item.cosechaRegistrada && ingresoTotal > 0 ? (
                            <div
                              className="grafico-barra-segmento grafico-barra-segmento--ingreso"
                              style={{ width: `${pctIngreso}%` }}
                              title={`Ingreso bruto: $ ${formatearNumero(ingresoTotal)}`}
                            />
                          ) : null}
                        </div>
                        <span className="grafico-barra-valor">
                          {item.cosechaRegistrada ? (
                            `$ ${formatearNumero(ingresoTotal)}`
                          ) : (
                            <span className="texto-suave">Pendiente</span>
                          )}
                        </span>
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          <div className="tabla-contenedor">
            <table className="tabla">
              <thead>
                <tr>
                  <th>Lote</th>
                  <th>Estado / Cultivo</th>
                  <th className="texto-derecha">Labores</th>
                  <th className="texto-derecha">Costos acumulados</th>
                  <th className="texto-derecha">Costo / ha</th>
                  <th className="texto-derecha">Ingreso bruto</th>
                  <th className="texto-derecha">Margen neto</th>
                  <th>ROI (%)</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {lotesFiltrados.map((item) => (
                  <tr
                    key={item.loteId}
                    className={
                      item.estadoRoi === 'CALCULADO' && Number(item.roiPct) < 0
                        ? 'fila--critica'
                        : ''
                    }
                  >
                    <td>
                      <strong>{item.loteNombre}</strong>
                      <div className="texto-suave texto-chico">
                        {formatearNumero(item.superficieHa)} ha · Suelo:{' '}
                        {item.tipoSuelo || '-'}
                      </div>
                    </td>
                    <td>
                      <span
                        className={`etiqueta ${
                          item.estadoLote === 'COSECHADO' ||
                          item.estadoLote === 'LISTO_COSECHA'
                            ? 'etiqueta--ok'
                            : ''
                        }`}
                      >
                        {etiquetaEstadoLote(item.estadoLote)}
                      </span>
                      {item.cosechaRegistrada ? (
                        <div className="texto-suave texto-chico">
                          {item.cultivoNombre} ·{' '}
                          {formatearFechaSimple(item.fechaUltimaCosecha)}
                        </div>
                      ) : (
                        <div className="texto-suave texto-chico">
                          Sin cosecha registrada
                        </div>
                      )}
                    </td>
                    <td className="texto-derecha">
                      <strong>{item.cantidadLaboresCompletadas}</strong>{' '}
                      <span className="texto-suave texto-chico">compl.</span>
                      <div className="texto-suave texto-chico">
                        {formatearNumero(item.horasMaquinaTotales)} hs máq.
                        {item.cantidadLaboresPendientes > 0
                          ? ` · ${item.cantidadLaboresPendientes} pend.`
                          : ''}
                      </div>
                    </td>
                    <td className="texto-derecha">
                      <strong>
                        $ {formatearNumero(item.costosTotalesAcumulados)}
                      </strong>
                      <div className="texto-suave texto-chico">
                        Ins.: $ {formatearNumero(item.costoInsumosAcumulado)} ·
                        Máq.: $ {formatearNumero(item.costoMaquinariaAcumulado)}
                      </div>
                    </td>
                    <td className="texto-derecha">
                      <strong>$ {formatearNumero(item.costoPorHa)}</strong>
                      <div className="texto-suave texto-chico">por ha</div>
                    </td>
                    <td className="texto-derecha">
                      {item.cosechaRegistrada ? (
                        <>
                          <strong>
                            $ {formatearNumero(item.ingresoBrutoTotal)}
                          </strong>
                          <div className="texto-suave texto-chico">
                            {formatearNumero(item.toneladasTotales)} t (
                            {formatearNumero(item.rindeTonPorHa)} t/ha)
                          </div>
                        </>
                      ) : (
                        <span className="texto-suave">
                          Pendiente de cosecha
                        </span>
                      )}
                    </td>
                    <td className="texto-derecha">
                      {item.cosechaRegistrada ? (
                        <>
                          <strong
                            className={
                              Number(item.margenNeto) >= 0
                                ? 'texto-exito'
                                : 'texto-peligro'
                            }
                          >
                            $ {formatearNumero(item.margenNeto)}
                          </strong>
                          <div className="texto-suave texto-chico">
                            $ {formatearNumero(item.margenPorHa)} / ha
                          </div>
                        </>
                      ) : (
                        <span className="texto-suave">
                          Pendiente de cosecha
                        </span>
                      )}
                    </td>
                    <td>
                      <span className={claseEtiquetaEstadoRoi(item)}>
                        {item.estadoRoi === 'CALCULADO'
                          ? `${formatearNumero(item.roiPct)} %`
                          : item.mensajeRoi}
                      </span>
                    </td>
                    <td>
                      <div className="acciones-celda">
                        <button
                          type="button"
                          className="btn btn--secundario btn--chico"
                          onClick={() => setLoteDetalle(item)}
                        >
                          Desglose
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {loteDetalle && (
        <DetalleFinancieroLoteModal
          reporte={loteDetalle}
          onCerrar={() => setLoteDetalle(null)}
        />
      )}
    </section>
  )
}

export default TableroFinanciero
