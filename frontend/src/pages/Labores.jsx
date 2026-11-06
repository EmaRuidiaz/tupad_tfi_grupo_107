import { useCallback, useEffect, useMemo, useState } from 'react'
import ConfirmModal from '../components/ConfirmModal.jsx'
import CompletarLaborModal from '../components/labores/CompletarLaborModal.jsx'
import DetalleLaborModal from '../components/labores/DetalleLaborModal.jsx'
import LaborFormModal from '../components/labores/LaborFormModal.jsx'
import ReprogramarLaborModal from '../components/labores/ReprogramarLaborModal.jsx'
import ValidarClimaLaborModal from '../components/labores/ValidarClimaLaborModal.jsx'
import { getSession, tieneAlgunRol } from '../services/api.js'
import { listarInsumos } from '../services/insumosService.js'
import {
  cancelarLabor,
  iniciarLabor,
  listarLabores,
} from '../services/laboresService.js'
import { listarLotes } from '../services/lotesService.js'
import { listarMaquinarias } from '../services/maquinariaService.js'
import {
  ESTADOS_LABOR,
  TIPOS_LABOR,
  etiquetaEstadoLabor,
  etiquetaTipoLabor,
  formatearFecha,
  formatearNumero,
} from '../utils/formatters.js'

function claseEtiquetaEstadoLabor(estado) {
  if (estado === 'COMPLETADA' || estado === 'APROBADA_CLIMA') {
    return 'etiqueta etiqueta--ok'
  }
  if (estado === 'BLOQUEADA_CLIMA') {
    return 'etiqueta etiqueta--critica'
  }
  if (estado === 'EN_EJECUCION') {
    return 'etiqueta etiqueta--advertencia'
  }
  if (estado === 'PLANIFICADA') {
    return 'etiqueta etiqueta--info'
  }
  return 'etiqueta'
}

function admiteGestionPlanificacion(estado) {
  return (
    estado === 'PLANIFICADA' ||
    estado === 'APROBADA_CLIMA' ||
    estado === 'BLOQUEADA_CLIMA'
  )
}

// RN-01 / doc 06 §6.3.2: LABRANZA inicia desde PLANIFICADA; el resto desde APROBADA_CLIMA.
function admiteIniciarEjecucion(labor) {
  if (!labor) return false
  if (labor.estado === 'APROBADA_CLIMA') return true
  return labor.estado === 'PLANIFICADA' && !labor.requiereValidacionClimatica
}

// HU-10 / doc 06 §6.3.2: se valida desde PLANIFICADA o BLOQUEADA_CLIMA (revalidación).
function admiteValidarClima(labor) {
  return (
    labor.requiereValidacionClimatica &&
    (labor.estado === 'PLANIFICADA' || labor.estado === 'BLOQUEADA_CLIMA')
  )
}

// RN-08 / doc 06 §6.3.2: sólo una labor EN_EJECUCION puede pasar a COMPLETADA.
function admiteCompletarLabor(estado) {
  return estado === 'EN_EJECUCION'
}

