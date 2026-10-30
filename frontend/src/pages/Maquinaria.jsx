import { useCallback, useEffect, useMemo, useState } from 'react'
import ConfirmModal from '../components/ConfirmModal.jsx'
import MaquinariaFormModal from '../components/maquinaria/MaquinariaFormModal.jsx'
import MantenimientoModal from '../components/maquinaria/MantenimientoModal.jsx'
import { getSession, isAdmin } from '../services/api.js'
import {
  cambiarEstadoMaquinaria,
  eliminarMaquinaria,
  listarMaquinarias,
} from '../services/maquinariaService.js'
import {
  ESTADOS_MAQUINARIA,
  TIPOS_MAQUINARIA,
  etiquetaEstadoMaquinaria,
  etiquetaTipoMaquinaria,
  formatearNumero,
} from '../utils/formatters.js'

function claseEstado(estado) {
  if (estado === 'OPERATIVO') return 'etiqueta etiqueta--ok'
  if (estado === 'FUERA_DE_SERVICIO') return 'etiqueta etiqueta--critica'
  if (estado === 'EN_MANTENIMIENTO') return 'etiqueta etiqueta--info'
  if (estado === 'EN_LABOR') return 'etiqueta etiqueta--advertencia'
  return 'etiqueta'
}

function Maquinaria() {
  const sesion = getSession()
  const puedeEditar = isAdmin()

  const [maquinarias, setMaquinarias] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')

  const [busqueda, setBusqueda] = useState('')
  const [tipoFiltro, setTipoFiltro] = useState('TODOS')
  const [estadoFiltro, setEstadoFiltro] = useState('TODOS')
  const [soloAlertas, setSoloAlertas] = useState(false)

  const [modalFormulario, setModalFormulario] = useState(null)
  const [maquinariaService, setMaquinariaService] = useState(null)
  const [maquinariaAEliminar, setMaquinariaAEliminar] = useState(null)
  const [eliminando, setEliminando] = useState(false)
  const [errorEliminacion, setErrorEliminacion] = useState('')

  const [maquinariaABajar, setMaquinariaABajar] = useState(null)
  const [bajando, setBajando] = useState(false)
  const [errorBaja, setErrorBaja] = useState('')

  const recargarMaquinarias = useCallback(async () => {
    setErrorGlobal('')
    try {
      const datos = await listarMaquinarias()
      setMaquinarias(datos)
    } catch (err) {
      setErrorGlobal(
        err.message || 'No se pudo cargar el parque de maquinaria.',
      )
    } finally {
      setCargando(false)
    }
  }, [])

  useEffect(() => {
    let cancelado = false
    listarMaquinarias()
      .then((datos) => {
        if (!cancelado) setMaquinarias(datos)
      })
      .catch((err) => {
        if (!cancelado) {
          setErrorGlobal(
            err.message || 'No se pudo cargar el parque de maquinaria.',
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
    const total = maquinarias.length
    const operativas = maquinarias.filter(
      (m) => m.estado === 'OPERATIVO',
    ).length
    const serviceVencido = maquinarias.filter(
      (m) => m.mantenimientoVencido,
    ).length
    const serviceProximo = maquinarias.filter(
      (m) => !m.mantenimientoVencido && Number(m.horasRestantesService) <= 50,
    ).length
    return { total, operativas, serviceVencido, serviceProximo }
  }, [maquinarias])

  const maquinariasFiltradas = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    return maquinarias.filter((item) => {
      if (
        soloAlertas &&
        !item.mantenimientoVencido &&
        Number(item.horasRestantesService) > 50
      ) {
        return false
      }
      if (tipoFiltro !== 'TODOS' && item.tipo !== tipoFiltro) return false
      if (estadoFiltro !== 'TODOS' && item.estado !== estadoFiltro) return false
      if (
        texto &&
        !item.nombre.toLowerCase().includes(texto) &&
        !item.matriculaOSerie.toLowerCase().includes(texto)
      ) {
        return false
      }
      return true
    })
  }, [maquinarias, soloAlertas, tipoFiltro, estadoFiltro, busqueda])

  async function manejarCambioEstado(maquinaria, nuevoEstado) {
    setErrorGlobal('')
    setMensajeExito('')
    try {
      await cambiarEstadoMaquinaria(maquinaria.id, nuevoEstado)
      setMensajeExito(
        `Estado de "${maquinaria.nombre}" actualizado a ${etiquetaEstadoMaquinaria(nuevoEstado)}.`,
      )
      await recargarMaquinarias()
    } catch (err) {
      setErrorGlobal(err.message || 'No se pudo cambiar el estado.')
    }
  }

  async function confirmarBaja() {
    if (!maquinariaABajar) return
    setBajando(true)
    setErrorBaja('')
    try {
      await cambiarEstadoMaquinaria(maquinariaABajar.id, 'FUERA_DE_SERVICIO')
      const nombre = maquinariaABajar.nombre
      setMaquinariaABajar(null)
      setMensajeExito(
        `Maquinaria "${nombre}" dada de baja lógica (Fuera de servicio).`,
      )
      await recargarMaquinarias()
    } catch (err) {
      setErrorBaja(err.message || 'No se pudo dar de baja la maquinaria.')
    } finally {
      setBajando(false)
    }
  }

  async function confirmarEliminar() {
    if (!maquinariaAEliminar) return
    setEliminando(true)
    setErrorEliminacion('')
    try {
      await eliminarMaquinaria(maquinariaAEliminar.id)
      const nombre = maquinariaAEliminar.nombre
      setMaquinariaAEliminar(null)
      setMensajeExito(`Maquinaria "${nombre}" eliminada.`)
      await recargarMaquinarias()
    } catch (err) {
      setErrorEliminacion(err.message || 'No se pudo eliminar la maquinaria.')
    } finally {
      setEliminando(false)
    }
  }

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Parque de Maquinaria</h1>
          <p className="pagina__subtitulo">
            Control de tractores, cosechadoras, pulverizadoras y vehículos, con
            seguimiento de horómetros y costos por hora.
          </p>
        </div>

        <div className="pagina__acciones">
          {puedeEditar ? (
            <button
              type="button"
              className="btn btn--primario"
              onClick={() => {
                setMensajeExito('')
                setModalFormulario({ maquinaria: null })
              }}
            >
              + Nueva maquinaria
            </button>
          ) : (
            <span className="etiqueta etiqueta--info">
              Rol {sesion?.rol} (consulta e ingreso a taller)
            </span>
          )}
        </div>
      </div>

      <div className="tarjetas-resumen">
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Total en flota</span>
          <strong className="tarjeta-kpi__valor">{resumen.total}</strong>
          <span className="tarjeta-kpi__detalle">Unidades registradas</span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Unidades operativas</span>
          <strong className="tarjeta-kpi__valor">{resumen.operativas}</strong>
          <span className="tarjeta-kpi__detalle">
            Disponibles para asignar a labores
          </span>
        </div>

        <div
          className={`tarjeta-kpi ${
            resumen.serviceVencido > 0
              ? 'tarjeta-kpi--alerta'
              : resumen.serviceProximo > 0
                ? 'tarjeta-kpi--info'
                : ''
          }`}
        >
          <span className="tarjeta-kpi__titulo">Atención de service</span>
          <strong className="tarjeta-kpi__valor">
            {resumen.serviceVencido > 0
              ? `${resumen.serviceVencido} vencido${resumen.serviceVencido > 1 ? 's' : ''}`
              : `${resumen.serviceProximo} próximo${resumen.serviceProximo > 1 ? 's' : ''}`}
          </strong>
          <span className="tarjeta-kpi__detalle">
            {resumen.serviceVencido > 0
              ? `${resumen.serviceVencido} superaron el umbral (${resumen.serviceProximo} a menos de 50 hs)`
              : resumen.serviceProximo > 0
                ? `${resumen.serviceProximo} a menos de 50 hs del próximo service`
                : 'Todas las unidades dentro de sus horas de service'}
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
          placeholder="Buscar por nombre o matrícula/serie..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />

        <select
          className="select-filtro"
          value={tipoFiltro}
          onChange={(e) => setTipoFiltro(e.target.value)}
          aria-label="Filtrar por tipo"
        >
          <option value="TODOS">Todos los tipos</option>
          {TIPOS_MAQUINARIA.map((t) => (
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
          {ESTADOS_MAQUINARIA.map((e) => (
            <option key={e.valor} value={e.valor}>
              {e.etiqueta}
            </option>
          ))}
        </select>

        <label className="check-filtro">
          <input
            type="checkbox"
            checked={soloAlertas}
            onChange={(e) => setSoloAlertas(e.target.checked)}
          />
          <span>
            Solo alertas de service (
            {resumen.serviceVencido + resumen.serviceProximo})
          </span>
        </label>
      </div>

      {cargando ? (
        <p className="aviso">Cargando parque de maquinaria...</p>
      ) : maquinariasFiltradas.length === 0 ? (
        <p className="aviso">
          No se encontraron maquinarias con los filtros seleccionados.
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Maquinaria</th>
                <th>Tipo</th>
                <th className="texto-derecha">Horómetro</th>
                <th className="texto-derecha">Próx. service</th>
                <th className="texto-derecha">Consumo</th>
                <th className="texto-derecha">Costo / h</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {maquinariasFiltradas.map((item) => (
                <tr
                  key={item.id}
                  className={
                    item.mantenimientoVencido
                      ? 'fila--critica'
                      : Number(item.horasRestantesService) <= 50
                        ? 'fila--alerta'
                        : ''
                  }
                >
                  <td>
                    <strong>{item.nombre}</strong>
                    <div className="texto-suave texto-chico">
                      Serie/Matrícula: {item.matriculaOSerie}
                    </div>
                  </td>
                  <td>
                    <span className="etiqueta">
                      {etiquetaTipoMaquinaria(item.tipo)}
                    </span>
                  </td>
                  <td className="texto-derecha">
                    <strong>{formatearNumero(item.horasUsoActuales)} hs</strong>
                  </td>
                  <td className="texto-derecha">
                    <div>
                      {formatearNumero(item.horasParaProximoService)} hs
                    </div>
                    {item.mantenimientoVencido ? (
                      <span className="etiqueta etiqueta--critica">
                        ⚠️ Service vencido
                      </span>
                    ) : Number(item.horasRestantesService) <= 50 ? (
                      <span
                        className="etiqueta etiqueta--advertencia"
                        title="Restan pocas horas de uso para el próximo service"
                      >
                        ⚡ Próximo (restan{' '}
                        {formatearNumero(item.horasRestantesService)} hs)
                      </span>
                    ) : (
                      <span className="texto-suave texto-chico">
                        Restan {formatearNumero(item.horasRestantesService)} hs
                      </span>
                    )}
                  </td>
                  <td className="texto-derecha">
                    {formatearNumero(item.consumoLitrosHora)} L/h
                  </td>
                  <td className="texto-derecha">
                    $ {formatearNumero(item.costoOperativoHora)}
                  </td>
                  <td>
                    <span className={claseEstado(item.estado)}>
                      {etiquetaEstadoMaquinaria(item.estado)}
                    </span>
                  </td>
                  <td>
                    <div className="acciones-celda">
                      <button
                        type="button"
                        className="btn btn--secundario btn--chico"
                        onClick={() => {
                          setMensajeExito('')
                          setMaquinariaService(item)
                        }}
                      >
                        Service
                      </button>

                      {item.estado === 'EN_LABOR' && (
                        <span className="texto-suave texto-chico">
                          En labor activa
                        </span>
                      )}

                      {item.estado === 'OPERATIVO' && (
                        <button
                          type="button"
                          className="btn btn--secundario btn--chico"
                          onClick={() =>
                            manejarCambioEstado(item, 'EN_MANTENIMIENTO')
                          }
                        >
                          Enviar a taller
                        </button>
                      )}

                      {puedeEditar && item.estado !== 'EN_LABOR' && (
                        <>
                          {item.estado === 'EN_MANTENIMIENTO' && (
                            <button
                              type="button"
                              className="btn btn--secundario btn--chico"
                              onClick={() =>
                                manejarCambioEstado(item, 'OPERATIVO')
                              }
                            >
                              Marcar operativo
                            </button>
                          )}

                          {item.estado === 'FUERA_DE_SERVICIO' && (
                            <button
                              type="button"
                              className="btn btn--secundario btn--chico"
                              onClick={() =>
                                manejarCambioEstado(item, 'EN_MANTENIMIENTO')
                              }
                            >
                              Reingresar a taller
                            </button>
                          )}

                          {(item.estado === 'OPERATIVO' ||
                            item.estado === 'EN_MANTENIMIENTO') && (
                            <button
                              type="button"
                              className="btn btn--secundario btn--chico"
                              onClick={() => {
                                setMensajeExito('')
                                setErrorBaja('')
                                setMaquinariaABajar(item)
                              }}
                            >
                              Dar de baja
                            </button>
                          )}

                          <button
                            type="button"
                            className="btn btn--secundario btn--chico"
                            onClick={() => {
                              setMensajeExito('')
                              setModalFormulario({ maquinaria: item })
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
                              setMaquinariaAEliminar(item)
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
        <MaquinariaFormModal
          maquinaria={modalFormulario.maquinaria}
          onCerrar={() => setModalFormulario(null)}
          onGuardado={async (mensaje) => {
            setModalFormulario(null)
            setMensajeExito(mensaje)
            await recargarMaquinarias()
          }}
        />
      )}

      {maquinariaService && (
        <MantenimientoModal
          maquinaria={maquinariaService}
          onCerrar={() => setMaquinariaService(null)}
          onGuardado={async (mensaje) => {
            setMaquinariaService(null)
            setMensajeExito(mensaje)
            await recargarMaquinarias()
          }}
        />
      )}

      <ConfirmModal
        abierto={Boolean(maquinariaAEliminar)}
        titulo="Eliminar maquinaria"
        mensaje={
          maquinariaAEliminar
            ? `¿Confirmás la eliminación de "${maquinariaAEliminar.nombre}" (${maquinariaAEliminar.matriculaOSerie})?`
            : ''
        }
        detalle="Solo se permite eliminar maquinarias sin labores agrícolas asociadas ni historial de mantenimiento registrado."
        cargando={eliminando}
        error={errorEliminacion}
        onCancelar={() => {
          if (!eliminando) {
            setMaquinariaAEliminar(null)
            setErrorEliminacion('')
          }
        }}
        onConfirmar={confirmarEliminar}
      />

      <ConfirmModal
        abierto={Boolean(maquinariaABajar)}
        titulo="Dar de baja maquinaria"
        mensaje={
          maquinariaABajar
            ? `¿Confirmás pasar a Fuera de servicio a "${maquinariaABajar.nombre}" (${maquinariaABajar.matriculaOSerie})?`
            : ''
        }
        detalle="La maquinaria quedará inhabilitada para ser asignada a labores agrícolas hasta que un administrador decida reingresarla a taller."
        textoConfirmar="Dar de baja"
        textoCargando="Dando de baja..."
        cargando={bajando}
        error={errorBaja}
        onCancelar={() => {
          if (!bajando) {
            setMaquinariaABajar(null)
            setErrorBaja('')
          }
        }}
        onConfirmar={confirmarBaja}
      />
    </section>
  )
}

export default Maquinaria
