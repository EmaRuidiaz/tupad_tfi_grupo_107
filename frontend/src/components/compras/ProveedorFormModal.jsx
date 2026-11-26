import { useEffect, useState } from 'react'
import Modal from '../Modal.jsx'
import { actualizarProveedor, crearProveedor } from '../../services/comprasService.js'

const FORM_VACIO = {
  nombre: '',
  cuit: '',
  telefono: '',
  email: '',
}

function ProveedorFormModal({ proveedor, onCerrar, onGuardado }) {
  const esEdicion = Boolean(proveedor?.id)
  const [form, setForm] = useState(FORM_VACIO)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (proveedor) {
      setForm({
        nombre: proveedor.nombre || '',
        cuit: proveedor.cuit || '',
        telefono: proveedor.telefono || '',
        email: proveedor.email || '',
      })
    } else {
      setForm(FORM_VACIO)
    }
    setError('')
  }, [proveedor])

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')

    if (!form.nombre.trim()) {
      setError('Ingresá el nombre del proveedor.')
      return
    }
    if (!form.cuit.trim()) {
      setError('Ingresá el CUIT del proveedor.')
      return
    }

    const payload = {
      nombre: form.nombre.trim(),
      cuit: form.cuit.trim(),
      telefono: form.telefono.trim() || null,
      email: form.email.trim() || null,
    }

    setGuardando(true)
    try {
      if (esEdicion) {
        await actualizarProveedor(proveedor.id, payload)
        onGuardado(`Proveedor "${payload.nombre}" actualizado.`)
      } else {
        await crearProveedor(payload)
        onGuardado(`Proveedor "${payload.nombre}" registrado.`)
      }
    } catch (err) {
      setError(err.message || 'No se pudo guardar el proveedor.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      titulo={esEdicion ? `Editar proveedor #${proveedor.id}` : 'Nuevo proveedor'}
      onCerrar={onCerrar}
    >
      {error && (
        <div className="alerta alerta--error" role="alert">
          {error}
        </div>
      )}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="campo">
          <label htmlFor="prov-nombre">Nombre / Razón social</label>
          <input
            id="prov-nombre"
            type="text"
            required
            maxLength={120}
            value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
          />
        </div>

        <div className="campo">
          <label htmlFor="prov-cuit">CUIT</label>
          <input
            id="prov-cuit"
            type="text"
            required
            maxLength={20}
            placeholder="30-12345678-9"
            value={form.cuit}
            onChange={(e) => setForm({ ...form, cuit: e.target.value })}
          />
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="prov-telefono">Teléfono</label>
            <input
              id="prov-telefono"
              type="text"
              maxLength={40}
              value={form.telefono}
              onChange={(e) => setForm({ ...form, telefono: e.target.value })}
            />
          </div>

          <div className="campo">
            <label htmlFor="prov-email">Email</label>
            <input
              id="prov-email"
              type="email"
              maxLength={120}
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </div>
        </div>

        <div className="modal__pie">
          <button type="button" className="btn btn--secundario" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--primario" disabled={guardando}>
            {guardando ? 'Guardando...' : esEdicion ? 'Guardar cambios' : 'Crear proveedor'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default ProveedorFormModal
