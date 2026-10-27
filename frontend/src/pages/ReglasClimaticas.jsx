import { useCallback, useEffect, useMemo, useState } from 'react'
import ConfirmModal from '../components/ConfirmModal.jsx'
import ReglaClimaticaFormModal from '../components/reglas/ReglaClimaticaFormModal.jsx'
import SimuladorClimaModal from '../components/reglas/SimuladorClimaModal.jsx'
import { getSession, isAdmin } from '../services/api.js'
import {
  eliminarReglaClimatica,
  listarReglasClimaticas,
} from '../services/reglasClimaticasService.js'
import {
  TIPOS_LABOR_REGLA,
  etiquetaTipoLabor,
  formatearNumero,
} from '../utils/formatters.js'

function ReglasClimaticas() {
  const sesion = getSession()
  const puedeEditar = isAdmin()

  const [reglas, setReglas] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')

  const [busqueda, setBusqueda] = useState('')
  const [tipoFiltro, setTipoFiltro] = useState('TODOS')

  const [modalFormulario, setModalFormulario] = useState(null)
  const [reglaASimular, setReglaASimular] = useState(null)
  const [reglaAEliminar, setReglaAEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)
  const [errorEliminacion, setErrorEliminacion] = useState('')

  const recargarReglas = useCallback(async () => {
    setErrorGlobal('')
    try {
      const datos = await listarReglasClimaticas()
      setReglas(datos)
    } catch (err) {
      setErrorGlobal(
        err.message || 'No se pudieron cargar las reglas climáticas.',
      )
    } finally {
      setCargando(false)
    }
  }, [])

  useEffect(() => {
    let cancelado = false
    listarReglasClimaticas()
      .then((datos) => {
        if (!cancelado) setReglas(datos)
      })
      .catch((err) => {
        if (!cancelado) {
          setErrorGlobal(
            err.message || 'No se pudieron cargar las reglas climáticas.',
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

  const tiposDisponibles = useMemo(() => {
    const configurados = new Set(reglas.map((r) => r.tipoLabor))
    return TIPOS_LABOR_REGLA.filter((t) => !configurados.has(t.valor))
  }, [reglas])

  const resumen = useMemo(() => {
    const total = reglas.length
    const totalEsperado = TIPOS_LABOR_REGLA.length
    const faltantes = tiposDisponibles.length
    const menorVientoMax =
      reglas.length > 0
        ? Math.min(...reglas.map((r) => Number(r.vientoMaxKmh)))
        : null
    return { total, totalEsperado, faltantes, menorVientoMax }
  }, [reglas, tiposDisponibles])

  const reglasFiltradas = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    return reglas.filter((item) => {
      if (tipoFiltro !== 'TODOS' && item.tipoLabor !== tipoFiltro) return false
      if (
        texto &&
        !etiquetaTipoLabor(item.tipoLabor).toLowerCase().includes(texto) &&
        !item.mensajeAlerta.toLowerCase().includes(texto)
      ) {
        return false
      }
      return true
    })
  }, [reglas, tipoFiltro, busqueda])

  async function confirmarEliminar() {
    if (!reglaAEliminar) return
    setEliminando(true)
    setErrorEliminacion('')
    try {
      await eliminarReglaClimatica(reglaAEliminar.id)
      const etiqueta = etiquetaTipoLabor(reglaAEliminar.tipoLabor)
      setReglaAEliminar(null)
      setMensajeExito(`Regla climática de "${etiqueta}" eliminada.`)
      await recargarReglas()
    } catch (err) {
      setErrorEliminacion(
        err.message || 'No se pudo eliminar la regla climática.',
      )
    } finally {
      setEliminando(false)
    }
  }

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Motor de Reglas Agronómicas</h1>
          <p className="pagina__subtitulo">
            Umbrales meteorológicos de viento, temperatura, lluvia y humedad
            relativa que el asistente climático valida antes de autorizar una
            labor en campo.
          </p>
        </div>

        <div className="pagina__acciones">
          {puedeEditar ? (
            <button
              type="button"
              className="btn btn--primario"
              disabled={tiposDisponibles.length === 0}
              title={
                tiposDisponibles.length === 0
                  ? 'Los 4 tipos de labor con validación climática ya tienen su regla configurada'
                  : 'Agregar regla para un tipo de labor pendiente'
              }
              onClick={() => {
                setMensajeExito('')
                setModalFormulario({ regla: null })
              }}
            >
              + Nueva regla
            </button>
          ) : (
            <span className="etiqueta etiqueta--info">
              Modo consulta y simulación ({sesion?.rol})
            </span>
          )}
        </div>
      </div>

      <div className="tarjetas-resumen">
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Reglas configuradas</span>
          <strong className="tarjeta-kpi__valor">
            {resumen.total} / {resumen.totalEsperado}
          </strong>
          <span className="tarjeta-kpi__detalle">
            Tipos de labor con validación climática
          </span>
        </div>

        <div
          className={`tarjeta-kpi ${
            resumen.faltantes > 0 ? 'tarjeta-kpi--info' : ''
          }`}
        >
          <span className="tarjeta-kpi__titulo">Cobertura agronómica</span>
          <strong className="tarjeta-kpi__valor">
            {resumen.faltantes === 0
              ? 'Completa'
              : `${resumen.faltantes} pendiente${resumen.faltantes > 1 ? 's' : ''}`}
          </strong>
          <span className="tarjeta-kpi__detalle">
            Labranza no requiere validación climática
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Umbral más estricto</span>
          <strong className="tarjeta-kpi__valor">
            {resumen.menorVientoMax !== null
              ? `${formatearNumero(resumen.menorVientoMax)} km/h`
              : '-'}
          </strong>
          <span className="tarjeta-kpi__detalle">
            Viento máximo menor en el catálogo
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
          placeholder="Buscar por tipo de labor o mensaje de alerta..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />

        <select
          className="select-filtro"
          value={tipoFiltro}
          onChange={(e) => setTipoFiltro(e.target.value)}
          aria-label="Filtrar por tipo de labor"
        >
          <option value="TODOS">Todos los tipos de labor</option>
          {TIPOS_LABOR_REGLA.map((t) => (
            <option key={t.valor} value={t.valor}>
              {t.etiqueta}
            </option>
          ))}
        </select>
      </div>

      {cargando ? (
        <p className="aviso">Cargando reglas climáticas...</p>
      ) : reglasFiltradas.length === 0 ? (
        <p className="aviso">
          No se encontraron reglas climáticas con los filtros seleccionados.
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Tipo de labor</th>
                <th className="texto-derecha">Viento (km/h)</th>
                <th className="texto-derecha">Temperatura (°C)</th>
                <th className="texto-derecha">Lluvia máx.</th>
                <th className="texto-derecha">Humedad mín.</th>
                <th>Mensaje de alerta</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {reglasFiltradas.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{etiquetaTipoLabor(item.tipoLabor)}</strong>
                    <div className="texto-suave texto-chico">
                      Código: {item.tipoLabor}
                    </div>
                  </td>
                  <td className="texto-derecha">
                    <strong>
                      {formatearNumero(item.vientoMinKmh)} a{' '}
                      {formatearNumero(item.vientoMaxKmh)}
                    </strong>
                    <div className="texto-suave texto-chico">km/h</div>
                  </td>
                  <td className="texto-derecha">
                    <strong>
                      {formatearNumero(item.tempMinC)} a{' '}
                      {formatearNumero(item.tempMaxC)}
                    </strong>
                    <div className="texto-suave texto-chico">°C</div>
                  </td>
                  <td className="texto-derecha">
                    <span className="etiqueta etiqueta--info">
                      &le; {formatearNumero(item.probLluviaMaxPct)} %
                    </span>
                  </td>
                  <td className="texto-derecha">
                    <span className="etiqueta etiqueta--ok">
                      &ge; {formatearNumero(item.humedadMinPct)} %
                    </span>
                  </td>
                  <td>{item.mensajeAlerta}</td>
                  <td>
                    <div className="acciones-celda">
                      <button
                        type="button"
                        className="btn btn--secundario btn--chico"
                        onClick={() => setReglaASimular(item)}
                      >
                        Simular clima
                      </button>

                      {puedeEditar && (
                        <>
                          <button
                            type="button"
                            className="btn btn--secundario btn--chico"
                            onClick={() => {
                              setMensajeExito('')
                              setModalFormulario({ regla: item })
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
                              setReglaAEliminar(item)
                            }}
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
        <ReglaClimaticaFormModal
          regla={modalFormulario.regla}
          tiposDisponibles={tiposDisponibles}
          onCerrar={() => setModalFormulario(null)}
          onGuardado={async (mensaje) => {
            setModalFormulario(null)
            setMensajeExito(mensaje)
            await recargarReglas()
          }}
        />
      )}

      {reglaASimular && (
        <SimuladorClimaModal
          regla={reglaASimular}
          onCerrar={() => setReglaASimular(null)}
        />
      )}

      <ConfirmModal
        abierto={Boolean(reglaAEliminar)}
        titulo="Eliminar regla climática"
        mensaje={
          reglaAEliminar
            ? `¿Confirmás la eliminación de la regla climática para "${etiquetaTipoLabor(reglaAEliminar.tipoLabor)}"?`
            : ''
        }
        detalle="Solo se permite eliminar reglas sin labores pendientes de validación climática. Podrás volver a registrar los umbrales desde «+ Nueva regla»."
        cargando={eliminando}
        error={errorEliminacion}
        onCancelar={() => {
          if (!eliminando) {
            setReglaAEliminar(null)
            setErrorEliminacion('')
          }
        }}
        onConfirmar={confirmarEliminar}
      />
    </section>
  )
}

export default ReglasClimaticas
