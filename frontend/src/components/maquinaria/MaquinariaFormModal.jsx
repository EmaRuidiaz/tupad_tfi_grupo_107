import { useState } from 'react'
import Modal from '../Modal.jsx'
import {
  actualizarMaquinaria,
  crearMaquinaria,
} from '../../services/maquinariaService.js'
import { TIPOS_MAQUINARIA } from '../../utils/formatters.js'

function estadoInicial(maquinaria) {
  if (!maquinaria) {
    return {
      nombre: '',
      tipo: 'TRACTOR',
      matriculaOSerie: '',
      horasUsoActuales: '0',
      horasParaProximoService: '250',
      consumoLitrosHora: '0',
      costoOperativoHora: '0',
    }
  }
  return {
    nombre: maquinaria.nombre,
    tipo: maquinaria.tipo,
    matriculaOSerie: maquinaria.matriculaOSerie,
    horasUsoActuales: String(maquinaria.horasUsoActuales ?? 0),
    horasParaProximoService: String(maquinaria.horasParaProximoService ?? 0),
    consumoLitrosHora: String(maquinaria.consumoLitrosHora ?? 0),
    costoOperativoHora: String(maquinaria.costoOperativoHora ?? 0),
  }
}

function MaquinariaFormModal({ maquinaria, onCerrar, onGuardado }) {
  const [form, setForm] = useState(() => estadoInicial(maquinaria))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')

    const horasActualesNum = Number(form.horasUsoActuales || 0)
    const horasProximoNum = Number(form.horasParaProximoService)

    if (!maquinaria && horasProximoNum <= horasActualesNum) {
      setError(
        'Las horas para el próximo service deben ser mayores a las horas de uso iniciales.',
      )
      return
    }

    if (maquinaria && horasActualesNum < Number(maquinaria.horasUsoActuales ?? 0)) {
      setError(
        `El horómetro actual (${horasActualesNum} hs) no puede reducirse por debajo del valor registrado (${maquinaria.horasUsoActuales} hs).`,
      )
      return
    }

    setGuardando(true)

    const payload = {
      nombre: form.nombre.trim(),
      tipo: form.tipo,
      matriculaOSerie: form.matriculaOSerie.trim(),
      horasUsoActuales: horasActualesNum,
      horasParaProximoService: horasProximoNum,
      consumoLitrosHora: Number(form.consumoLitrosHora),
      costoOperativoHora: Number(form.costoOperativoHora),
    }

    try {
      if (maquinaria) {
        await actualizarMaquinaria(maquinaria.id, payload)
        onGuardado(`Maquinaria "${payload.nombre}" actualizada correctamente.`)
      } else {
        await crearMaquinaria(payload)
        onGuardado(`Maquinaria "${payload.nombre}" registrada correctamente.`)
      }
    } catch (err) {
      setError(err.message || 'No se pudo guardar la maquinaria.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      titulo={maquinaria ? 'Editar maquinaria' : 'Registrar nueva maquinaria'}
      onCerrar={onCerrar}
    >
      {error && <div className="alerta alerta--error">{error}</div>}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="campo">
          <label htmlFor="maq-nombre">Marca y modelo / Nombre</label>
          <input
            id="maq-nombre"
            type="text"
            maxLength={100}
            required
            value={form.nombre}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })}
            placeholder="Ej: Tractor John Deere 6110M"
          />
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="maq-tipo">Tipo</label>
            <select
              id="maq-tipo"
              value={form.tipo}
              onChange={(e) => setForm({ ...form, tipo: e.target.value })}
            >
              {TIPOS_MAQUINARIA.map((t) => (
                <option key={t.valor} value={t.valor}>
                  {t.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="maq-matricula">Matrícula o N.° de serie</label>
            <input
              id="maq-matricula"
              type="text"
              maxLength={50}
              required
              value={form.matriculaOSerie}
              onChange={(e) =>
                setForm({ ...form, matriculaOSerie: e.target.value })
              }
              placeholder="Ej: JD-6110M-2022-01"
            />
          </div>
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="maq-horas-actuales">Horómetro actual (hs)</label>
            <input
              id="maq-horas-actuales"
              type="number"
              step="0.01"
              min="0"
              max="99999999.99"
              required
              value={form.horasUsoActuales}
              onChange={(e) =>
                setForm({ ...form, horasUsoActuales: e.target.value })
              }
            />
          </div>

          <div className="campo">
            <label htmlFor="maq-horas-service">Próximo service (hs)</label>
            <input
              id="maq-horas-service"
              type="number"
              step="0.01"
              min="0.01"
              max="99999999.99"
              required
              value={form.horasParaProximoService}
              onChange={(e) =>
                setForm({ ...form, horasParaProximoService: e.target.value })
              }
            />
          </div>
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="maq-consumo">Consumo combustible (L/h)</label>
            <input
              id="maq-consumo"
              type="number"
              step="0.01"
              min="0"
              max="999999.99"
              required
              value={form.consumoLitrosHora}
              onChange={(e) =>
                setForm({ ...form, consumoLitrosHora: e.target.value })
              }
            />
          </div>

          <div className="campo">
            <label htmlFor="maq-costo">Costo operativo ($/h)</label>
            <input
              id="maq-costo"
              type="number"
              step="0.01"
              min="0"
              max="99999999.99"
              required
              value={form.costoOperativoHora}
              onChange={(e) =>
                setForm({ ...form, costoOperativoHora: e.target.value })
              }
            />
          </div>
        </div>

        {maquinaria ? (
          <p className="texto-suave texto-chico">
            Nota: En la operatoria habitual, el horómetro se incrementa al
            completar labores agrícolas y el próximo service se actualiza al
            registrar un mantenimiento.
          </p>
        ) : (
          <p className="texto-suave texto-chico">
            La maquinaria se registra en estado &laquo;Operativo&raquo;.
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

export default MaquinariaFormModal
