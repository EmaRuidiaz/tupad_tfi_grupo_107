import { useState } from 'react'
import Modal from '../Modal.jsx'
import { actualizarInsumo, crearInsumo } from '../../services/insumosService.js'
import {
  CATEGORIAS_INSUMO,
  UNIDADES_INSUMO,
  formatearNumero,
} from '../../utils/formatters.js'

function estadoInicial(insumo) {
  if (!insumo) {
    return {
      nombre: '',
      categoria: 'SEMILLA',
      unidadMedida: 'BOLSAS',
      stockInicial: '0',
      stockMinimoAlerta: '0',
      precioUnitario: '0',
    }
  }
  return {
    nombre: insumo.nombre,
    categoria: insumo.categoria,
    unidadMedida: insumo.unidadMedida,
    stockInicial: '0',
    stockMinimoAlerta: String(insumo.stockMinimoAlerta ?? 0),
    precioUnitario: String(insumo.precioUnitario ?? 0),
  }
}

function InsumoFormModal({ insumo, onCerrar, onGuardado }) {
  const [form, setForm] = useState(() => estadoInicial(insumo))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setGuardando(true)

    const payload = {
      nombre: form.nombre.trim(),
      categoria: form.categoria,
      unidadMedida: form.unidadMedida,
      stockMinimoAlerta: Number(form.stockMinimoAlerta),
      precioUnitario: Number(form.precioUnitario),
      ...(insumo ? {} : { stockInicial: Number(form.stockInicial || 0) }),
    }

    try {
      if (insumo) {
        await actualizarInsumo(insumo.id, payload)
        onGuardado(`Insumo "${payload.nombre}" actualizado correctamente.`)
      } else {
        await crearInsumo(payload)
        onGuardado(`Insumo "${payload.nombre}" registrado correctamente.`)
      }
    } catch (err) {
      setError(err.message || 'No se pudo guardar el insumo.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      titulo={insumo ? 'Editar insumo' : 'Registrar nuevo insumo'}
      onCerrar={onCerrar}
    >
      {error && <div className="alerta alerta--error">{error}</div>}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="campo">
          <label htmlFor="insumo-nombre">Nombre comercial</label>
          <input
            id="insumo-nombre"
            type="text"
            maxLength={120}
            required
            value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
            placeholder="Ej: Semilla Soja Don Mario 46i20"
          />
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="insumo-categoria">Categoría</label>
            <select
              id="insumo-categoria"
              value={form.categoria}
              onChange={(e) => setForm({ ...form, categoria: e.target.value })}
            >
              {CATEGORIAS_INSUMO.map((c) => (
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
              value={form.unidadMedida}
              onChange={(e) =>
                setForm({ ...form, unidadMedida: e.target.value })
              }
            >
              {UNIDADES_INSUMO.map((u) => (
                <option key={u.valor} value={u.valor}>
                  {u.etiqueta}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="fila-campos">
          {!insumo && (
            <div className="campo">
              <label htmlFor="insumo-stock-inicial">Stock inicial</label>
              <input
                id="insumo-stock-inicial"
                type="number"
                step="0.01"
                min="0"
                required
                value={form.stockInicial}
                onChange={(e) =>
                  setForm({ ...form, stockInicial: e.target.value })
                }
              />
            </div>
          )}

          <div className="campo">
            <label htmlFor="insumo-stock-minimo">Stock mínimo de alerta</label>
            <input
              id="insumo-stock-minimo"
              type="number"
              step="0.01"
              min="0"
              required
              value={form.stockMinimoAlerta}
              onChange={(e) =>
                setForm({ ...form, stockMinimoAlerta: e.target.value })
              }
            />
          </div>

          <div className="campo">
            <label htmlFor="insumo-precio">Precio unitario ($)</label>
            <input
              id="insumo-precio"
              type="number"
              step="0.01"
              min="0"
              required
              value={form.precioUnitario}
              onChange={(e) =>
                setForm({ ...form, precioUnitario: e.target.value })
              }
            />
          </div>
        </div>

        {insumo && (
          <p className="texto-suave texto-chico">
            Nota: El stock físico actual ({formatearNumero(insumo.stockActual)}{' '}
            {insumo.unidadMedida}) sólo se modifica mediante compras, labores o
            el botón &laquo;Ajustar&raquo; para mantener el historial de
            movimientos.
          </p>
        )}

        <div className="modal__pie">
          <button
            type="button"
            className="btn btn--secundario"
            onClick={onCerrar}
          >
            Cancelar
          </button>
          <button
            type="submit"
            className="btn btn--primario"
            disabled={guardando}
          >
            {guardando ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default InsumoFormModal
