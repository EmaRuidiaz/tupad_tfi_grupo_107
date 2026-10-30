import Modal from '../Modal.jsx'
import {
  etiquetaEstadoLabor,
  etiquetaEstadoLote,
  etiquetaEstadoReserva,
  etiquetaTipoLabor,
  formatearFecha,
  formatearNumero,
} from '../../utils/formatters.js'

function claseEtiquetaReserva(estadoReserva) {
  if (estadoReserva === 'CONSUMIDO') return 'etiqueta etiqueta--ok'
  if (estadoReserva === 'RESERVADO') return 'etiqueta etiqueta--info'
  return 'etiqueta'
}

function DetalleLaborModal({ labor, onCerrar }) {
  if (!labor) return null

  const tieneClimaRegistrado =
    labor.temperaturaRegistrada != null ||
    labor.vientoVelocidadKmh != null ||
    labor.probabilidadLluviaPct != null ||
    labor.humedadRegistradaPct != null

  return (
    <Modal
      abierto
      ancho
      titulo={`Detalle de orden de trabajo #${labor.id}`}
      onCerrar={onCerrar}
    >
      <div className="resumen-ajuste">
        <div>
          <strong>Tipo de labor:</strong> {etiquetaTipoLabor(labor.tipoLabor)}
        </div>
        <div>
          <strong>Estado:</strong> {etiquetaEstadoLabor(labor.estado)}
        </div>
        <div>
          <strong>Lote:</strong> {labor.loteNombre} (
          {formatearNumero(labor.loteSuperficieHa)} ha ·{' '}
          {etiquetaEstadoLote(labor.loteEstado)})
        </div>
        <div>
          <strong>Responsable:</strong> {labor.usuarioUsername || '-'}
        </div>
        <div>
          <strong>Fecha planificada:</strong>{' '}
          {formatearFecha(labor.fechaPlanificada)}
        </div>
        <div>
          <strong>Fecha de ejecución:</strong>{' '}
          {formatearFecha(labor.fechaEjecucion)}
        </div>
        <div>
          <strong>Maquinaria:</strong>{' '}
          {labor.maquinariaNombre
            ? `${labor.maquinariaNombre} (${labor.maquinariaMatriculaOSerie})`
            : 'Sin maquinaria (labor manual)'}
        </div>
        {labor.estado === 'COMPLETADA' ? (
          <div>
            <strong>Costo total consolidado (RN-05):</strong> ${' '}
            {formatearNumero(labor.costoTotalCalculado)} (
            {formatearNumero(labor.horasMaquinaConsumidas)} hs máquina)
          </div>
        ) : (
          <div>
            <strong>Costo estimado de insumos:</strong> ${' '}
            {formatearNumero(labor.costoInsumosEstimado)}
          </div>
        )}
      </div>

      {tieneClimaRegistrado && (
        <div className="resumen-ajuste">
          <div>
            <strong>Clima registrado:</strong>
          </div>
          <div>Temp: {formatearNumero(labor.temperaturaRegistrada)} °C</div>
          <div>Viento: {formatearNumero(labor.vientoVelocidadKmh)} km/h</div>
          <div>
            Prob. lluvia: {formatearNumero(labor.probabilidadLluviaPct)}%
          </div>
          <div>Humedad: {formatearNumero(labor.humedadRegistradaPct)}%</div>
        </div>
      )}

      {labor.observacionesClimaticas && (
        <div className="resumen-ajuste">
          <div>
            <strong>Observaciones:</strong> {labor.observacionesClimaticas}
          </div>
        </div>
      )}

      <h3 style={{ margin: '0.5rem 0', fontSize: '1rem' }}>
        Insumos asignados ({labor.insumos?.length || 0})
      </h3>

      {!labor.insumos || labor.insumos.length === 0 ? (
        <p className="aviso">
          Esta orden de trabajo no tiene insumos asociados.
        </p>
      ) : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead>
              <tr>
                <th>Insumo</th>
                <th className="texto-derecha">Cantidad</th>
                <th>Estado reserva</th>
                <th className="texto-derecha">
                  {labor.estado === 'COMPLETADA'
                    ? 'Subtotal consolidado'
                    : 'Subtotal estimado'}
                </th>
              </tr>
            </thead>
            <tbody>
              {labor.insumos.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.insumoNombre}</strong>
                    <div className="texto-suave texto-chico">
                      Precio unit. ref.: ${' '}
                      {formatearNumero(item.insumoPrecioUnitario)} /{' '}
                      {item.insumoUnidadMedida}
                    </div>
                  </td>
                  <td className="texto-derecha">
                    <strong>{formatearNumero(item.cantidadUtilizada)}</strong>{' '}
                    <span className="texto-suave texto-chico">
                      {item.insumoUnidadMedida}
                    </span>
                  </td>
                  <td>
                    <span className={claseEtiquetaReserva(item.estadoReserva)}>
                      {etiquetaEstadoReserva(item.estadoReserva)}
                    </span>
                  </td>
                  <td className="texto-derecha">
                    ${' '}
                    {formatearNumero(
                      item.estadoReserva === 'CONSUMIDO'
                        ? item.costoSubtotal
                        : item.costoEstimado,
                    )}
                  </td>
                </tr>
              ))}
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

export default DetalleLaborModal
