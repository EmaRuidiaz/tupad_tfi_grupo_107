import { useEffect, useMemo, useState } from 'react'
import Modal from '../Modal.jsx'
import { registrarCompra } from '../../services/comprasService.js'
import { listarInsumos } from '../../services/insumosService.js'
import { formatearNumero } from '../../utils/formatters.js'

function fechaHoraActualLocal() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function CompraFormModal({ proveedores, onCerrar, onGuardado }) {
  const [form, setForm] = useState({
    proveedorId: '',
    fechaCompra: fechaHoraActualLocal(),
    detalles: [],
  })
  const [insumos, setInsumos] = useState([])
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelado = false
    listarInsumos()
      .then((datos) => { if (!cancelado) setInsumos(datos) })
      .catch(() => { if (!cancelado) setInsumos([]) })
    return () => { cancelado = true }
  }, [])

  const idsElegidos = useMemo(
    () => new Set(form.detalles.map((d) => String(d.insumoId)).filter(Boolean)),
    [form.detalles],
  )

  function agregarLinea() {
    const disponible = insumos.find((i) => !idsElegidos.has(String(i.id)))
    if (!disponible) return
    setForm((prev) => ({
      ...prev,
      detalles: [
        ...prev.detalles,
        {
          uid: `${Date.now()}-${Math.random()}`,
          insumoId: String(disponible.id),
          cantidad: '',
          precioUnitario: String(disponible.precioUnitario ?? ''),
        },
      ],
    }))
  }

  function actualizarLinea(uid, campo, valor) {
    setForm((prev) => ({
      ...prev,
      detalles: prev.detalles.map((d) => (d.uid === uid ? { ...d, [campo]: valor } : d)),
    }))
  }

  function quitarLinea(uid) {
    setForm((prev) => ({ ...prev, detalles: prev.detalles.filter((d) => d.uid !== uid) }))
  }

  const resumen = useMemo(() => {
    const lineas = form.detalles.map((d) => {
      const insumo = insumos.find((i) => String(i.id) === String(d.insumoId))
      const cantidad = Number(d.cantidad)
      const precio = Number(d.precioUnitario)
      const subtotal = Number.isFinite(cantidad) && Number.isFinite(precio) ? cantidad * precio : 0
      return { ...d, insumo, subtotal }
    })
    const total = lineas.reduce((acc, l) => acc + l.subtotal, 0)
    return { lineas, total }
  }, [form.detalles, insumos])

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    if (!form.proveedorId) { setError('Seleccioná un proveedor.'); return }
    if (form.detalles.length === 0) { setError('Agregá al menos un insumo.'); return }
    for (const d of form.detalles) {
      if (!d.insumoId || Number(d.cantidad) <= 0 || Number(d.precioUnitario) < 0) {
        setError('Revisá cantidades y precios de todas las líneas.'); return
      }
    }
    setGuardando(true)
    try {
      const fechaNormalizada =
        form.fechaCompra.length === 16 ? `${form.fechaCompra}:00` : form.fechaCompra
      const creada = await registrarCompra({
        proveedorId: Number(form.proveedorId),
        fechaCompra: fechaNormalizada,
        detalles: form.detalles.map((d) => ({
          insumoId: Number(d.insumoId),
          cantidad: Number(d.cantidad),
          precioUnitario: Number(d.precioUnitario),
        })),
      })
      onGuardado(
        `Compra #${creada.id} registrada. Total: $ ${formatearNumero(creada.totalCompra)}. Stock actualizado.`,
      )
    } catch (err) {
      setError(err.message || 'No se pudo registrar la compra.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal abierto ancho titulo="Registrar compra" onCerrar={onCerrar}>
      {error && <div className="alerta alerta--error">{error}</div>}
      <form onSubmit={manejarSubmit} className="formulario">
        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="compra-proveedor">Proveedor</label>
            <select
              id="compra-proveedor"
              required
              value={form.proveedorId}
              onChange={(e) => setForm({ ...form, proveedorId: e.target.value })}
            >
              <option value="">Seleccionar proveedor...</option>
              {proveedores.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nombre} ({p.cuit})
                </option>
              ))}
            </select>
          </div>
          <div className="campo">
            <label htmlFor="compra-fecha">Fecha y hora de compra</label>
            <input
              id="compra-fecha"
              type="datetime-local"
              required
              value={form.fechaCompra}
              onChange={(e) => setForm({ ...form, fechaCompra: e.target.value })}
            />
          </div>
        </div>

        <div className="campo">
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '0.5rem',
              flexWrap: 'wrap',
            }}
          >
            <label>Insumos comprados</label>
            <button
              type="button"
              className="btn btn--secundario btn--chico"
              onClick={agregarLinea}
              disabled={form.detalles.length >= insumos.length}
            >
              + Agregar insumo
            </button>
          </div>

          {form.detalles.length === 0 ? (
            <p className="aviso" style={{ margin: '0.25rem 0 0' }}>
              Sin líneas todavía. Usá «+ Agregar insumo».
            </p>
          ) : (
            <div className="tabla-contenedor" style={{ marginTop: '0.35rem' }}>
              <table className="tabla">
                <thead>
                  <tr>
                    <th>Insumo</th>
                    <th>Cantidad</th>
                    <th>Precio unit. ($)</th>
                    <th className="texto-derecha">Subtotal</th>
                    <th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {resumen.lineas.map((d) => (
                    <tr key={d.uid}>
                      <td>
                        <select
                          aria-label="Insumo"
                          value={d.insumoId}
                          onChange={(e) => actualizarLinea(d.uid, 'insumoId', e.target.value)}
                          style={{ width: '100%' }}
                        >
                          {insumos.map((i) => {
                            const usado =
                              String(i.id) !== String(d.insumoId) && idsElegidos.has(String(i.id))
                            return (
                              <option key={i.id} value={i.id} disabled={usado}>
                                {i.nombre} ({i.unidadMedida})
                              </option>
                            )
                          })}
                        </select>
                      </td>
                      <td>
                        <input
                          type="number"
                          step="0.01"
                          min="0.01"
                          required
                          aria-label="Cantidad"
                          value={d.cantidad}
                          onChange={(e) => actualizarLinea(d.uid, 'cantidad', e.target.value)}
                          style={{ width: '110px' }}
                        />
                      </td>
                      <td>
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          required
                          aria-label="Precio unitario"
                          value={d.precioUnitario}
                          onChange={(e) => actualizarLinea(d.uid, 'precioUnitario', e.target.value)}
                          style={{ width: '120px' }}
                        />
                      </td>
                      <td className="texto-derecha">$ {formatearNumero(d.subtotal)}</td>
                      <td>
                        <button
                          type="button"
                          className="btn btn--peligro btn--chico"
                          onClick={() => quitarLinea(d.uid)}
                        >
                          Quitar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="resumen-ajuste">
          <div>
            <strong>Líneas:</strong> {form.detalles.length}
          </div>
          <div>
            <strong>Total:</strong> $ {formatearNumero(resumen.total)}
          </div>
        </div>

        <div className="modal__pie">
          <button type="button" className="btn btn--secundario" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--primario" disabled={guardando}>
            {guardando ? 'Registrando...' : 'Registrar compra'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default CompraFormModal