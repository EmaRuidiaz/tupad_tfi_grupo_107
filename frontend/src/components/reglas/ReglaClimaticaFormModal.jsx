import { useState } from 'react'
import Modal from '../Modal.jsx'
import {
  actualizarReglaClimatica,
  crearReglaClimatica,
} from '../../services/reglasClimaticasService.js'
import {
  TIPOS_LABOR_REGLA,
  etiquetaTipoLabor,
} from '../../utils/formatters.js'

function estadoInicial(regla, tiposDisponibles) {
  if (!regla) {
    const primerTipo = tiposDisponibles?.[0]?.valor || 'PULVERIZACION'
    return {
      tipoLabor: primerTipo,
      vientoMinKmh: '0',
      vientoMaxKmh: '20',
      tempMinC: '5',
      tempMaxC: '35',
      probLluviaMaxPct: '20',
      humedadMinPct: '30',
      mensajeAlerta: '',
    }
  }
  return {
    tipoLabor: regla.tipoLabor,
    vientoMinKmh: String(regla.vientoMinKmh ?? 0),
    vientoMaxKmh: String(regla.vientoMaxKmh ?? 20),
    tempMinC: String(regla.tempMinC ?? 5),
    tempMaxC: String(regla.tempMaxC ?? 35),
    probLluviaMaxPct: String(regla.probLluviaMaxPct ?? 20),
    humedadMinPct: String(regla.humedadMinPct ?? 30),
    mensajeAlerta: regla.mensajeAlerta ?? '',
  }
}

function ReglaClimaticaFormModal({
  regla,
  tiposDisponibles,
  onCerrar,
  onGuardado,
}) {
  const opcionesTipo = regla ? TIPOS_LABOR_REGLA : tiposDisponibles
  const [form, setForm] = useState(() =>
    estadoInicial(regla, tiposDisponibles),
  )
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')

    const vientoMin = Number(form.vientoMinKmh)
    const vientoMax = Number(form.vientoMaxKmh)
    const tempMin = Number(form.tempMinC)
    const tempMax = Number(form.tempMaxC)
    const probLluvia = Number(form.probLluviaMaxPct)
    const humedadMin = Number(form.humedadMinPct)

    if (vientoMin > vientoMax) {
      setError(
        'La velocidad mínima de viento no puede superar a la velocidad máxima.',
      )
      return
    }

    if (tempMin >= tempMax) {
      setError(
        'La temperatura mínima debe ser menor a la temperatura máxima.',
      )
      return
    }

    if (probLluvia < 0 || probLluvia > 100 || humedadMin < 0 || humedadMin > 100) {
      setError(
        'Los porcentajes de probabilidad de lluvia y humedad mínima deben estar entre 0 y 100.',
      )
      return
    }

    setGuardando(true)

    const payload = {
      tipoLabor: form.tipoLabor,
      vientoMinKmh: vientoMin,
      vientoMaxKmh: vientoMax,
      tempMinC: tempMin,
      tempMaxC: tempMax,
      probLluviaMaxPct: probLluvia,
      humedadMinPct: humedadMin,
      mensajeAlerta: form.mensajeAlerta.trim(),
    }

    try {
      if (regla) {
        await actualizarReglaClimatica(regla.id, payload)
        onGuardado(
          `Regla climática para "${etiquetaTipoLabor(payload.tipoLabor)}" actualizada correctamente.`,
        )
      } else {
        await crearReglaClimatica(payload)
        onGuardado(
          `Regla climática para "${etiquetaTipoLabor(payload.tipoLabor)}" registrada correctamente.`,
        )
      }
    } catch (err) {
      setError(err.message || 'No se pudo guardar la regla climática.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho
      titulo={
        regla
          ? `Editar regla climática: ${etiquetaTipoLabor(regla.tipoLabor)}`
          : 'Registrar nueva regla climática'
      }
      onCerrar={onCerrar}
    >
      {error && <div className="alerta alerta--error">{error}</div>}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="campo">
          <label htmlFor="regla-tipo-labor">Tipo de labor</label>
          <select
            id="regla-tipo-labor"
            value={form.tipoLabor}
            disabled={Boolean(regla)}
            onChange={(e) => setForm({ ...form, tipoLabor: e.target.value })}
          >
            {opcionesTipo.map((t) => (
              <option key={t.valor} value={t.valor}>
                {t.etiqueta}
              </option>
            ))}
          </select>
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="regla-viento-min">Viento mínimo (km/h)</label>
            <input
              id="regla-viento-min"
              type="number"
              step="0.01"
              min="0"
              max="999.99"
              required
              value={form.vientoMinKmh}
              onChange={(e) =>
                setForm({ ...form, vientoMinKmh: e.target.value })
              }
            />
          </div>

          <div className="campo">
            <label htmlFor="regla-viento-max">Viento máximo (km/h)</label>
            <input
              id="regla-viento-max"
              type="number"
              step="0.01"
              min="0.01"
              max="999.99"
              required
              value={form.vientoMaxKmh}
              onChange={(e) =>
                setForm({ ...form, vientoMaxKmh: e.target.value })
              }
            />
          </div>
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="regla-temp-min">Temperatura mínima (°C)</label>
            <input
              id="regla-temp-min"
              type="number"
              step="0.01"
              min="-50"
              max="60"
              required
              value={form.tempMinC}
              onChange={(e) => setForm({ ...form, tempMinC: e.target.value })}
            />
          </div>

          <div className="campo">
            <label htmlFor="regla-temp-max">Temperatura máxima (°C)</label>
            <input
              id="regla-temp-max"
              type="number"
              step="0.01"
              min="-50"
              max="60"
              required
              value={form.tempMaxC}
              onChange={(e) => setForm({ ...form, tempMaxC: e.target.value })}
            />
          </div>
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="regla-lluvia-max">
              Probabilidad máx. de lluvia (%)
            </label>
            <input
              id="regla-lluvia-max"
              type="number"
              step="0.01"
              min="0"
              max="100"
              required
              value={form.probLluviaMaxPct}
              onChange={(e) =>
                setForm({ ...form, probLluviaMaxPct: e.target.value })
              }
            />
          </div>

          <div className="campo">
            <label htmlFor="regla-humedad-min">
              Humedad relativa mínima (%)
            </label>
            <input
              id="regla-humedad-min"
              type="number"
              step="0.01"
              min="0"
              max="100"
              required
              value={form.humedadMinPct}
              onChange={(e) =>
                setForm({ ...form, humedadMinPct: e.target.value })
              }
            />
          </div>
        </div>

        <div className="campo">
          <label htmlFor="regla-mensaje">
            Mensaje de alerta al bloquear una labor
          </label>
          <textarea
            id="regla-mensaje"
            rows={2}
            maxLength={255}
            required
            placeholder="Ej: Viento superior a 15 km/h genera deriva peligrosa de fitosanitarios o lluvia inminente."
            value={form.mensajeAlerta}
            onChange={(e) =>
              setForm({ ...form, mensajeAlerta: e.target.value })
            }
          />
        </div>

        <p className="texto-suave texto-chico">
          Nota: El viento mínimo evita pulverizar con inversión térmica (calma
          absoluta). Las labores de Labranza no llevan regla climática y se
          inician sin validación meteorológica.
        </p>

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

export default ReglaClimaticaFormModal