function Labores() {
  const sesion = getSession()
  const puedePlanificar = tieneAlgunRol('ADMIN', 'AGRONOMO')
  const puedeEjecutar = tieneAlgunRol('ADMIN', 'AGRONOMO', 'OPERARIO')

  const [labores, setLabores] = useState([])
  const [lotes, setLotes] = useState([])
  const [maquinarias, setMaquinarias] = useState([])
  const [insumos, setInsumos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')

  const [busqueda, setBusqueda] = useState('')
  const [loteFiltro, setLoteFiltro] = useState('TODOS')
  const [tipoFiltro, setTipoFiltro] = useState('TODOS')
  const [estadoFiltro, setEstadoFiltro] = useState('TODOS')

  const [modalFormulario, setModalFormulario] = useState(null)
  const [laborDetalle, setLaborDetalle] = useState(null)
  const [laborReprogramar, setLaborReprogramar] = useState(null)
  const [laborACompletar, setLaborACompletar] = useState(null)
  const [laborAIniciar, setLaborAIniciar] = useState(null)
  const [laborValidarClima, setLaborValidarClima] = useState(null)
  const [iniciando, setIniciando] = useState(false)
  const [errorInicio, setErrorInicio] = useState('')
  const [laborACancelar, setLaborACancelar] = useState(null)
  const [cancelando, setCancelando] = useState(false)
  const [errorCancelacion, setErrorCancelacion] = useState('')

  const recargarTodo = useCallback(async () => {
    setErrorGlobal('')
    try {
      const [datosLabores, datosLotes, datosMaq, datosIns] = await Promise.all([
        listarLabores(),
        listarLotes(),
        listarMaquinarias(),
        listarInsumos(),
      ])
      setLabores(datosLabores)
      setLotes(datosLotes)
      setMaquinarias(datosMaq)
      setInsumos(datosIns)
    } catch (err) {
      setErrorGlobal(
        err.message || 'No se pudieron cargar las órdenes de trabajo.',
      )
    } finally {
      setCargando(false)
    }
  }, [])

  useEffect(() => {
    let cancelado = false
    Promise.all([
      listarLabores(),
      listarLotes(),
      listarMaquinarias(),
      listarInsumos(),
    ])
      .then(([datosLabores, datosLotes, datosMaq, datosIns]) => {
        if (!cancelado) {
          setLabores(datosLabores)
          setLotes(datosLotes)
          setMaquinarias(datosMaq)
          setInsumos(datosIns)
        }
      })
      .catch((err) => {
        if (!cancelado) {
          setErrorGlobal(
            err.message || 'No se pudieron cargar las órdenes de trabajo.',
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
    const total = labores.length
    const pendientes = labores.filter((l) =>
      admiteGestionPlanificacion(l.estado),
    ).length
    const enEjecucion = labores.filter(
      (l) => l.estado === 'EN_EJECUCION',
    ).length
    const completadas = labores.filter((l) => l.estado === 'COMPLETADA').length
    const costoAcumulado = labores
      .filter((l) => l.estado === 'COMPLETADA')
      .reduce((acc, l) => acc + Number(l.costoTotalCalculado || 0), 0)
    const reservasActivas = labores.reduce(
      (acc, l) =>
        acc +
        (l.insumos?.filter((i) => i.estadoReserva === 'RESERVADO').length || 0),
      0,
    )
    return {
      total,
      pendientes,
      enEjecucion,
      completadas,
      costoAcumulado,
      reservasActivas,
    }
  }, [labores])

  const laboresFiltradas = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    return labores.filter((l) => {
      if (loteFiltro !== 'TODOS' && String(l.loteId) !== String(loteFiltro)) {
        return false
      }
      if (tipoFiltro !== 'TODOS' && l.tipoLabor !== tipoFiltro) {
        return false
      }
      if (estadoFiltro !== 'TODOS' && l.estado !== estadoFiltro) {
        return false
      }
      if (texto) {
        const coincideLote = l.loteNombre?.toLowerCase().includes(texto)
        const coincideMaq = l.maquinariaNombre?.toLowerCase().includes(texto)
        const coincideObs = l.observacionesClimaticas
          ?.toLowerCase()
          .includes(texto)
        if (!coincideLote && !coincideMaq && !coincideObs) {
          return false
        }
      }
      return true
    })
  }, [labores, loteFiltro, tipoFiltro, estadoFiltro, busqueda])

  async function confirmarInicio() {
    if (!laborAIniciar) return
    setIniciando(true)
    setErrorInicio('')
    try {
      const iniciada = await iniciarLabor(laborAIniciar.id)
      setLaborAIniciar(null)
      setMensajeExito(
        `Orden #${iniciada.id} en ejecución. Se descontó el stock reservado y la maquinaria pasó a estado En labor.`,
      )
      await recargarTodo()
    } catch (err) {
      setErrorInicio(err.message || 'No se pudo iniciar la labor.')
    } finally {
      setIniciando(false)
    }
  }

  async function confirmarCancelacion() {
    if (!laborACancelar) return
    setCancelando(true)
    setErrorCancelacion('')
    try {
      await cancelarLabor(laborACancelar.id)
      const idCancelada = laborACancelar.id
      setLaborACancelar(null)
      setMensajeExito(
        `Orden de trabajo #${idCancelada} cancelada y reservas de stock liberadas.`,
      )
      await recargarTodo()
    } catch (err) {
      setErrorCancelacion(err.message || 'No se pudo cancelar la labor.')
    } finally {
      setCancelando(false)
    }
  }

  const detalleModalInicio = laborAIniciar
    ? `Al iniciar, la orden pasará a estado En ejecución, se descontará el stock físico de los insumos reservados (registrando el egreso en el historial) y la maquinaria asignada pasará a estado En labor.${
        laborAIniciar.maquinariaMantenimientoVencido
          ? ` ⚠ ATENCIÓN (RN-06): La maquinaria "${laborAIniciar.maquinariaNombre}" tiene el umbral de mantenimiento vencido; se recomienda registrar el service al finalizar.`
          : ''
      }`
    : ''

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Labores Agrícolas</h1>
          <p className="pagina__subtitulo">
            Planificación de órdenes de trabajo, reserva de insumos, ejecución
            en campo y cierre con impacto en stock, horómetros y costos (HU-09 /
            HU-11).
          </p>
        </div>

        <div className="pagina__acciones">
          {puedePlanificar ? (
            <button
              type="button"
              className="btn btn--primario"
              onClick={() => {
                setMensajeExito('')
                setModalFormulario({ labor: null })
              }}
            >
              + Programar labor
            </button>
          ) : (
            <span className="etiqueta etiqueta--info">
              Modo operativo ({sesion?.rol})
            </span>
          )}
        </div>
      </div>

      <div className="tarjetas-resumen">
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Órdenes registradas</span>
          <strong className="tarjeta-kpi__valor">{resumen.total}</strong>
          <span className="tarjeta-kpi__detalle">
            {resumen.enEjecucion > 0
              ? `${resumen.enEjecucion} en ejecución ahora`
              : 'Historial total'}
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Pendientes de ejecución</span>
          <strong className="tarjeta-kpi__valor">{resumen.pendientes}</strong>
          <span className="tarjeta-kpi__detalle">
            Planificadas o a validar clima
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Labores completadas</span>
          <strong className="tarjeta-kpi__valor">{resumen.completadas}</strong>
          <span className="tarjeta-kpi__detalle">
            Costo acum.: $ {formatearNumero(resumen.costoAcumulado)}
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Reservas activas de stock</span>
          <strong className="tarjeta-kpi__valor">
            {resumen.reservasActivas}
          </strong>
          <span className="tarjeta-kpi__detalle">
            Líneas de insumo en estado Reservado
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
          placeholder="Buscar por lote, maquinaria u observación..."
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
          {lotes.map((l) => (
            <option key={l.id} value={l.id}>
              {l.nombre}
            </option>
          ))}
        </select>

        <select
          className="select-filtro"
          value={tipoFiltro}
          onChange={(e) => setTipoFiltro(e.target.value)}
          aria-label="Filtrar por tipo de labor"
        >
          <option value="TODOS">Todos los tipos</option>
          {TIPOS_LABOR.map((t) => (
            <option key={t.valor} value={t.valor}>
              {t.etiqueta}
            </option>
          ))}
        </select>

        <select
          className="select-filtro"
          value={estadoFiltro}
          onChange={(e) => setEstadoFiltro(e.target.value)}
          aria-label="Filtrar por estado"
        >
          <option value="TODOS">Todos los estados</option>
          {ESTADOS_LABOR.map((e) => (
            <option key={e.valor} value={e.valor}>
              {e.etiqueta}
            </option>
          ))}
        </select>
      </div>

      {cargando ? (
        <p className="aviso">Cargando órdenes de trabajo...</p>
      ) : laboresFiltradas.length === 0 ? (
        <p className="aviso">
          No se encontraron órdenes de trabajo con los filtros seleccionados.
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Orden / Labor</th>
                <th>Lote</th>
                <th>Fecha planificada</th>
                <th>Maquinaria</th>
                <th>Insumos</th>
                <th className="texto-derecha">Costo (est. / final)</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {laboresFiltradas.map((labor) => {
                const esPendiente = admiteGestionPlanificacion(labor.estado)
                const puedeIniciarEsta = admiteIniciarEjecucion(labor)
                const puedeCompletarEsta = admiteCompletarLabor(labor.estado)

                return (
                  <tr
                    key={labor.id}
                    className={
                      labor.estado === 'BLOQUEADA_CLIMA' ? 'fila--critica' : ''
                    }
                  >
                    <td>
                      <strong>
                        #{labor.id} · {etiquetaTipoLabor(labor.tipoLabor)}
                      </strong>
                      <div className="texto-suave texto-chico">
                        {labor.requiereValidacionClimatica
                          ? 'Con regla climática'
                          : 'Sin regla climática'}{' '}
                        · Resp.: {labor.usuarioUsername || '-'}
                      </div>
                    </td>
                    <td>
                      <strong>{labor.loteNombre}</strong>
                      <div className="texto-suave texto-chico">
                        {formatearNumero(labor.loteSuperficieHa)} ha
                      </div>
                    </td>
                    <td className="texto-chico">
                      <div>
                        <strong>Plan.:</strong>{' '}
                        {formatearFecha(labor.fechaPlanificada)}
                      </div>
                      {labor.fechaEjecucion && (
                        <div className="texto-suave">
                          Ejec.: {formatearFecha(labor.fechaEjecucion)}
                        </div>
                      )}
                    </td>
                    <td>
                      {labor.maquinariaNombre ? (
                        <>
                          <div>{labor.maquinariaNombre}</div>
                          <div className="texto-suave texto-chico">
                            {labor.maquinariaMatriculaOSerie}
                            {labor.estado === 'COMPLETADA'
                              ? ` · ${formatearNumero(labor.horasMaquinaConsumidas)} hs`
                              : ''}
                            {labor.maquinariaMantenimientoVencido
                              ? ' · ⚠ Service vencido'
                              : ''}
                          </div>
                        </>
                      ) : (
                        <span className="texto-suave">Manual</span>
                      )}
                    </td>
                    <td className="texto-chico">
                      {!labor.insumos || labor.insumos.length === 0 ? (
                        <span className="texto-suave">Sin insumos</span>
                      ) : (
                        <div>
                          {labor.insumos.map((item) => (
                            <div key={item.id}>
                              {item.insumoNombre}:{' '}
                              <strong>
                                {formatearNumero(item.cantidadUtilizada)}
                              </strong>{' '}
                              {item.insumoUnidadMedida} (
                              {item.estadoReserva.toLowerCase()})
                            </div>
                          ))}
                        </div>
                      )}
                    </td>
                    <td className="texto-derecha">
                      {labor.estado === 'COMPLETADA' ? (
                        <>
                          <strong>
                            $ {formatearNumero(labor.costoTotalCalculado)}
                          </strong>
                          <div className="texto-suave texto-chico">
                            Consolidado
                          </div>
                        </>
                      ) : (
                        <>
                          <strong>
                            ${' '}
                            {formatearNumero(
                              labor.estado === 'EN_EJECUCION'
                                ? labor.costoInsumosConsumido
                                : labor.costoInsumosEstimado,
                            )}
                          </strong>
                          <div className="texto-suave texto-chico">
                            {labor.estado === 'EN_EJECUCION'
                              ? 'Insumos cons.'
                              : 'Insumos est.'}
                          </div>
                        </>
                      )}
                    </td>
                    <td>
                      <span className={claseEtiquetaEstadoLabor(labor.estado)}>
                        {etiquetaEstadoLabor(labor.estado)}
                      </span>
                    </td>
                    <td>
                      <div className="acciones-celda">
                        <button
                          type="button"
                          className="btn btn--secundario btn--chico"
                          onClick={() => setLaborDetalle(labor)}
                        >
                          Detalle
                        </button>
                        {puedeEjecutar && admiteValidarClima(labor) && (
                          <button
                            type="button"
                            className="btn btn--primario btn--chico"
                            onClick={() => {
                              setMensajeExito('')
                              setLaborValidarClima(labor)
                            }}
                          >
                            Validar clima
                          </button>
                        )}
                        {puedeEjecutar && puedeIniciarEsta && (
                          <button
                            type="button"
                            className="btn btn--primario btn--chico"
                            onClick={() => {
                              setMensajeExito('')
                              setErrorInicio('')
                              setLaborAIniciar(labor)
                            }}
                          >
                            Iniciar
                          </button>
                        )}
                        {puedeEjecutar && puedeCompletarEsta && (
                          <button
                            type="button"
                            className="btn btn--primario btn--chico"
                            onClick={() => {
                              setMensajeExito('')
                              setLaborACompletar(labor)
                            }}
                          >
                            Completar
                          </button>
                        )}
                        {puedePlanificar && esPendiente && (
                          <>
                            <button
                              type="button"
                              className="btn btn--secundario btn--chico"
                              onClick={() => {
                                setMensajeExito('')
                                setModalFormulario({ labor })
                              }}
                            >
                              Editar
                            </button>
                            <button
                              type="button"
                              className="btn btn--secundario btn--chico"
                              onClick={() => {
                                setMensajeExito('')
                                setLaborReprogramar(labor)
                              }}
                            >
                              Reprogramar
                            </button>
                            <button
                              type="button"
                              className="btn btn--peligro btn--chico"
                              onClick={() => {
                                setMensajeExito('')
                                setErrorCancelacion('')
                                setLaborACancelar(labor)
                              }}
                            >
                              Cancelar
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {modalFormulario && (
        <LaborFormModal
          labor={modalFormulario.labor}
          lotes={lotes}
          maquinarias={maquinarias}
          insumos={insumos}
          laboresExistentes={labores}
          onCerrar={() => setModalFormulario(null)}
          onGuardado={async (mensaje) => {
            setModalFormulario(null)
            setMensajeExito(mensaje)
            await recargarTodo()
          }}
        />
      )}

      {laborDetalle && (
        <DetalleLaborModal
          labor={laborDetalle}
          onCerrar={() => setLaborDetalle(null)}
        />
      )}

      {laborReprogramar && (
        <ReprogramarLaborModal
          labor={laborReprogramar}
          onCerrar={() => setLaborReprogramar(null)}
          onGuardado={async (mensaje) => {
            setLaborReprogramar(null)
            setMensajeExito(mensaje)
            await recargarTodo()
          }}
        />
      )}

      {laborValidarClima && (
        <ValidarClimaLaborModal
          labor={laborValidarClima}
          onCerrar={async (cambioEstado) => {
            setLaborValidarClima(null)
            if (cambioEstado) await recargarTodo()
          }}
        />
      )}

      {laborACompletar && (
        <CompletarLaborModal
          labor={laborACompletar}
          maquinarias={maquinarias}
          onCerrar={() => setLaborACompletar(null)}
          onGuardado={async (mensaje) => {
            setLaborACompletar(null)
            setMensajeExito(mensaje)
            await recargarTodo()
          }}
        />
      )}

      <ConfirmModal
        abierto={Boolean(laborAIniciar)}
        titulo="Iniciar ejecución de labor (CU-04 / HU-11)"
        mensaje={
          laborAIniciar
            ? `¿Confirmás el inicio en campo de la orden #${laborAIniciar.id} (${etiquetaTipoLabor(laborAIniciar.tipoLabor)} en "${laborAIniciar.loteNombre}")?`
            : ''
        }
        detalle={detalleModalInicio}
        textoConfirmar="Sí, iniciar labor"
        cargando={iniciando}
        error={errorInicio}
        onCancelar={() => {
          if (!iniciando) {
            setLaborAIniciar(null)
            setErrorInicio('')
          }
        }}
        onConfirmar={confirmarInicio}
      />

      <ConfirmModal
        abierto={Boolean(laborACancelar)}
        titulo="Cancelar orden de trabajo (CU-08)"
        mensaje={
          laborACancelar
            ? `¿Confirmás la cancelación de la orden #${laborACancelar.id} (${etiquetaTipoLabor(laborACancelar.tipoLabor)} en "${laborACancelar.loteNombre}")?`
            : ''
        }
        detalle="La orden pasará a estado Cancelada y todas sus reservas de insumos volverán a estar disponibles en el inventario (estado Liberado)."
        textoConfirmar="Sí, cancelar orden"
        cargando={cancelando}
        error={errorCancelacion}
        onCancelar={() => {
          if (!cancelando) {
            setLaborACancelar(null)
            setErrorCancelacion('')
          }
        }}
        onConfirmar={confirmarCancelacion}
      />
    </section>
  )
}

export default Labores
