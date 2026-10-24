import { useCallback, useEffect, useMemo, useState } from 'react'
import AjusteStockModal from '../components/insumos/AjusteStockModal.jsx'
import HistorialMovimientosModal from '../components/insumos/HistorialMovimientosModal.jsx'
import InsumoFormModal from '../components/insumos/InsumoFormModal.jsx'
import { getSession, isAdmin } from '../services/api.js'
import { eliminarInsumo, listarInsumos } from '../services/insumosService.js'
import { CATEGORIAS_INSUMO, formatearNumero } from '../utils/formatters.js'

function Insumos() {
  const sesion = getSession()
  const puedeEditar = isAdmin()

  const [insumos, setInsumos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')

  // Filtros
  const [busqueda, setBusqueda] = useState('')
  const [categoriaFiltro, setCategoriaFiltro] = useState('TODAS')
  const [soloCriticos, setSoloCriticos] = useState(false)

  // Modales activos (null = cerrado)
  const [modalFormulario, setModalFormulario] = useState(null) // { insumo: null | Insumo }
  const [insumoAjuste, setInsumoAjuste] = useState(null)
  const [insumoHistorial, setInsumoHistorial] = useState(null)

  const recargarInsumos = useCallback(async () => {
    setErrorGlobal('')
    try {
      const datos = await listarInsumos(false)
      setInsumos(datos)
    } catch (err) {
      setErrorGlobal(
        err.message || 'No se pudo cargar el inventario de insumos.',
      )
    } finally {
      setCargando(false)
    }
  }, [])

  useEffect(() => {
    let cancelado = false
    listarInsumos(false)
      .then((datos) => {
        if (!cancelado) setInsumos(datos)
      })
      .catch((err) => {
        if (!cancelado) {
          setErrorGlobal(
            err.message || 'No se pudo cargar el inventario de insumos.',
          )
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
  }, [])

  const resumen = useMemo(() => {
    const total = insumos.length
    const criticos = insumos.filter((i) => i.stockCritico).length
    const conReserva = insumos.filter((i) => Number(i.stockReservado) > 0).length
    return { total, criticos, conReserva }
  }, [insumos])

  const insumosFiltrados = useMemo(() => {
    return insumos.filter((item) => {
      if (soloCriticos && !item.stockCritico) return false
      if (categoriaFiltro !== 'TODAS' && item.categoria !== categoriaFiltro) {
        return false
      }
      if (
        busqueda.trim() &&
        !item.nombre.toLowerCase().includes(busqueda.trim().toLowerCase())
      ) {
        return false
      }
      return true
    })
  }, [insumos, soloCriticos, categoriaFiltro, busqueda])

  async function manejarEliminar(insumo) {
    const confirmado = window.confirm(
      `¿Eliminar el insumo "${insumo.nombre}"? Sólo se permite si no tiene stock ni movimientos registrados.`,
    )
    if (!confirmado) return

    setErrorGlobal('')
    setMensajeExito('')
    try {
      await eliminarInsumo(insumo.id)
      setMensajeExito(`Insumo "${insumo.nombre}" eliminado.`)
      await recargarInsumos()
    } catch (err) {
      setErrorGlobal(err.message || 'No se pudo eliminar el insumo.')
    }
  }

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Inventario de Insumos</h1>
          <p className="pagina__subtitulo">
            Control de existencias de semillas, fertilizantes, fitosanitarios y
            combustible, con alertas automáticas de stock mínimo.
          </p>
        </div>

        <div className="pagina__acciones">
          {puedeEditar ? (
            <button
              type="button"
              className="btn btn--primario"
              onClick={() => {
                setMensajeExito('')
                setModalFormulario({ insumo: null })
              }}
            >
              + Nuevo insumo
            </button>
          ) : (
            <span className="etiqueta etiqueta--info">
              Modo consulta ({sesion?.rol})
            </span>
          )}
        </div>
      </div>

      {/* Tarjetas de resumen */}
      <div className="tarjetas-resumen">
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Total en catálogo</span>
          <strong className="tarjeta-kpi__valor">{resumen.total}</strong>
          <span className="tarjeta-kpi__detalle">Insumos registrados</span>
        </div>

        <div
          className={`tarjeta-kpi ${resumen.criticos > 0 ? 'tarjeta-kpi--alerta' : ''}`}
        >
          <span className="tarjeta-kpi__titulo">En stock crítico</span>
          <strong className="tarjeta-kpi__valor">{resumen.criticos}</strong>
          <span className="tarjeta-kpi__detalle">
            Igual o por debajo del mínimo de alerta
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Con reserva activa</span>
          <strong className="tarjeta-kpi__valor">{resumen.conReserva}</strong>
          <span className="tarjeta-kpi__detalle">
            Comprometidos en labores planificadas
          </span>
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

      {/* Barra de filtros */}
      <div className="barra-filtros">
        <input
          type="search"
          className="input-filtro"
          placeholder="Buscar insumo por nombre..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />

        <select
          className="select-filtro"
          value={categoriaFiltro}
          onChange={(e) => setCategoriaFiltro(e.target.value)}
          aria-label="Filtrar por categoría"
        >
          <option value="TODAS">Todas las categorías</option>
          {CATEGORIAS_INSUMO.map((c) => (
            <option key={c.valor} value={c.valor}>
              {c.etiqueta}
            </option>
          ))}
        </select>

        <label className="check-filtro">
          <input
            type="checkbox"
            checked={soloCriticos}
            onChange={(e) => setSoloCriticos(e.target.checked)}
          />
          <span>Ver sólo stock crítico ({resumen.criticos})</span>
        </label>
      </div>

      {/* Tabla principal */}
      {cargando ? (
        <p className="aviso">Cargando inventario de insumos...</p>
      ) : insumosFiltrados.length === 0 ? (
        <p className="aviso">
          No se encontraron insumos con los filtros seleccionados.
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Insumo</th>
                <th>Categoría</th>
                <th className="texto-derecha">Stock actual</th>
                <th className="texto-derecha">Reservado</th>
                <th className="texto-derecha">Disponible</th>
                <th className="texto-derecha">Mín. alerta</th>
                <th className="texto-derecha">Precio unit.</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {insumosFiltrados.map((item) => (
                <tr
                  key={item.id}
                  className={item.stockCritico ? 'fila--critica' : ''}
                >
                  <td>
                    <strong>{item.nombre}</strong>
                    <div className="texto-suave texto-chico">
                      Unidad: {item.unidadMedida}
                    </div>
                  </td>
                  <td>
                    <span className="etiqueta">{item.categoria}</span>
                  </td>
                  <td className="texto-derecha">
                    <strong>{formatearNumero(item.stockActual)}</strong>
                  </td>
                  <td className="texto-derecha">
                    {formatearNumero(item.stockReservado)}
                  </td>
                  <td className="texto-derecha">
                    {formatearNumero(item.stockDisponible)}
                  </td>
                  <td className="texto-derecha">
                    {formatearNumero(item.stockMinimoAlerta)}
                  </td>
                  <td className="texto-derecha">
                    $ {formatearNumero(item.precioUnitario)}
                  </td>
                  <td>
                    {item.stockCritico ? (
                      <span
                        className="etiqueta etiqueta--critica"
                        title="El stock actual es menor o igual al stock mínimo de alerta"
                      >
                        ⚠️ Stock crítico
                      </span>
                    ) : (
                      <span className="etiqueta etiqueta--ok">✓ Normal</span>
                    )}
                  </td>
                  <td>
                    <div className="acciones-celda">
                      <button
                        type="button"
                        className="btn btn--secundario btn--chico"
                        onClick={() => setInsumoHistorial(item)}
                      >
                        Historial
                      </button>
                      {puedeEditar && (
                        <>
                          <button
                            type="button"
                            className="btn btn--secundario btn--chico"
                            onClick={() => {
                              setMensajeExito('')
                              setInsumoAjuste(item)
                            }}
                          >
                            Ajustar
                          </button>
                          <button
                            type="button"
                            className="btn btn--secundario btn--chico"
                            onClick={() => {
                              setMensajeExito('')
                              setModalFormulario({ insumo: item })
                            }}
                          >
                            Editar
                          </button>
                          <button
                            type="button"
                            className="btn btn--peligro btn--chico"
                            onClick={() => manejarEliminar(item)}
                          >
                            Eliminar
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalFormulario && (
        <InsumoFormModal
          insumo={modalFormulario.insumo}
          onCerrar={() => setModalFormulario(null)}
          onGuardado={async (mensaje) => {
            setModalFormulario(null)
            setMensajeExito(mensaje)
            await recargarInsumos()
          }}
        />
      )}

      {insumoAjuste && (
        <AjusteStockModal
          insumo={insumoAjuste}
          onCerrar={() => setInsumoAjuste(null)}
          onGuardado={async (mensaje) => {
            setInsumoAjuste(null)
            setMensajeExito(mensaje)
            await recargarInsumos()
          }}
        />
      )}

      {insumoHistorial && (
        <HistorialMovimientosModal
          insumo={insumoHistorial}
          onCerrar={() => setInsumoHistorial(null)}
        />
      )}
    </section>
  )
}

export default Insumos
