import { useCallback, useEffect, useMemo, useState } from 'react'
import ConfirmModal from '../components/ConfirmModal.jsx'
import CambioEstadoLoteModal from '../components/lotes/CambioEstadoLoteModal.jsx'
import LoteFormModal from '../components/lotes/LoteFormModal.jsx'
import { getSession, tieneAlgunRol } from '../services/api.js'
import { eliminarLote, listarLotes } from '../services/lotesService.js'
import {
  ESTADOS_LOTE,
  etiquetaEstadoLote,
  formatearNumero,
} from '../utils/formatters.js'

function Lotes() {
  const sesion = getSession()
  const puedeEditar = tieneAlgunRol('ADMIN', 'AGRONOMO')

  const [lotes, setLotes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')

  const [busqueda, setBusqueda] = useState('')
  const [estadoFiltro, setEstadoFiltro] = useState('TODOS')

  const [modalFormulario, setModalFormulario] = useState(null)
  const [loteCambioEstado, setLoteCambioEstado] = useState(null)
  const [loteAEliminar, setLoteAEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)
  const [errorEliminacion, setErrorEliminacion] = useState('')

  const recargarLotes = useCallback(async () => {
    setErrorGlobal('')
    try {
      const datos = await listarLotes()
      setLotes(datos)
    } catch (err) {
      setErrorGlobal(err.message || 'No se pudieron cargar los lotes.')
    } finally {
      setCargando(false)
    }
  }, [])

  useEffect(() => {
    let cancelado = false
    listarLotes()
      .then((datos) => {
        if (!cancelado) setLotes(datos)
      })
      .catch((err) => {
        if (!cancelado) {
          setErrorGlobal(err.message || 'No se pudieron cargar los lotes.')
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
    const total = lotes.length
    const superficie = lotes.reduce(
      (suma, l) => suma + Number(l.superficieHa || 0),
      0,
    )
    const listosCosecha = lotes.filter(
      (l) => l.estado === 'LISTO_COSECHA',
    ).length
    return { total, superficie, listosCosecha }
  }, [lotes])

  const lotesFiltrados = useMemo(() => {
    return lotes.filter((lote) => {
      if (estadoFiltro !== 'TODOS' && lote.estado !== estadoFiltro) {
        return false
      }
      if (
        busqueda.trim() &&
        !lote.nombre.toLowerCase().includes(busqueda.trim().toLowerCase())
      ) {
        return false
      }
      return true
    })
  }, [lotes, estadoFiltro, busqueda])

  async function confirmarEliminar() {
    if (!loteAEliminar) return
    setEliminando(true)
    setErrorEliminacion('')
    try {
      await eliminarLote(loteAEliminar.id)
      const nombre = loteAEliminar.nombre
      setLoteAEliminar(null)
      setMensajeExito(`Lote "${nombre}" eliminado.`)
      await recargarLotes()
    } catch (err) {
      setErrorEliminacion(err.message || 'No se pudo eliminar el lote.')
    } finally {
      setEliminando(false)
    }
  }

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Gestión de Lotes</h1>
          <p className="pagina__subtitulo">
            Catastro digital de la superficie sembrable: ubicación, superficie
            y estado agronómico de cada lote.
          </p>
        </div>

        <div className="pagina__acciones">
          {puedeEditar ? (
            <button
              type="button"
              className="btn btn--primario"
              onClick={() => {
                setMensajeExito('')
                setModalFormulario({ lote: null })
              }}
            >
              + Nuevo lote
            </button>
          ) : (
            <span className="etiqueta etiqueta--info">
              Modo consulta ({sesion?.rol})
            </span>
          )}
        </div>
      </div>

      <div className="tarjetas-resumen">
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Lotes registrados</span>
          <strong className="tarjeta-kpi__valor">{resumen.total}</strong>
          <span className="tarjeta-kpi__detalle">En el catastro</span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Superficie total</span>
          <strong className="tarjeta-kpi__valor">
            {formatearNumero(resumen.superficie)}
          </strong>
          <span className="tarjeta-kpi__detalle">Hectáreas</span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Listos para cosecha</span>
          <strong className="tarjeta-kpi__valor">
            {resumen.listosCosecha}
          </strong>
          <span className="tarjeta-kpi__detalle">
            Cultivo en madurez comercial
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

      <div className="barra-filtros">
        <input
          type="search"
          className="input-filtro"
          placeholder="Buscar lote por nombre..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />

        <select
          className="select-filtro"
          value={estadoFiltro}
          onChange={(e) => setEstadoFiltro(e.target.value)}
          aria-label="Filtrar por estado"
        >
          <option value="TODOS">Todos los estados</option>
          {ESTADOS_LOTE.map((e) => (
            <option key={e.valor} value={e.valor}>
              {e.etiqueta}
            </option>
          ))}
        </select>
      </div>

      {cargando ? (
        <p className="aviso">Cargando lotes...</p>
      ) : lotesFiltrados.length === 0 ? (
        <p className="aviso">
          No se encontraron lotes con los filtros seleccionados.
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Lote</th>
                <th className="texto-derecha">Superficie (ha)</th>
                <th>Coordenadas</th>
                <th>Estado</th>
                <th>Responsable</th>
                {puedeEditar && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {lotesFiltrados.map((lote) => (
                <tr key={lote.id}>
                  <td>
                    <strong>{lote.nombre}</strong>
                    <div className="texto-suave texto-chico">
                      Suelo: {lote.tipoSuelo || '-'}
                      {lote.poligonoWkt ? ' · Con polígono' : ''}
                    </div>
                  </td>
                  <td className="texto-derecha">
                    <strong>{formatearNumero(lote.superficieHa)}</strong>
                  </td>
                  <td className="texto-chico">
                    {lote.latitud}, {lote.longitud}
                  </td>
                  <td>
                    <span
                      className={`etiqueta ${lote.estado === 'LISTO_COSECHA' ? 'etiqueta--ok' : ''}`}
                    >
                      {etiquetaEstadoLote(lote.estado)}
                    </span>
                  </td>
                  <td>{lote.usuarioUsername || '-'}</td>
                  {puedeEditar && (
                    <td>
                      <div className="acciones-celda">
                        <button
                          type="button"
                          className="btn btn--secundario btn--chico"
                          onClick={() => {
                            setMensajeExito('')
                            setLoteCambioEstado(lote)
                          }}
                        >
                          Estado
                        </button>
                        <button
                          type="button"
                          className="btn btn--secundario btn--chico"
                          onClick={() => {
                            setMensajeExito('')
                            setModalFormulario({ lote })
                          }}
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          className="btn btn--peligro btn--chico"
                          onClick={() => {
                            setMensajeExito('')
                            setErrorEliminacion('')
                            setLoteAEliminar(lote)
                          }}
                        >
                          Eliminar
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalFormulario && (
        <LoteFormModal
          lote={modalFormulario.lote}
          onCerrar={() => setModalFormulario(null)}
          onGuardado={async (mensaje) => {
            setModalFormulario(null)
            setMensajeExito(mensaje)
            await recargarLotes()
          }}
        />
      )}

      {loteCambioEstado && (
        <CambioEstadoLoteModal
          lote={loteCambioEstado}
          onCerrar={() => setLoteCambioEstado(null)}
          onGuardado={async (mensaje) => {
            setLoteCambioEstado(null)
            setMensajeExito(mensaje)
            await recargarLotes()
          }}
        />
      )}

      <ConfirmModal
        abierto={Boolean(loteAEliminar)}
        titulo="Eliminar lote"
        mensaje={
          loteAEliminar
            ? `¿Confirmás la eliminación del lote "${loteAEliminar.nombre}"?`
            : ''
        }
        detalle="Solo se permite eliminar lotes sin labores ni cosechas registradas."
        cargando={eliminando}
        error={errorEliminacion}
        onCancelar={() => {
          if (!eliminando) {
            setLoteAEliminar(null)
            setErrorEliminacion('')
          }
        }}
        onConfirmar={confirmarEliminar}
      />
    </section>
  )
}

export default Lotes
