import { useCallback, useEffect, useMemo, useState } from 'react'
import ProveedorFormModal from '../components/compras/ProveedorFormModal.jsx'
import { getSession, isAdmin } from '../services/api.js'
import { eliminarProveedor, listarProveedores } from '../services/comprasService.js'

function Proveedores() {
  const sesion = getSession()
  const puedeGestionar = isAdmin()

  const [proveedores, setProveedores] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorGlobal, setErrorGlobal] = useState('')
  const [mensajeExito, setMensajeExito] = useState('')
  const [busqueda, setBusqueda] = useState('')

  const [modalAbierto, setModalAbierto] = useState(false)
  const [proveedorEditando, setProveedorEditando] = useState(null)
  const [eliminandoId, setEliminandoId] = useState(null)

  const recargar = useCallback(async (esCancelado = () => false) => {
    if (!esCancelado()) setErrorGlobal('')
    try {
      const datos = await listarProveedores()
      if (!esCancelado()) {
        setProveedores(datos)
      }
    } catch (err) {
      if (!esCancelado()) {
        setErrorGlobal(err.message || 'No se pudieron cargar los proveedores.')
      }
    } finally {
      if (!esCancelado()) {
        setCargando(false)
      }
    }
  }, [])

  useEffect(() => {
    let cancelado = false
    recargar(() => cancelado)
    return () => {
      cancelado = true
    }
  }, [recargar])

  const proveedoresFiltrados = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    if (!q) return proveedores
    return proveedores.filter(
      (p) =>
        (p.nombre || '').toLowerCase().includes(q) ||
        (p.cuit || '').toLowerCase().includes(q) ||
        (p.email || '').toLowerCase().includes(q),
    )
  }, [proveedores, busqueda])

  async function manejarEliminar(proveedor) {
    setMensajeExito('')
    setErrorGlobal('')
    setEliminandoId(proveedor.id)
    try {
      await eliminarProveedor(proveedor.id)
      setMensajeExito(`Proveedor "${proveedor.nombre}" eliminado.`)
      await recargar()
    } catch (err) {
      setErrorGlobal(err.message || 'No se pudo eliminar el proveedor.')
    } finally {
      setEliminandoId(null)
    }
  }

  return (
    <section className="pagina">
      <div className="pagina__encabezado">
        <div>
          <h1>Proveedores</h1>
          <p className="pagina__subtitulo">
            Catálogo de proveedores de insumos agrícolas para registro de compras.
          </p>
        </div>
        <div className="pagina__acciones">
          {puedeGestionar ? (
            <button
              type="button"
              className="btn btn--primario"
              onClick={() => {
                setMensajeExito('')
                setProveedorEditando(null)
                setModalAbierto(true)
              }}
            >
              + Nuevo proveedor
            </button>
          ) : (
            <span className="etiqueta etiqueta--info">Modo consulta ({sesion?.rol})</span>
          )}
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
          placeholder="Buscar por nombre, CUIT o email..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />
      </div>

      {cargando ? (
        <p className="aviso">Cargando proveedores...</p>
      ) : proveedoresFiltrados.length === 0 ? (
        <p className="aviso">
          {proveedores.length === 0
            ? 'Todavía no hay proveedores cargados.'
            : 'No hay proveedores que coincidan con la búsqueda.'}
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>CUIT</th>
                <th>Teléfono</th>
                <th>Email</th>
                {puedeGestionar && <th>Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {proveedoresFiltrados.map((p) => (
                <tr key={p.id}>
                  <td>
                    <strong>{p.nombre}</strong>
                  </td>
                  <td>{p.cuit}</td>
                  <td>{p.telefono || '-'}</td>
                  <td>{p.email || '-'}</td>
                  {puedeGestionar && (
                    <td>
                      <div className="acciones-celda">
                        <button
                          type="button"
                          className="btn btn--secundario btn--chico"
                          onClick={() => {
                            setMensajeExito('')
                            setProveedorEditando(p)
                            setModalAbierto(true)
                          }}
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          className="btn btn--peligro btn--chico"
                          disabled={eliminandoId === p.id}
                          onClick={() => manejarEliminar(p)}
                        >
                          {eliminandoId === p.id ? 'Eliminando...' : 'Eliminar'}
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

      {modalAbierto && (
        <ProveedorFormModal
          proveedor={proveedorEditando}
          onCerrar={() => {
            setModalAbierto(false)
            setProveedorEditando(null)
          }}
          onGuardado={async (mensaje) => {
            setModalAbierto(false)
            setProveedorEditando(null)
            setMensajeExito(mensaje)
            await recargar()
          }}
        />
      )}
    </section>
  )
}

export default Proveedores
