import { useCallback, useEffect, useMemo, useState } from 'react'
import { getSession, isAdmin } from '../services/api.js'
import {
  actualizarInsumo,
  crearInsumo,
  eliminarInsumo,
  listarInsumos,
  listarMovimientosInsumo,
  registrarAjusteStock,
} from '../services/insumosService.js'

const CATEGORIAS = [
  { valor: 'SEMILLA', etiqueta: 'Semilla' },
  { valor: 'FERTILIZANTE', etiqueta: 'Fertilizante' },
  { valor: 'FITOSANITARIO', etiqueta: 'Fitosanitario' },
  { valor: 'COMBUSTIBLE', etiqueta: 'Combustible' },
]

const UNIDADES = [
  { valor: 'BOLSAS', etiqueta: 'Bolsas' },
  { valor: 'LITROS', etiqueta: 'Litros' },
  { valor: 'KILOGRAMOS', etiqueta: 'Kilogramos' },
  { valor: 'TONELADAS', etiqueta: 'Toneladas' },
]

const FORM_INICIAL = {
  nombre: '',
  categoria: 'SEMILLA',
  unidadMedida: 'BOLSAS',
  stockInicial: '0',
  stockMinimoAlerta: '0',
  precioUnitario: '0',
}

function formatearNumero(valor) {
  return Number(valor ?? 0).toLocaleString('es-AR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

function formatearFecha(iso) {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('es-AR', {
    dateStyle: 'short',
    timeStyle: 'short',
  })
}

function Insumos() {
  const sesion = getSession()
  const puedeEditar = isAdmin()

  const [insumos, setInsumos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')

  // Filtros de listado
  const [busqueda, setBusqueda] = useState('')
  const [categoriaFiltro, setCategoriaFiltro] = useState('TODAS')
  const [soloCriticos, setSoloCriticos] = useState(false)

  // Modal de Alta / Edición de Insumo
  const [modalInsumoAbierto, setModalInsumoAbierto] = useState(false)
  const [insumoEditando, setInsumoEditando] = useState(null)
  const [formInsumo, setFormInsumo] = useState(FORM_INICIAL)
  const [guardandoInsumo, setGuardandoInsumo] = useState(false)
  const [errorModalInsumo, setErrorModalInsumo] = useState('')

  // Modal de Ajuste de Stock
  const [insumoAjuste, setInsumoAjuste] = useState(null)
  const [cantidadAjuste, setCantidadAjuste] = useState('')
  const [observacionAjuste, setObservacionAjuste] = useState('')
  const [guardandoAjuste, setGuardandoAjuste] = useState(false)
  const [errorModalAjuste, setErrorModalAjuste] = useState('')

  // Modal de Historial de Movimientos
  const [insumoHistorial, setInsumoHistorial] = useState(null)
  const [movimientos, setMovimientos] = useState([])
  const [cargandoMovimientos, setCargandoMovimientos] = useState(false)
  const [errorMovimientos, setErrorMovimientos] = useState('')

  const cargarInsumos = useCallback(async () => {
    setErrorGlobal('')
    try {
      const datos = await listarInsumos(false)
      setInsumos(datos)
    } catch (err) {
      setErrorGlobal(err.message || 'No se pudo cargar el inventario de insumos.')
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

  function abrirNuevoInsumo() {
    setInsumoEditando(null)
    setFormInsumo(FORM_INICIAL)
    setErrorModalInsumo('')
    setModalInsumoAbierto(true)
  }

  function abrirEditarInsumo(insumo) {
    setInsumoEditando(insumo)
    setFormInsumo({
      nombre: insumo.nombre,
      categoria: insumo.categoria,
      unidadMedida: insumo.unidadMedida,
      stockInicial: '0',
      stockMinimoAlerta: String(insumo.stockMinimoAlerta ?? 0),
      precioUnitario: String(insumo.precioUnitario ?? 0),
    })
    setErrorModalInsumo('')
    setModalInsumoAbierto(true)
  }

  async function manejarGuardarInsumo(e) {
    e.preventDefault()
    setErrorModalInsumo('')
    setMensajeExito('')
    setGuardandoInsumo(true)

    const payload = {
      nombre: formInsumo.nombre.trim(),
      categoria: formInsumo.categoria,
      unidadMedida: formInsumo.unidadMedida,
      stockMinimoAlerta: Number(formInsumo.stockMinimoAlerta),
      precioUnitario: Number(formInsumo.precioUnitario),
      ...(insumoEditando
        ? {}
        : { stockInicial: Number(formInsumo.stockInicial || 0) }),
    }

    try {
      if (insumoEditando) {
        await actualizarInsumo(insumoEditando.id, payload)
        setMensajeExito(`Insumo "${payload.nombre}" actualizado correctamente.`)
      } else {
        await crearInsumo(payload)
        setMensajeExito(`Insumo "${payload.nombre}" registrado correctamente.`)
      }
      setModalInsumoAbierto(false)
      await cargarInsumos()
    } catch (err) {
      setErrorModalInsumo(err.message || 'No se pudo guardar el insumo.')
    } finally {
      setGuardandoInsumo(false)
    }
  }

  async function manejarEliminar(insumo) {
    const confirmado = window.confirm(
      `¿Eliminar el insumo "${insumo.nombre}"? Sólo se permite si no registra movimientos, compras ni labores.`,
    )
    if (!confirmado) return

    setErrorGlobal('')
    setMensajeExito('')
    try {
      await eliminarInsumo(insumo.id)
      setMensajeExito(`Insumo "${insumo.nombre}" eliminado.`)
      await cargarInsumos()
    } catch (err) {
      setErrorGlobal(err.message || 'No se pudo eliminar el insumo.')
    }
  }

  function abrirModalAjuste(insumo) {
    setInsumoAjuste(insumo)
    setCantidadAjuste('')
    setObservacionAjuste('')
    setErrorModalAjuste('')
  }

  async function manejarGuardarAjuste(e) {
    e.preventDefault()
    if (!insumoAjuste) return
    setErrorModalAjuste('')
    setMensajeExito('')
    setGuardandoAjuste(true)

    try {
      await registrarAjusteStock(insumoAjuste.id, {
        cantidad: Number(cantidadAjuste),
        observacion: observacionAjuste.trim(),
      })
      setMensajeExito(
        `Ajuste de stock aplicado sobre "${insumoAjuste.nombre}".`,
      )
      setInsumoAjuste(null)
      await cargarInsumos()
    } catch (err) {
      setErrorModalAjuste(err.message || 'No se pudo registrar el ajuste.')
    } finally {
      setGuardandoAjuste(false)
    }
  }

  async function abrirHistorial(insumo) {
    setInsumoHistorial(insumo)
    setMovimientos([])
    setErrorMovimientos('')
    setCargandoMovimientos(true)
    try {
      const lista = await listarMovimientosInsumo(insumo.id)
      setMovimientos(lista)
    } catch (err) {
      setErrorMovimientos(
        err.message || 'No se pudo obtener el historial de movimientos.',
      )
    } finally {
      setCargandoMovimientos(false)
    }
  }

  const stockResultantePreview = insumoAjuste
    ? Number(insumoAjuste.stockActual) + Number(cantidadAjuste || 0)
    : 0

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
              onClick={abrirNuevoInsumo}
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
          {CATEGORIAS.map((c) => (
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
                    USD {formatearNumero(item.precioUnitario)}
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
                        onClick={() => abrirHistorial(item)}
                      >
                        Historial
                      </button>
                      {puedeEditar && (
                        <>
                          <button
                            type="button"
                            className="btn btn--secundario btn--chico"
                            onClick={() => abrirModalAjuste(item)}
                          >
                            Ajustar
                          </button>
                          <button
                            type="button"
                            className="btn btn--secundario btn--chico"
                            onClick={() => abrirEditarInsumo(item)}
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

      {/* Modal de Alta / Edición de Insumo */}
      {modalInsumoAbierto && (
        <div className="modal-fondo" role="dialog" aria-modal="true">
          <div className="modal">
            <div className="modal__encabezado">
              <h2>
                {insumoEditando ? 'Editar insumo' : 'Registrar nuevo insumo'}
              </h2>
              <button
                type="button"
                className="modal__cerrar"
                onClick={() => setModalInsumoAbierto(false)}
                aria-label="Cerrar"
              >
                ✕
              </button>
            </div>

            {errorModalInsumo && (
              <div className="alerta alerta--error">{errorModalInsumo}</div>
            )}

            <form onSubmit={manejarGuardarInsumo} className="formulario">
              <div className="campo">
                <label htmlFor="insumo-nombre">Nombre comercial</label>
                <input
                  id="insumo-nombre"
                  type="text"
                  maxLength={120}
                  required
                  value={formInsumo.nombre}
                  onChange={(e) =>
                    setFormInsumo({ ...formInsumo, nombre: e.target.value })
                  }
                  placeholder="Ej: Semilla Soja Don Mario 46i20"
                />
              </div>

              <div className="fila-campos">
                <div className="campo">
                  <label htmlFor="insumo-categoria">Categoría</label>
                  <select
                    id="insumo-categoria"
                    value={formInsumo.categoria}
                    onChange={(e) =>
                      setFormInsumo({
                        ...formInsumo,
                        categoria: e.target.value,
                      })
                    }
                  >
                    {CATEGORIAS.map((c) => (
                      <option key={c.valor} value={c.valor}>
                        {c.etiqueta}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="campo">
                  <label htmlFor="insumo-unidad">Unidad de medida</label>
                  <select
                    id="insumo-unidad"
                    value={formInsumo.unidadMedida}
                    onChange={(e) =>
                      setFormInsumo({
                        ...formInsumo,
                        unidadMedida: e.target.value,
                      })
                    }
                  >
                    {UNIDADES.map((u) => (
                      <option key={u.valor} value={u.valor}>
                        {u.etiqueta}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="fila-campos">
                {!insumoEditando && (
                  <div className="campo">
                    <label htmlFor="insumo-stock-inicial">Stock inicial</label>
                    <input
                      id="insumo-stock-inicial"
                      type="number"
                      step="0.01"
                      min="0"
                      required
                      value={formInsumo.stockInicial}
                      onChange={(e) =>
                        setFormInsumo({
                          ...formInsumo,
                          stockInicial: e.target.value,
                        })
                      }
                    />
                  </div>
                )}

                <div className="campo">
                  <label htmlFor="insumo-stock-minimo">
                    Stock mínimo de alerta
                  </label>
                  <input
                    id="insumo-stock-minimo"
                    type="number"
                    step="0.01"
                    min="0"
                    required
                    value={formInsumo.stockMinimoAlerta}
                    onChange={(e) =>
                      setFormInsumo({
                        ...formInsumo,
                        stockMinimoAlerta: e.target.value,
                      })
                    }
                  />
                </div>

                <div className="campo">
                  <label htmlFor="insumo-precio">Precio unitario (USD)</label>
                  <input
                    id="insumo-precio"
                    type="number"
                    step="0.01"
                    min="0"
                    required
                    value={formInsumo.precioUnitario}
                    onChange={(e) =>
                      setFormInsumo({
                        ...formInsumo,
                        precioUnitario: e.target.value,
                      })
                    }
                  />
                </div>
              </div>

              {insumoEditando && (
                <p className="texto-suave texto-chico">
                  Nota: El stock físico actual (
                  {formatearNumero(insumoEditando.stockActual)}{' '}
                  {insumoEditando.unidadMedida}) sólo se modifica mediante
                  compras, labores o el botón &laquo;Ajustar&raquo; para
                  mantener el historial de movimientos.
                </p>
              )}

              <div className="modal__pie">
                <button
                  type="button"
                  className="btn btn--secundario"
                  onClick={() => setModalInsumoAbierto(false)}
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="btn btn--primario"
                  disabled={guardandoInsumo}
                >
                  {guardandoInsumo ? 'Guardando...' : 'Guardar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal de Ajuste de Stock */}
      {insumoAjuste && (
        <div className="modal-fondo" role="dialog" aria-modal="true">
          <div className="modal">
            <div className="modal__encabezado">
              <h2>Ajustar stock: {insumoAjuste.nombre}</h2>
              <button
                type="button"
                className="modal__cerrar"
                onClick={() => setInsumoAjuste(null)}
                aria-label="Cerrar"
              >
                ✕
              </button>
            </div>

            {errorModalAjuste && (
              <div className="alerta alerta--error">{errorModalAjuste}</div>
            )}

            <div className="resumen-ajuste">
              <div>
                <span>Stock actual:</span>{' '}
                <strong>
                  {formatearNumero(insumoAjuste.stockActual)}{' '}
                  {insumoAjuste.unidadMedida}
                </strong>
              </div>
              <div>
                <span>Stock reservado:</span>{' '}
                <strong>
                  {formatearNumero(insumoAjuste.stockReservado)}{' '}
                  {insumoAjuste.unidadMedida}
                </strong>
              </div>
              <div>
                <span>Stock resultante:</span>{' '}
                <strong
                  className={
                    stockResultantePreview < 0 ? 'texto-peligro' : ''
                  }
                >
                  {formatearNumero(stockResultantePreview)}{' '}
                  {insumoAjuste.unidadMedida}
                </strong>
              </div>
            </div>

            <form onSubmit={manejarGuardarAjuste} className="formulario">
              <div className="campo">
                <label htmlFor="ajuste-cantidad">
                  Cantidad a ajustar (+ ingreso / - merma)
                </label>
                <input
                  id="ajuste-cantidad"
                  type="number"
                  step="0.01"
                  required
                  placeholder="Ej: 50 o -15.5"
                  value={cantidadAjuste}
                  onChange={(e) => setCantidadAjuste(e.target.value)}
                />
              </div>

              <div className="campo">
                <label htmlFor="ajuste-observacion">
                  Observación / Motivo del ajuste
                </label>
                <textarea
                  id="ajuste-observacion"
                  rows={3}
                  required
                  placeholder="Ej: Recuento físico en galpón principal"
                  value={observacionAjuste}
                  onChange={(e) => setObservacionAjuste(e.target.value)}
                />
              </div>

              <div className="modal__pie">
                <button
                  type="button"
                  className="btn btn--secundario"
                  onClick={() => setInsumoAjuste(null)}
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="btn btn--primario"
                  disabled={guardandoAjuste}
                >
                  {guardandoAjuste ? 'Aplicando...' : 'Confirmar ajuste'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal de Historial de Movimientos */}
      {insumoHistorial && (
        <div className="modal-fondo" role="dialog" aria-modal="true">
          <div className="modal modal--ancho">
            <div className="modal__encabezado">
              <h2>
                Historial de movimientos: {insumoHistorial.nombre} (
                {insumoHistorial.unidadMedida})
              </h2>
              <button
                type="button"
                className="modal__cerrar"
                onClick={() => setInsumoHistorial(null)}
                aria-label="Cerrar"
              >
                ✕
              </button>
            </div>

            {errorMovimientos && (
              <div className="alerta alerta--error">{errorMovimientos}</div>
            )}

            {cargandoMovimientos ? (
              <p className="aviso">Cargando movimientos...</p>
            ) : movimientos.length === 0 ? (
              <p className="aviso">
                Este insumo aún no registra movimientos en el historial de
                auditoría.
              </p>
            ) : (
              <div className="tabla-contenedor">
                <table className="tabla">
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Tipo</th>
                      <th className="texto-derecha">Cantidad</th>
                      <th>Responsable</th>
                      <th>Observación</th>
                    </tr>
                  </thead>
                  <tbody>
                    {movimientos.map((m) => {
                      const esPositivo = Number(m.cantidad) > 0
                      return (
                        <tr key={m.id}>
                          <td>{formatearFecha(m.fecha)}</td>
                          <td>
                            <span className="etiqueta">{m.tipo}</span>
                          </td>
                          <td className="texto-derecha">
                            <strong
                              className={
                                esPositivo ? 'texto-exito' : 'texto-peligro'
                              }
                            >
                              {esPositivo ? '+' : ''}
                              {formatearNumero(m.cantidad)}
                            </strong>
                          </td>
                          <td>{m.usuarioUsername}</td>
                          <td>{m.observacion || '-'}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}

            <div className="modal__pie">
              <button
                type="button"
                className="btn btn--secundario"
                onClick={() => setInsumoHistorial(null)}
              >
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

export default Insumos
