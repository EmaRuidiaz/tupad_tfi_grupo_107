import { useCallback, useEffect, useMemo, useState } from 'react'
import CompraFormModal from '../components/compras/CompraFormModal.jsx'
import { getSession, isAdmin } from '../services/api.js'
import { listarCompras, listarProveedores } from '../services/comprasService.js'
import { formatearFecha, formatearNumero } from '../utils/formatters.js'

function Compras() {
  const sesion = getSession()
  const puedeRegistrar = isAdmin()

  const [compras, setCompras] = useState([])
  const [proveedores, setProveedores] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')

  const [proveedorFiltro, setProveedorFiltro] = useState('TODOS')
  const [busqueda, setBusqueda] = useState('')
  const [modalAbierto, setModalAbierto] = useState(false)

  const recargar = useCallback(async () => {
    setErrorGlobal('')
    try {
      const [listaCompras, listaProv] = await Promise.all([
        listarCompras(),
        listarProveedores(),
      ])
      setCompras(listaCompras)
      setProveedores(listaProv)
    } catch (err) {
      setErrorGlobal(err.message || 'No se pudieron cargar las compras.')
    } finally {
      setCargando(false)
    }
  }, [])

  useEffect(() => {
    recargar()
  }, [recargar])

  const comprasFiltradas = useMemo(() => {
    const texto = busqueda.trim().toLowerCase()
    return compras.filter((c) => {
      if (proveedorFiltro !== 'TODOS' && String(c.proveedorId) !== proveedorFiltro) return false
      if (
        texto &&
        !c.proveedorNombre.toLowerCase().includes(texto) &&
        !c.proveedorCuit.toLowerCase().includes(texto)
      ) {
        return false
      }
      return true
    })
  }, [compras, proveedorFiltro, busqueda])

  const resumen = useMemo(() => {
    const total = comprasFiltradas.reduce((s, c) => s + Number(c.totalCompra || 0), 0)
    const lineas = comprasFiltradas.reduce((s, c) => s + (c.detalles?.length || 0), 0)
    return { cantidad: comprasFiltradas.length, total, lineas }
  }, [comprasFiltradas])

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Compras de Insumos</h1>
          <p className="pagina__subtitulo">
            Registro de facturas de proveedores con actualización automática de stock y precio
            unitario (CU-02 / RF-03).
          </p>
        </div>
        <div className="pagina__acciones">
          {puedeRegistrar ? (
            <button
              type="button"
              className="btn btn--primario"
              onClick={() => {
                setMensajeExito('')
                setModalAbierto(true)
              }}
            >
              + Registrar compra
            </button>
          ) : (
            <span className="etiqueta etiqueta--info">Modo consulta ({sesion?.rol})</span>
          )}
        </div>
      </div>

      <div className="tarjetas-resumen">
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Compras registradas</span>
          <strong className="tarjeta-kpi__valor">{resumen.cantidad}</strong>
          <span className="tarjeta-kpi__detalle">{resumen.lineas} línea(s) de detalle</span>
        </div>
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Monto total</span>
          <strong className="tarjeta-kpi__valor">$ {formatearNumero(resumen.total)}</strong>
          <span className="tarjeta-kpi__detalle">Compras visibles con los filtros actuales</span>
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
          placeholder="Buscar por proveedor o CUIT..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />
        <select
          className="select-filtro"
          value={proveedorFiltro}
          onChange={(e) => setProveedorFiltro(e.target.value)}
          aria-label="Filtrar por proveedor"
        >
          <option value="TODOS">Todos los proveedores</option>
          {proveedores.map((p) => (
            <option key={p.id} value={String(p.id)}>
              {p.nombre}
            </option>
          ))}
        </select>
      </div>

      {cargando ? (
        <p className="aviso">Cargando compras...</p>
      ) : comprasFiltradas.length === 0 ? (
        <p className="aviso">
          {compras.length === 0
            ? 'Todavía no hay compras registradas.'
            : 'No hay compras con los filtros seleccionados.'}
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Proveedor</th>
                <th>Responsable</th>
                <th className="texto-derecha">Líneas</th>
                <th className="texto-derecha">Total</th>
                <th>Detalle</th>
              </tr>
            </thead>
            <tbody>
              {comprasFiltradas.map((c) => (
                <tr key={c.id}>
                  <td>{formatearFecha(c.fechaCompra)}</td>
                  <td>
                    <strong>{c.proveedorNombre}</strong>
                    <div className="texto-suave texto-chico">CUIT: {c.proveedorCuit}</div>
                  </td>
                  <td>{c.usuarioUsername || '-'}</td>
                  <td className="texto-derecha">{c.detalles?.length || 0}</td>
                  <td className="texto-derecha">
                    <strong>$ {formatearNumero(c.totalCompra)}</strong>
                  </td>
                  <td>
                    <div className="acciones-celda">
                      <details>
                        <summary className="btn btn--secundario btn--chico">
                          Ver líneas
                        </summary>
                        <div style={{ marginTop: '0.5rem' }}>
                          <table className="tabla">
                            <thead>
                              <tr>
                                <th>Insumo</th>
                                <th className="texto-derecha">Cantidad</th>
                                <th className="texto-derecha">Precio</th>
                                <th className="texto-derecha">Subtotal</th>
                              </tr>
                            </thead>
                            <tbody>
                              {c.detalles.map((d) => (
                                <tr key={d.id}>
                                  <td>{d.insumoNombre}</td>
                                  <td className="texto-derecha">
                                    {formatearNumero(d.cantidad)} {d.unidadMedida}
                                  </td>
                                  <td className="texto-derecha">
                                    $ {formatearNumero(d.precioUnitario)}
                                  </td>
                                  <td className="texto-derecha">
                                    $ {formatearNumero(d.subtotal)}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </details>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalAbierto && (
        <CompraFormModal
          proveedores={proveedores}
          onCerrar={() => setModalAbierto(false)}
          onGuardado={async (mensaje) => {
            setModalAbierto(false)
            setMensajeExito(mensaje)
            await recargar()
          }}
        />
      )}
    </section>
  )
}

export default Compras