import { useEffect, useState } from 'react'
import Modal from '../Modal.jsx'
import { listarMovimientosInsumo } from '../../services/insumosService.js'
import { formatearFecha, formatearNumero } from '../../utils/formatters.js'

function HistorialMovimientosModal({ insumo, onCerrar }) {
  const [movimientos, setMovimientos] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelado = false
    listarMovimientosInsumo(insumo.id)
      .then((lista) => {
        if (!cancelado) setMovimientos(lista)
      })
      .catch((err) => {
        if (!cancelado) {
          setError(
            err.message || 'No se pudo obtener el historial de movimientos.',
          )
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })

    return () => {
      cancelado = true
    }
  }, [insumo.id])

  return (
    <Modal
      abierto
      ancho
      titulo={`Historial de movimientos: ${insumo.nombre} (${insumo.unidadMedida})`}
      onCerrar={onCerrar}
    >
      {error && <div className="alerta alerta--error">{error}</div>}

      {cargando ? (
        <p className="aviso">Cargando movimientos...</p>
      ) : movimientos.length === 0 ? (
        <p className="aviso">
          Este insumo aún no registra movimientos en el historial.
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
          onClick={onCerrar}
        >
          Cerrar
        </button>
      </div>
    </Modal>
  )
}

export default HistorialMovimientosModal
