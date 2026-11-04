import { useMemo, useState } from 'react'
import Modal from '../Modal.jsx'
import {
  etiquetaEstadoLote,
  etiquetaTipoLabor,
  formatearFecha,
  formatearFechaSimple,
  formatearNumero,
} from '../../utils/formatters.js'

function claseBadgeRoi(reporte) {
  if (!reporte) return 'etiqueta'
  if (reporte.estadoRoi === 'CALCULADO') {
    return Number(reporte.roiPct) >= 0
      ? 'etiqueta etiqueta--ok'
      : 'etiqueta etiqueta--critica'
  }
  if (reporte.estadoRoi === 'PENDIENTE') {
    return 'etiqueta etiqueta--advertencia'
  }
  return 'etiqueta etiqueta--info'
}

function DetalleFinancieroLoteModal({ reporte, onCerrar }) {
  const superficie = Number(reporte?.superficieHa || 0)
  const costosAcumulados = Number(reporte?.costosTotalesAcumulados || 0)

  const [rindeSimuladoHa, setRindeSimuladoHa] = useState('3.50')
  const [precioSimuladoTon, setPrecioSimuladoTon] = useState('300.00')

  const simulacion = useMemo(() => {
    const rindeHa = Number(rindeSimuladoHa)
    const precioTon = Number(precioSimuladoTon)
    if (
      !Number.isFinite(rindeHa) ||
      rindeHa < 0 ||
      !Number.isFinite(precioTon) ||
      precioTon <= 0 ||
      superficie <= 0
    ) {
      return null
    }
    const toneladasProyectadas = rindeHa * superficie
    const ingresoBrutoProyectado = toneladasProyectadas * precioTon
    const margenNetoProyectado = ingresoBrutoProyectado - costosAcumulados
    const roiProyectado =
      costosAcumulados > 0
        ? (margenNetoProyectado / costosAcumulados) * 100
        : null
    const toneladasEquilibrio =
      costosAcumulados > 0 ? costosAcumulados / precioTon : 0
    const rindeEquilibrioHa =
      superficie > 0 ? toneladasEquilibrio / superficie : 0

    return {
      toneladasProyectadas,
      ingresoBrutoProyectado,
      margenNetoProyectado,
      roiProyectado,
      toneladasEquilibrio,
      rindeEquilibrioHa,
    }
  }, [rindeSimuladoHa, precioSimuladoTon, superficie, costosAcumulados])

  if (!reporte) return null

  const porcentajeInsumos =
    costosAcumulados > 0
      ? (Number(reporte.costoInsumosAcumulado || 0) / costosAcumulados) * 100
      : 0
  const porcentajeMaquinaria =
    costosAcumulados > 0
      ? (Number(reporte.costoMaquinariaAcumulado || 0) / costosAcumulados) * 100
      : 0

  return (
    <Modal
      abierto
      ancho
      titulo={`Desglose financiero · ${reporte.loteNombre}`}
      onCerrar={onCerrar}
    >
      <div className="resumen-ajuste">
        <div>
          <strong>Superficie:</strong> {formatearNumero(reporte.superficieHa)}{' '}
          ha
        </div>
        <div>
          <strong>Estado agronómico:</strong>{' '}
          {etiquetaEstadoLote(reporte.estadoLote)}
        </div>
        <div>
          <strong>Tipo de suelo:</strong> {reporte.tipoSuelo || '-'}
        </div>
        <div>
          <strong>Labores completadas:</strong>{' '}
          {reporte.cantidadLaboresCompletadas} (
          {formatearNumero(reporte.horasMaquinaTotales)} hs de máquina)
        </div>
        {reporte.cantidadLaboresPendientes > 0 && (
          <div>
            <strong>Órdenes pendientes:</strong>{' '}
            {reporte.cantidadLaboresPendientes} (no suman hasta completarse)
          </div>
        )}
      </div>

      <div className="tarjetas-resumen" style={{ marginBottom: '1rem' }}>
        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Costos acumulados (RN-05)</span>
          <strong className="tarjeta-kpi__valor">
            $ {formatearNumero(reporte.costosTotalesAcumulados)}
          </strong>
          <span className="tarjeta-kpi__detalle">
            $ {formatearNumero(reporte.costoPorHa)} / ha · Insumos{' '}
            {formatearNumero(porcentajeInsumos)}% / Maq.{' '}
            {formatearNumero(porcentajeMaquinaria)}%
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Ingreso bruto</span>
          <strong className="tarjeta-kpi__valor">
            {reporte.cosechaRegistrada
              ? `$ ${formatearNumero(reporte.ingresoBrutoTotal)}`
              : 'Pendiente'}
          </strong>
          <span className="tarjeta-kpi__detalle">
            {reporte.cosechaRegistrada
              ? `${formatearNumero(reporte.toneladasTotales)} t (${formatearNumero(reporte.rindeTonPorHa)} t/ha) · $ ${formatearNumero(reporte.ingresoPorHa)}/ha`
              : 'Sin cosecha registrada en este lote'}
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Margen neto</span>
          <strong
            className={`tarjeta-kpi__valor ${
              reporte.cosechaRegistrada
                ? Number(reporte.margenNeto) >= 0
                  ? 'texto-exito'
                  : 'texto-peligro'
                : ''
            }`}
          >
            {reporte.cosechaRegistrada
              ? `$ ${formatearNumero(reporte.margenNeto)}`
              : 'Pendiente'}
          </strong>
          <span className="tarjeta-kpi__detalle">
            {reporte.cosechaRegistrada
              ? `$ ${formatearNumero(reporte.margenPorHa)} / ha (Ingreso - Costos)`
              : 'Se calcula al registrar la cosecha'}
          </span>
        </div>

        <div className="tarjeta-kpi">
          <span className="tarjeta-kpi__titulo">Retorno de Inversión (ROI)</span>
          <div>
            <span className={claseBadgeRoi(reporte)}>
              {reporte.estadoRoi === 'CALCULADO'
                ? `${formatearNumero(reporte.roiPct)} %`
                : reporte.mensajeRoi}
            </span>
          </div>
          <span className="tarjeta-kpi__detalle">
            Fórmula: (Margen Neto / Costos) × 100
          </span>
        </div>
      </div>

      <h3 style={{ margin: '0.75rem 0 0.5rem', fontSize: '1rem' }}>
        Composición de costos por labores completadas (
        {reporte.laboresCompletadas?.length || 0})
      </h3>

      {!reporte.laboresCompletadas ||
      reporte.laboresCompletadas.length === 0 ? (
        <p className="aviso">
          Este lote aún no registra labores completadas con costos consolidados.
        </p>
      ) : (
        <div className="tabla-contenedor" style={{ marginBottom: '1rem' }}>
          <table className="tabla">
            <thead>
              <tr>
                <th>Orden / Labor</th>
                <th>Fecha ejecución</th>
                <th>Maquinaria / Horas</th>
                <th className="texto-derecha">Costo insumos</th>
                <th className="texto-derecha">Costo maquinaria</th>
                <th className="texto-derecha">Total consolidado</th>
              </tr>
            </thead>
            <tbody>
              {reporte.laboresCompletadas.map((lab) => (
                <tr key={lab.laborId}>
                  <td>
                    <strong>
                      #{lab.laborId} · {etiquetaTipoLabor(lab.tipoLabor)}
                    </strong>
                    <div className="texto-suave texto-chico">
                      Resp.: {lab.usuarioUsername || '-'}
                    </div>
                  </td>
                  <td className="texto-chico">
                    {formatearFecha(lab.fechaEjecucion || lab.fechaPlanificada)}
                  </td>
                  <td className="texto-chico">
                    {lab.maquinariaNombre ? (
                      <>
                        <div>{lab.maquinariaNombre}</div>
                        <div className="texto-suave">
                          {formatearNumero(lab.horasMaquinaConsumidas)} hs
                        </div>
                      </>
                    ) : (
                      <span className="texto-suave">Manual (0 hs)</span>
                    )}
                  </td>
                  <td className="texto-derecha">
                    $ {formatearNumero(lab.costoInsumos)}
                  </td>
                  <td className="texto-derecha">
                    $ {formatearNumero(lab.costoMaquinaria)}
                  </td>
                  <td className="texto-derecha">
                    <strong>
                      $ {formatearNumero(lab.costoTotalCalculado)}
                    </strong>
                  </td>
                </tr>
              ))}
              <tr>
                <td colSpan={3}>
                  <strong>Total acumulado en el lote</strong>
                </td>
                <td className="texto-derecha">
                  <strong>
                    $ {formatearNumero(reporte.costoInsumosAcumulado)}
                  </strong>
                </td>
                <td className="texto-derecha">
                  <strong>
                    $ {formatearNumero(reporte.costoMaquinariaAcumulado)}
                  </strong>
                </td>
                <td className="texto-derecha">
                  <strong>
                    $ {formatearNumero(reporte.costosTotalesAcumulados)}
                  </strong>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      <h3 style={{ margin: '0.75rem 0 0.5rem', fontSize: '1rem' }}>
        Registros de cosecha en el lote ({reporte.cosechas?.length || 0})
      </h3>

      {!reporte.cosechas || reporte.cosechas.length === 0 ? (
        <p className="aviso">
          Aún no se registraron cosechas en este lote (RF-08, criterio 2).
        </p>
      ) : (
        <div className="tabla-contenedor" style={{ marginBottom: '1rem' }}>
          <table className="tabla">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Cultivo</th>
                <th className="texto-derecha">Producción (t)</th>
                <th className="texto-derecha">Rinde (t/ha)</th>
                <th className="texto-derecha">Precio / t</th>
                <th className="texto-derecha">Ingreso bruto</th>
              </tr>
            </thead>
            <tbody>
              {reporte.cosechas.map((cos) => (
                <tr key={cos.cosechaId}>
                  <td>{formatearFechaSimple(cos.fechaCosecha)}</td>
                  <td>
                    <strong>{cos.cultivoNombre}</strong>
                    <div className="texto-suave texto-chico">
                      {cos.cultivoEspecie}
                    </div>
                  </td>
                  <td className="texto-derecha">
                    {formatearNumero(cos.toneladasTotales)} t
                  </td>
                  <td className="texto-derecha">
                    {formatearNumero(cos.rindeTonPorHa)} t/ha
                  </td>
                  <td className="texto-derecha">
                    $ {formatearNumero(cos.precioVentaPorTon)}
                  </td>
                  <td className="texto-derecha">
                    <strong>$ {formatearNumero(cos.ingresoBrutoTotal)}</strong>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!reporte.cosechaRegistrada && (
        <div
          style={{
            border: '1px solid var(--color-borde)',
            borderRadius: '8px',
            padding: '1rem',
            backgroundColor: 'var(--color-fondo)',
          }}
        >
          <h3 style={{ margin: '0 0 0.35rem', fontSize: '0.95rem' }}>
            Simulador de punto de equilibrio y ROI proyectado
          </h3>
          <p
            className="texto-suave texto-chico"
            style={{ margin: '0 0 0.75rem' }}
          >
            Proyección informativa (no modifica datos) tomando los costos
            acumulados actuales de{' '}
            <strong>
              $ {formatearNumero(reporte.costosTotalesAcumulados)}
            </strong>{' '}
            sobre <strong>{formatearNumero(reporte.superficieHa)} ha</strong>.
          </p>

          <div className="fila-campos" style={{ marginBottom: '0.75rem' }}>
            <div className="campo">
              <label htmlFor="sim-rinde-ha">Rinde estimado (t / ha)</label>
              <input
                id="sim-rinde-ha"
                type="number"
                step="0.01"
                min="0"
                value={rindeSimuladoHa}
                onChange={(e) => setRindeSimuladoHa(e.target.value)}
              />
            </div>
            <div className="campo">
              <label htmlFor="sim-precio-ton">Precio de venta ($ / t)</label>
              <input
                id="sim-precio-ton"
                type="number"
                step="0.01"
                min="0.01"
                value={precioSimuladoTon}
                onChange={(e) => setPrecioSimuladoTon(e.target.value)}
              />
            </div>
          </div>

          {simulacion && (
            <div className="resumen-ajuste" style={{ marginBottom: 0 }}>
              <div>
                <strong>Producción proyectada:</strong>{' '}
                {formatearNumero(simulacion.toneladasProyectadas)} t
              </div>
              <div>
                <strong>Ingreso bruto proyectado:</strong> ${' '}
                {formatearNumero(simulacion.ingresoBrutoProyectado)}
              </div>
              <div>
                <strong>Margen neto proyectado:</strong>{' '}
                <strong
                  className={
                    simulacion.margenNetoProyectado >= 0
                      ? 'texto-exito'
                      : 'texto-peligro'
                  }
                >
                  $ {formatearNumero(simulacion.margenNetoProyectado)}
                </strong>
              </div>
              <div>
                <strong>ROI proyectado:</strong>{' '}
                {simulacion.roiProyectado !== null ? (
                  <strong
                    className={
                      simulacion.roiProyectado >= 0
                        ? 'texto-exito'
                        : 'texto-peligro'
                    }
                  >
                    {formatearNumero(simulacion.roiProyectado)} %
                  </strong>
                ) : (
                  <span className="texto-suave">Sin costos registrados</span>
                )}
              </div>
              {costosAcumulados > 0 && (
                <div>
                  <strong>Rinde de equilibrio (break-even):</strong>{' '}
                  {formatearNumero(simulacion.rindeEquilibrioHa)} t/ha (
                  {formatearNumero(simulacion.toneladasEquilibrio)} t totales)
                </div>
              )}
            </div>
          )}
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

export default DetalleFinancieroLoteModal
