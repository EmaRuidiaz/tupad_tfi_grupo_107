import { useCallback, useEffect, useMemo, useState } from 'react'
import CosechaFormModal from '../components/cosechas/CosechaFormModal.jsx'
import { getSession, tieneAlgunRol } from '../services/api.js'
import { listarCosechas, listarCultivos } from '../services/cosechasService.js'
import { listarLotes } from '../services/lotesService.js'
import { formatearFechaSimple, formatearNumero } from '../utils/formatters.js'

// HU-12: registro de los rendimientos obtenidos por lote (CU-05).
function Cosechas() {
  const sesion = getSession()
  const puedeRegistrar = tieneAlgunRol('ADMIN', 'AGRONOMO')

  const [cosechas, setCosechas] = useState([])
  const [lotes, setLotes] = useState([])
  const [cultivos, setCultivos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')

  const [loteFiltro, setLoteFiltro] = useState('TODOS')
  const [busqueda, setBusqueda] = useState('')
  const [modalAbierto, setModalAbierto] = useState(null)

  const cargarDatos = useCallback(async () => {
    const [listaCosechas, listaLotes, listaCultivos] = await Promise.all([
      listarCosechas(),
      listarLotes(),
      listarCultivos(),
    ])
    return { listaCosechas, listaLotes, listaCultivos }
  }, [])

  const recargar = useCallback(async () => {
    setErrorGlobal('')
    try {
      const { listaCosechas, listaLotes, listaCultivos } = await cargarDatos()
      setCosechas(listaCosechas)
      setLotes(listaLotes)
      setCultivos(listaCultivos)
    } catch (err) {
      setErrorGlobal(err.message || 'No se pudieron cargar las cosechas.')
    } finally {
      setCargando(false)
    }
  }, [cargarDatos])

  useEffect(() => {
    let cancelado = false
    cargarDatos()
      .then(({ listaCosechas, listaLotes, listaCultivos }) => {
        if (cancelado) return
        setCosechas(listaCosechas)
        setLotes(listaLotes)
        setCultivos(listaCultivos)
      })
      .catch((err) => {
        if (!cancelado) setErrorGlobal(err.message || 'No se pudieron cargar las cosechas.')
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
  }, [cargarDatos])

  const lotesListos = useMemo(
    () => lotes.filter((l) => l.estado === 'LISTO_COSECHA'),
    [lotes],
  )

  const cosechasFiltradas = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    return cosechas.filter((c) => {
      if (loteFiltro !== 'TODOS' && String(c.loteId) !== loteFiltro) return false
      if (
        texto &&
        !c.cultivoNombre.toLowerCase().includes(texto) &&
        !c.loteNombre.toLowerCase().includes(texto)
      ) {
        return false
      }
      return true
    })
  }, [cosechas, loteFiltro, busqueda])

  // Los KPIs reflejan lo que se ve en la tabla (respetan los filtros).
  const resumen = useMemo(() => {
    const toneladas = cosechasFiltradas.reduce((s, c) => s + Number(c.toneladasTotales || 0), 0)
    const superficie = cosechasFiltradas.reduce((s, c) => s + Number(c.superficieHa || 0), 0)
    const ingreso = cosechasFiltradas.reduce((s, c) => s + Number(c.ingresoBrutoTotal || 0), 0)
    const margen = cosechasFiltradas.reduce((s, c) => s + Number(c.margenNetoCalculado || 0), 0)
    return {
      cantidad: cosechasFiltradas.length,
      toneladas,
      rindePromedio: superficie > 0 ? toneladas / superficie : 0,
      ingreso,
      margen,
    }
  }, [cosechasFiltradas])

  // Lotes que tienen al menos una cosecha, para el filtro.
  const lotesConCosecha = useMemo(() => {
    const vistos = new Map()
    cosechas.forEach((c) => vistos.set(c.loteId, c.loteNombre))
    return [...vistos.entries()].sort((a, b) => a[1].localeCompare(b[1]))
  }, [cosechas])

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Registro de Cosechas</h1>
          <p className="pagina__subtitulo">
            Cierre de cada ciclo productivo: toneladas recolectadas, rinde por hectárea, ingreso bruto
            y margen neto del lote.
          </p>
        </div>

        <div className="pagina__acciones">
          {puedeRegistrar ? (
            <button
              type="button"
              className="btn btn--primario"
              disabled={lotesListos.length === 0}
              title={
                lotesListos.length === 0
                  ? 'No hay lotes en estado «Listo para cosecha»'
                  : undefined
              }
              onClick={() => {
                setMensajeExito('')
                setModalAbierto({ loteId: null })
              }}
            >
              + Registrar cosecha
            </button>
          ) : (
            <span className="etiqueta etiqueta--info">Modo consulta ({sesion?.rol})</span>
          )}
        </div>
      </div>

      <div className="tarjetas-resumen">
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Cosechas registradas</span>
          <strong className="tarjeta-kpi__valor">{resumen.cantidad}</strong>
          <span className="tarjeta-kpi__detalle">
            {formatearNumero(resumen.toneladas)} t recolectadas
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Rinde promedio</span>
          <strong className="tarjeta-kpi__valor">{formatearNumero(resumen.rindePromedio)}</strong>
          <span className="tarjeta-kpi__detalle">Toneladas por hectárea</span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Ingreso bruto</span>
          <strong className="tarjeta-kpi__valor">$ {formatearNumero(resumen.ingreso)}</strong>
          <span className={`tarjeta-kpi__detalle ${resumen.margen < 0 ? 'texto-peligro' : ''}`}>
            Margen neto: $ {formatearNumero(resumen.margen)}
          </span>
        </div>

        <div className={`tarjeta-kpi ${lotesListos.length > 0 ? 'tarjeta-kpi--alerta' : ''}`}>
          <span className="tarjeta-kpi__titulo">Listos para cosecha</span>
          <strong className="tarjeta-kpi__valor">{lotesListos.length}</strong>
          <span className="tarjeta-kpi__detalle">Lotes pendientes de registrar</span>
        </div>
      </div>

      {errorGlobal && (
        <div className="alerta alerta--error" role="alert">
          {errorGlobal}
        </div>
      )}

      {mensajeExito && (
        <div className="alerta alerta--exito" role="status">
          {mensajeExito}
        </div>
      )}

      {puedeRegistrar && lotesListos.length > 0 && (
        <div className="alerta alerta--advertencia">
          Lotes listos para cosechar:{' '}
          {lotesListos.map((lote, i) => (
            <span key={lote.id}>
              {i > 0 && ', '}
              <button
                type="button"
                className="btn btn--secundario btn--chico"
                onClick={() => {
                  setMensajeExito('')
                  setModalAbierto({ loteId: lote.id })
                }}
              >
                {lote.nombre}
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="barra-filtros">
        <input
          type="search"
          className="input-filtro"
          placeholder="Buscar por lote o cultivo..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />

        <select
          className="select-filtro"
          value={loteFiltro}
          onChange={(e) => setLoteFiltro(e.target.value)}
          aria-label="Filtrar por lote"
        >
          <option value="TODOS">Todos los lotes</option>
          {lotesConCosecha.map(([id, nombre]) => (
            <option key={id} value={String(id)}>
              {nombre}
            </option>
          ))}
        </select>
      </div>

      {cargando ? (
        <p className="aviso">Cargando cosechas...</p>
      ) : cosechasFiltradas.length === 0 ? (
        <p className="aviso">
          {cosechas.length === 0
            ? 'Todavía no hay cosechas registradas.'
            : 'No se encontraron cosechas con los filtros seleccionados.'}
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Lote</th>
                <th>Cultivo</th>
                <th className="texto-derecha">Toneladas</th>
                <th className="texto-derecha">Rinde (t/ha)</th>
                <th className="texto-derecha">Precio ($/t)</th>
                <th className="texto-derecha">Ingreso bruto</th>
                <th className="texto-derecha">Costos del ciclo</th>
                <th className="texto-derecha">Margen neto</th>
                <th className="texto-derecha">ROI</th>
                <th>Responsable</th>
              </tr>
            </thead>
            <tbody>
              {cosechasFiltradas.map((c) => (
                <tr key={c.id} className={Number(c.margenNetoCalculado) < 0 ? 'fila--alerta' : ''}>
                  <td>{formatearFechaSimple(c.fechaCosecha)}</td>
                  <td>
                    <strong>{c.loteNombre}</strong>
                    <div className="texto-suave texto-chico">
                      {formatearNumero(c.superficieHa)} ha
                      {c.laborId ? ` · Trilla #${c.laborId}` : ''}
                    </div>
                  </td>
                  <td>
                    {c.cultivoNombre}
                    <div className="texto-suave texto-chico">{c.cultivoEspecie}</div>
                  </td>
                  <td className="texto-derecha">{formatearNumero(c.toneladasTotales)}</td>
                  <td className="texto-derecha">
                    <strong>{formatearNumero(c.rindeTonPorHa)}</strong>
                  </td>
                  <td className="texto-derecha">$ {formatearNumero(c.precioVentaPorTon)}</td>
                  <td className="texto-derecha">$ {formatearNumero(c.ingresoBrutoTotal)}</td>
                  <td className="texto-derecha">$ {formatearNumero(c.costosCicloImputados)}</td>
                  <td
                    className={`texto-derecha ${Number(c.margenNetoCalculado) < 0 ? 'texto-peligro' : 'texto-exito'}`}
                  >
                    <strong>$ {formatearNumero(c.margenNetoCalculado)}</strong>
                  </td>
                  <td className="texto-derecha">
                    {c.roiPct !== null && c.roiPct !== undefined ? (
                      `${formatearNumero(c.roiPct)} %`
                    ) : (
                      <span className="texto-suave texto-chico">Sin costos</span>
                    )}
                  </td>
                  <td>{c.usuarioUsername || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalAbierto && (
        <CosechaFormModal
          lotesListos={lotesListos}
          cultivos={cultivos}
          loteInicialId={modalAbierto.loteId}
          onCerrar={() => setModalAbierto(null)}
          onGuardado={async (mensaje) => {
            setModalAbierto(null)
            setMensajeExito(mensaje)
            await recargar()
          }}
        />
      )}
    </section>
  )
}

export default Cosechas
