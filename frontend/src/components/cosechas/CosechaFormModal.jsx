import { useEffect, useMemo, useState } from 'react'
import Modal from '../Modal.jsx'
import { registrarCosecha } from '../../services/cosechasService.js'
import { listarLabores } from '../../services/laboresService.js'
import {
  fechaHoyIso,
  formatearFecha,
  formatearNumero,
} from '../../utils/formatters.js'

const FORM_VACIO = {
  loteId: '',
  cultivoId: '',
  laborId: '',
  fechaCosecha: fechaHoyIso(),
  toneladasTotales: '',
  precioVentaPorTon: '',
}

/**
 * Alta de la cosecha consolidada del ciclo (HU-12, CU-05).
 * Sólo se ofrecen los lotes en estado LISTO_COSECHA (RN-02); el backend vuelve a validarlo.
 */
function CosechaFormModal({ lotesListos, cultivos, loteInicialId, onCerrar, onGuardado }) {
  const [form, setForm] = useState(() => ({
    ...FORM_VACIO,
    loteId: loteInicialId ? String(loteInicialId) : '',
  }))
  // Labores de trilla del último lote consultado; "cargando" se deriva de si corresponden al lote elegido.
  const [laboresPorLote, setLaboresPorLote] = useState({ loteId: null, labores: [] })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  const loteSeleccionado = lotesListos.find((l) => String(l.id) === form.loteId)
  const cargandoLabores = Boolean(form.loteId) && laboresPorLote.loteId !== form.loteId
  const laboresCosecha = laboresPorLote.loteId === form.loteId ? laboresPorLote.labores : []

  // Al elegir el lote se buscan sus órdenes de trilla completadas para vincularlas (opcional).
  useEffect(() => {
    if (!form.loteId) return undefined
    let cancelado = false
    const loteId = form.loteId
    listarLabores({ loteId, estado: 'COMPLETADA', tipoLabor: 'COSECHA' })
      .then((datos) => {
        if (!cancelado) setLaboresPorLote({ loteId, labores: datos })
      })
      .catch(() => {
        if (!cancelado) setLaboresPorLote({ loteId, labores: [] })
      })
    return () => {
      cancelado = true
    }
  }, [form.loteId])

  // Vista previa de los valores que calcula el backend (el margen neto depende de los costos del ciclo).
  const vistaPrevia = useMemo(() => {
    const toneladas = Number(form.toneladasTotales)
    const precio = Number(form.precioVentaPorTon)
    const superficie = Number(loteSeleccionado?.superficieHa || 0)
    if (!form.toneladasTotales || Number.isNaN(toneladas)) return null
    return {
      rinde: superficie > 0 ? toneladas / superficie : null,
      ingreso: form.precioVentaPorTon && !Number.isNaN(precio) ? toneladas * precio : null,
    }
  }, [form.toneladasTotales, form.precioVentaPorTon, loteSeleccionado])

  function cambiarLote(loteId) {
    setForm({ ...form, loteId, laborId: '' })
  }

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setGuardando(true)

    const payload = {
      loteId: Number(form.loteId),
      cultivoId: Number(form.cultivoId),
      laborId: form.laborId ? Number(form.laborId) : null,
      fechaCosecha: form.fechaCosecha,
      toneladasTotales: Number(form.toneladasTotales),
      precioVentaPorTon: Number(form.precioVentaPorTon),
    }

    try {
      const cosecha = await registrarCosecha(payload)
      onGuardado(
        `Cosecha registrada en "${cosecha.loteNombre}": ${formatearNumero(cosecha.toneladasTotales)} t ` +
          `(${formatearNumero(cosecha.rindeTonPorHa)} t/ha), margen neto $ ${formatearNumero(cosecha.margenNetoCalculado)}. ` +
          'El lote pasó a «Cosechado».',
      )
    } catch (err) {
      setError(err.message || 'No se pudo registrar la cosecha.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal abierto ancho titulo="Registrar cosecha" onCerrar={onCerrar}>
      {error && <div className="alerta alerta--error">{error}</div>}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="cosecha-lote">Lote (listo para cosecha)</label>
            <select
              id="cosecha-lote"
              required
              value={form.loteId}
              onChange={(e) => cambiarLote(e.target.value)}
            >
              <option value="">Seleccioná un lote...</option>
              {lotesListos.map((lote) => (
                <option key={lote.id} value={lote.id}>
                  {lote.nombre} ({formatearNumero(lote.superficieHa)} ha)
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="cosecha-cultivo">Cultivo recolectado</label>
            <select
              id="cosecha-cultivo"
              required
              value={form.cultivoId}
              onChange={(e) => setForm({ ...form, cultivoId: e.target.value })}
            >
              <option value="">Seleccioná un cultivo...</option>
              {cultivos.map((cultivo) => (
                <option key={cultivo.id} value={cultivo.id}>
                  {cultivo.nombre} ({cultivo.especie})
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="cosecha-fecha">Fecha de cosecha</label>
            <input
              id="cosecha-fecha"
              type="date"
              required
              max={fechaHoyIso()}
              value={form.fechaCosecha}
              onChange={(e) => setForm({ ...form, fechaCosecha: e.target.value })}
            />
          </div>

          <div className="campo">
            <label htmlFor="cosecha-toneladas">Toneladas totales</label>
            <input
              id="cosecha-toneladas"
              type="number"
              step="0.01"
              min="0"
              required
              value={form.toneladasTotales}
              onChange={(e) => setForm({ ...form, toneladasTotales: e.target.value })}
              placeholder="Ej: 1350"
            />
          </div>

          <div className="campo">
            <label htmlFor="cosecha-precio">Precio de venta ($ / t)</label>
            <input
              id="cosecha-precio"
              type="number"
              step="0.01"
              min="0"
              required
              value={form.precioVentaPorTon}
              onChange={(e) => setForm({ ...form, precioVentaPorTon: e.target.value })}
              placeholder="Ej: 210000"
            />
          </div>
        </div>

        <div className="campo">
          <label htmlFor="cosecha-labor">Orden de trilla vinculada (opcional)</label>
          <select
            id="cosecha-labor"
            value={form.laborId}
            disabled={!form.loteId || cargandoLabores}
            onChange={(e) => setForm({ ...form, laborId: e.target.value })}
          >
            <option value="">
              {!form.loteId
                ? 'Primero elegí el lote'
                : cargandoLabores
                  ? 'Buscando labores de cosecha...'
                  : laboresCosecha.length === 0
                    ? 'Sin labores de cosecha completadas en este lote'
                    : 'Sin vincular'}
            </option>
            {laboresCosecha.map((labor) => (
              <option key={labor.id} value={labor.id}>
                #{labor.id} · {formatearFecha(labor.fechaEjecucion || labor.fechaPlanificada)} · $
                {' '}
                {formatearNumero(labor.costoTotalCalculado)}
              </option>
            ))}
          </select>
        </div>

        {vistaPrevia && (
          <div className="alerta alerta--advertencia">
            <strong>Vista previa:</strong>{' '}
            {vistaPrevia.rinde !== null
              ? `rinde ${formatearNumero(vistaPrevia.rinde)} t/ha`
              : 'elegí el lote para calcular el rinde'}
            {vistaPrevia.ingreso !== null &&
              ` · ingreso bruto $ ${formatearNumero(vistaPrevia.ingreso)}`}
            <div className="texto-chico">
              El margen neto se calcula al guardar, restando los costos de las labores completadas del
              ciclo que se cierra.
            </div>
          </div>
        )}

        <p className="texto-suave texto-chico">
          La cosecha cierra el ciclo productivo: el lote pasa a «Cosechado» y el registro no se puede
          modificar.
        </p>

        <div className="modal__pie">
          <button type="button" className="btn btn--secundario" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--primario" disabled={guardando}>
            {guardando ? 'Registrando...' : 'Registrar cosecha'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default CosechaFormModal
