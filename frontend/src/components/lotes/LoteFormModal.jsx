import { useState } from 'react'
import Modal from '../Modal.jsx'
import { actualizarLote, crearLote } from '../../services/lotesService.js'
import { etiquetaEstadoLote } from '../../utils/formatters.js'

function estadoInicial(lote) {
  if (!lote) {
    return {
      nombre: '',
      superficieHa: '',
      latitud: '',
      longitud: '',
      tipoSuelo: '',
      poligonoWkt: '',
      observaciones: '',
    }
  }
  return {
    nombre: lote.nombre,
    superficieHa: String(lote.superficieHa ?? ''),
    latitud: String(lote.latitud ?? ''),
    longitud: String(lote.longitud ?? ''),
    tipoSuelo: lote.tipoSuelo ?? '',
    poligonoWkt: lote.poligonoWkt ?? '',
    observaciones: lote.observaciones ?? '',
  }
}

function LoteFormModal({ lote, onCerrar, onGuardado }) {
  const [form, setForm] = useState(() => estadoInicial(lote))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setGuardando(true)

    const payload = {
      nombre: form.nombre.trim(),
      superficieHa: Number(form.superficieHa),
      latitud: Number(form.latitud),
      longitud: Number(form.longitud),
      tipoSuelo: form.tipoSuelo.trim() || null,
      poligonoWkt: form.poligonoWkt.trim() || null,
      observaciones: form.observaciones.trim() || null,
    }

    try {
      if (lote) {
        await actualizarLote(lote.id, payload)
        onGuardado(`Lote "${payload.nombre}" actualizado correctamente.`)
      } else {
        await crearLote(payload)
        onGuardado(`Lote "${payload.nombre}" registrado correctamente.`)
      }
    } catch (err) {
      setError(err.message || 'No se pudo guardar el lote.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho
      titulo={lote ? 'Editar lote' : 'Registrar nuevo lote'}
      onCerrar={onCerrar}
    >
      {error && <div className="alerta alerta--error">{error}</div>}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="lote-nombre">Nombre</label>
            <input
              id="lote-nombre"
              type="text"
              maxLength={100}
              required
              value={form.nombre}
              onChange={(e) => setForm({ ...form, nombre: e.target.value })}
              placeholder="Ej: Lote Norte - La Escondida"
            />
          </div>

          <div className="campo">
            <label htmlFor="lote-superficie">Superficie (ha)</label>
            <input
              id="lote-superficie"
              type="number"
              step="0.01"
              min="0.01"
              required
              value={form.superficieHa}
              onChange={(e) =>
                setForm({ ...form, superficieHa: e.target.value })
              }
            />
          </div>
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="lote-latitud">Latitud del centro</label>
            <input
              id="lote-latitud"
              type="number"
              step="0.000001"
              min="-90"
              max="90"
              required
              value={form.latitud}
              onChange={(e) => setForm({ ...form, latitud: e.target.value })}
              placeholder="Ej: -33.892"
            />
          </div>

          <div className="campo">
            <label htmlFor="lote-longitud">Longitud del centro</label>
            <input
              id="lote-longitud"
              type="number"
              step="0.000001"
              min="-180"
              max="180"
              required
              value={form.longitud}
              onChange={(e) => setForm({ ...form, longitud: e.target.value })}
              placeholder="Ej: -60.573"
            />
          </div>

          <div className="campo">
            <label htmlFor="lote-suelo">Tipo de suelo</label>
            <input
              id="lote-suelo"
              type="text"
              maxLength={50}
              value={form.tipoSuelo}
              onChange={(e) => setForm({ ...form, tipoSuelo: e.target.value })}
              placeholder="Ej: Franco Limoso"
            />
          </div>
        </div>

        <div className="campo">
          <label htmlFor="lote-poligono">Polígono WKT (opcional)</label>
          <textarea
            id="lote-poligono"
            rows={2}
            value={form.poligonoWkt}
            onChange={(e) => setForm({ ...form, poligonoWkt: e.target.value })}
            placeholder="POLYGON((-60.573 -33.892, -60.572 -33.892, -60.572 -33.891, -60.573 -33.892))"
          />
          <span className="texto-suave texto-chico">
            Puntos en formato &laquo;longitud latitud&raquo;. El primer y el
            último punto deben coincidir.
          </span>
        </div>

        <div className="campo">
          <label htmlFor="lote-observaciones">Observaciones</label>
          <textarea
            id="lote-observaciones"
            rows={3}
            value={form.observaciones}
            onChange={(e) =>
              setForm({ ...form, observaciones: e.target.value })
            }
          />
        </div>

        {lote ? (
          <p className="texto-suave texto-chico">
            Nota: el estado agronómico ({etiquetaEstadoLote(lote.estado)}) se
            modifica con el botón &laquo;Estado&raquo;.
          </p>
        ) : (
          <p className="texto-suave texto-chico">
            El lote se registra en estado &laquo;En preparación&raquo;.
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

export default LoteFormModal
