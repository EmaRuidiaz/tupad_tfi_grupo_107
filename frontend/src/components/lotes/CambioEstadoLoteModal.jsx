import { useState } from 'react'
import Modal from '../Modal.jsx'
import { cambiarEstadoLote } from '../../services/lotesService.js'
import { etiquetaEstadoLote } from '../../utils/formatters.js'

function esPerdidaTotal(actual, destino) {
  return (
    (actual === 'SEMBRADO' || actual === 'EN_CRECIMIENTO') &&
    destino === 'EN_PREPARACION'
  )
}

function CambioEstadoLoteModal({ lote, onCerrar, onGuardado }) {
  const opciones = [...(lote.transicionesPermitidas ?? [])].sort(
    (a, b) =>
      Number(esPerdidaTotal(lote.estado, a)) -
      Number(esPerdidaTotal(lote.estado, b)),
  )
  const [destino, setDestino] = useState(opciones[0] ?? '')
  const [observacion, setObservacion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  const requiereObservacion = esPerdidaTotal(lote.estado, destino)

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setGuardando(true)

    try {
      await cambiarEstadoLote(lote.id, {
        estado: destino,
        observacion: observacion.trim() || null,
      })
      onGuardado(
        `Lote "${lote.nombre}" pasó a "${etiquetaEstadoLote(destino)}".`,
      )
    } catch (err) {
      setError(err.message || 'No se pudo cambiar el estado del lote.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal abierto titulo={`Cambiar estado: ${lote.nombre}`} onCerrar={onCerrar}>
      {error && <div className="alerta alerta--error">{error}</div>}

      <div className="resumen-ajuste">
        <div>
          <span>Estado actual:</span>{' '}
          <strong>{etiquetaEstadoLote(lote.estado)}</strong>
        </div>
      </div>

      {opciones.length === 0 ? (
        <>
          <p className="aviso">
            {lote.estado === 'EN_PREPARACION'
              ? 'El lote pasará a "Sembrado" automáticamente al completarse una labor de siembra.'
              : lote.estado === 'LISTO_COSECHA'
                ? 'El lote pasará a "Cosechado" automáticamente al registrarse la cosecha.'
                : 'No hay cambios de estado manuales disponibles.'}
          </p>
          <div className="modal__pie">
            <button
              type="button"
              className="btn btn--secundario"
              onClick={onCerrar}
            >
              Cerrar
            </button>
          </div>
        </>
      ) : (
        <form onSubmit={manejarSubmit} className="formulario">
          <div className="campo">
            <label htmlFor="estado-destino">Nuevo estado</label>
            <select
              id="estado-destino"
              value={destino}
              onChange={(e) => setDestino(e.target.value)}
            >
              {opciones.map((estado) => (
                <option key={estado} value={estado}>
                  {etiquetaEstadoLote(estado)}
                  {esPerdidaTotal(lote.estado, estado)
                    ? ' (pérdida total del cultivo)'
                    : ''}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="estado-observacion">
              Observación{requiereObservacion ? '' : ' (opcional)'}
            </label>
            <textarea
              id="estado-observacion"
              rows={3}
              required={requiereObservacion}
              placeholder={
                requiereObservacion
                  ? 'Ej: Granizo destruyó el cultivo'
                  : 'Se agrega a las observaciones del lote'
              }
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
              {guardando ? 'Guardando...' : 'Confirmar cambio'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}

export default CambioEstadoLoteModal
