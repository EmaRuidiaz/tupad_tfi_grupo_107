import { useState } from 'react'
import Modal from '../Modal.jsx'
import { registrarAjusteStock } from '../../services/insumosService.js'
import { formatearNumero } from '../../utils/formatters.js'

function AjusteStockModal({ insumo, onCerrar, onGuardado }) {
  const [cantidad, setCantidad] = useState('')
  const [observacion, setObservacion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  const stockResultante = Number(insumo.stockActual) + Number(cantidad || 0)

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setGuardando(true)

    try {
      await registrarAjusteStock(insumo.id, {
        cantidad: Number(cantidad),
        observacion: observacion.trim(),
      })
      onGuardado(`Ajuste de stock aplicado sobre "${insumo.nombre}".`)
    } catch (err) {
      setError(err.message || 'No se pudo registrar el ajuste.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      titulo={`Ajustar stock: ${insumo.nombre}`}
      onCerrar={onCerrar}
    >
      {error && <div className="alerta alerta--error">{error}</div>}

      <div className="resumen-ajuste">
        <div>
          <span>Stock actual:</span>{' '}
          <strong>
            {formatearNumero(insumo.stockActual)} {insumo.unidadMedida}
          </strong>
        </div>
        <div>
          <span>Stock reservado:</span>{' '}
          <strong>
            {formatearNumero(insumo.stockReservado)} {insumo.unidadMedida}
          </strong>
        </div>
        <div>
          <span>Stock resultante:</span>{' '}
          <strong className={stockResultante < 0 ? 'texto-peligro' : ''}>
            {formatearNumero(stockResultante)} {insumo.unidadMedida}
          </strong>
        </div>
      </div>

      <form onSubmit={manejarSubmit} className="formulario">
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
            value={cantidad}
            onChange={(e) => setCantidad(e.target.value)}
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
            value={observacion}
            onChange={(e) => setObservacion(e.target.value)}
          />
        </div>

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
            {guardando ? 'Aplicando...' : 'Confirmar ajuste'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default AjusteStockModal
