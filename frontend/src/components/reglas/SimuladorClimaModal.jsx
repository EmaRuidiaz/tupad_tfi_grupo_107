import { useState } from 'react'
import Modal from '../Modal.jsx'
import { evaluarReglaClimatica } from '../../services/reglasClimaticasService.js'
import {
  etiquetaTipoLabor,
  formatearNumero,
} from '../../utils/formatters.js'

function valoresIniciales(regla) {
  const vientoMedio = (
    (Number(regla.vientoMinKmh) + Number(regla.vientoMaxKmh)) /
    2
  ).toFixed(2)
  const tempMedia = (
    (Number(regla.tempMinC) + Number(regla.tempMaxC)) /
    2
  ).toFixed(2)
  return {
    vientoVelocidadKmh: vientoMedio,
    temperaturaC: tempMedia,
    probabilidadLluviaPct: String(
      Math.max(0, Number(regla.probLluviaMaxPct) - 5),
    ),
    humedadRelativaPct: String(
      Math.min(100, Number(regla.humedadMinPct) + 10),
    ),
  }
}

function SimuladorClimaModal({ regla, onCerrar }) {
  const [form, setForm] = useState(() => valoresIniciales(regla))
  const [evaluando, setEvaluando] = useState(false)
  const [error, setError] = useState('')
  const [resultado, setResultado] = useState(null)

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setEvaluando(true)
    try {
      const respuesta = await evaluarReglaClimatica(regla.id, {
        vientoVelocidadKmh: Number(form.vientoVelocidadKmh),
        temperaturaC: Number(form.temperaturaC),
        probabilidadLluviaPct: Number(form.probabilidadLluviaPct),
        humedadRelativaPct: Number(form.humedadRelativaPct),
      })
      setResultado(respuesta)
    } catch (err) {
      setError(err.message || 'No se pudo evaluar la condición climática.')
    } finally {
      setEvaluando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho
      titulo={`Simular validación climática: ${etiquetaTipoLabor(regla.tipoLabor)}`}
      onCerrar={onCerrar}
    >
      <div className="resumen-ajuste">
        <div>
          <span>Viento permitido:</span>{' '}
          <strong>
            {formatearNumero(regla.vientoMinKmh)} a{' '}
            {formatearNumero(regla.vientoMaxKmh)} km/h
          </strong>
        </div>
        <div>
          <span>Temperatura:</span>{' '}
          <strong>
            {formatearNumero(regla.tempMinC)} a{' '}
            {formatearNumero(regla.tempMaxC)} °C
          </strong>
        </div>
        <div>
          <span>Lluvia máx.:</span>{' '}
          <strong>&le; {formatearNumero(regla.probLluviaMaxPct)} %</strong>
        </div>
        <div>
          <span>Humedad mín.:</span>{' '}
          <strong>&ge; {formatearNumero(regla.humedadMinPct)} %</strong>
        </div>
      </div>

      {error && <div className="alerta alerta--error">{error}</div>}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="sim-viento">Viento observado (km/h)</label>
            <input
              id="sim-viento"
              type="number"
              step="0.01"
              min="0"
              max="999.99"
              required
              value={form.vientoVelocidadKmh}
              onChange={(e) =>
                setForm({ ...form, vientoVelocidadKmh: e.target.value })
              }
            />
          </div>

          <div className="campo">
            <label htmlFor="sim-temp">Temperatura observada (°C)</label>
            <input
              id="sim-temp"
              type="number"
              step="0.01"
              min="-50"
              max="60"
              required
              value={form.temperaturaC}
              onChange={(e) =>
                setForm({ ...form, temperaturaC: e.target.value })
              }
            />
          </div>
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="sim-lluvia">Probabilidad de lluvia (%)</label>
            <input
              id="sim-lluvia"
              type="number"
              step="0.01"
              min="0"
              max="100"
              required
              value={form.probabilidadLluviaPct}
              onChange={(e) =>
                setForm({ ...form, probabilidadLluviaPct: e.target.value })
              }
            />
          </div>

          <div className="campo">
            <label htmlFor="sim-humedad">Humedad relativa (%)</label>
            <input
              id="sim-humedad"
              type="number"
              step="0.01"
              min="0"
              max="100"
              required
              value={form.humedadRelativaPct}
              onChange={(e) =>
                setForm({ ...form, humedadRelativaPct: e.target.value })
              }
            />
          </div>
        </div>

        {resultado && (
          <div
            className={`alerta ${
              resultado.aprobada ? 'alerta--exito' : 'alerta--error'
            }`}
            role="status"
          >
            <strong>
              Dictamen del motor: {resultado.estadoLaborSugerido}
            </strong>
            {resultado.aprobada ? (
              <div>
                Todas las variables se encuentran dentro de los umbrales
                agronómicos permitidos.
              </div>
            ) : (
              <div>
                <div>{resultado.mensajeAlerta}</div>
                <ul>
                  {resultado.incumplimientos.map((motivo) => (
                    <li key={motivo}>{motivo}</li>
                  ))}
                </ul>
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
          <button
            type="submit"
            className="btn btn--primario"
            disabled={evaluando}
          >
            {evaluando ? 'Evaluando...' : 'Evaluar condiciones'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default SimuladorClimaModal
