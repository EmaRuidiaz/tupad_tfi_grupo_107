import { useState } from 'react'
import Modal from '../Modal.jsx'
import { reprogramarLabor } from '../../services/laboresService.js'
import {
  etiquetaEstadoLabor,
  etiquetaTipoLabor,
  formatearFecha,
} from '../../utils/formatters.js'

function aDatetimeLocal(iso) {
  if (!iso) return ''
  return String(iso).slice(0, 16)
}

function ReprogramarLaborModal({ labor, onCerrar, onGuardado }) {
  const [fechaPlanificada, setFechaPlanificada] = useState(() =>
    aDatetimeLocal(labor.fechaPlanificada),
  )
  const [observaciones, setObservaciones] = useState(
    () => labor.observacionesClimaticas ?? '',
  )
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setGuardando(true)

    const fechaNormalizada =
      fechaPlanificada.length === 16
        ? `${fechaPlanificada}:00`
        : fechaPlanificada

    try {
      await reprogramarLabor(labor.id, {
        fechaPlanificada: fechaNormalizada,
        observaciones: observaciones.trim() || null,
      })
      onGuardado(
        `Orden de trabajo #${labor.id} (${etiquetaTipoLabor(labor.tipoLabor)}) reprogramada correctamente.`,
      )
    } catch (err) {
      setError(err.message || 'No se pudo reprogramar la labor.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      titulo={`Reprogramar labor #${labor.id} (CU-08)`}
      onCerrar={onCerrar}
    >
      <div className="resumen-ajuste">
        <div>
          <strong>Lote:</strong> {labor.loteNombre}
        </div>
        <div>
          <strong>Labor:</strong> {etiquetaTipoLabor(labor.tipoLabor)}
        </div>
        <div>
          <strong>Estado actual:</strong> {etiquetaEstadoLabor(labor.estado)}
        </div>
        <div>
          <strong>Fecha previa:</strong> {formatearFecha(labor.fechaPlanificada)}
        </div>
      </div>

      {error && (
        <div className="alerta alerta--error" role="alert">
          {error}
        </div>
      )}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="campo">
          <label htmlFor="reprog-fecha">Nueva fecha y hora planificada</label>
          <input
            id="reprog-fecha"
            type="datetime-local"
            required
            value={fechaPlanificada}
            onChange={(e) => setFechaPlanificada(e.target.value)}
          />
          <span className="texto-suave texto-chico">
            La labor volverá al estado &laquo;Planificada&raquo; conservando
            todas sus reservas de insumos.
          </span>
        </div>

        <div className="campo">
          <label htmlFor="reprog-obs">Observaciones</label>
          <textarea
            id="reprog-obs"
            rows={3}
            maxLength={1000}
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            placeholder="Motivo de reprogramación o notas para el operario..."
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
            {guardando ? 'Guardando...' : 'Guardar nueva fecha'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default ReprogramarLaborModal
