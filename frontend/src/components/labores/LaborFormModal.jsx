import { useMemo, useState } from 'react'
import Modal from '../Modal.jsx'
import { programarLabor } from '../../services/laboresService.js'
import {
  TIPOS_LABOR,
  etiquetaEstadoLote,
  etiquetaEstadoMaquinaria,
  etiquetaTipoLabor,
  etiquetaTipoMaquinaria,
  fechaHoraManianaIso,
  formatearNumero,
} from '../../utils/formatters.js'

function LaborFormModal({
  lotes,
  maquinarias,
  insumos,
  onCerrar,
  onGuardado,
}) {
  const loteInicial =
    lotes.find(
      (l) => l.estado !== 'COSECHADO' && l.estado !== 'DESCANSO',
    )?.id ??
    lotes[0]?.id ??
    ''

  const [form, setForm] = useState(() => ({
    loteId: loteInicial ? String(loteInicial) : '',
    tipoLabor: 'PULVERIZACION',
    fechaPlanificada: fechaHoraManianaIso(),
    maquinariaId: '',
    observaciones: '',
  }))

  const [lineasInsumo, setLineasInsumo] = useState([])
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  const loteSeleccionado = useMemo(
    () => lotes.find((l) => String(l.id) === String(form.loteId)) || null,
    [lotes, form.loteId],
  )

  const maquinariaSeleccionada = useMemo(
    () =>
      maquinarias.find((m) => String(m.id) === String(form.maquinariaId)) ||
      null,
    [maquinarias, form.maquinariaId],
  )

  const loteBloqueaTipoLabor = Boolean(
    loteSeleccionado &&
      (loteSeleccionado.estado === 'COSECHADO' ||
        loteSeleccionado.estado === 'DESCANSO') &&
      form.tipoLabor !== 'LABRANZA',
  )

  const idsInsumosElegidos = useMemo(
    () =>
      new Set(
        lineasInsumo
          .map((l) => Number(l.insumoId))
          .filter((id) => Number.isFinite(id) && id > 0),
      ),
    [lineasInsumo],
  )

  function agregarLineaInsumo() {
    const disponibleParaElegir = insumos.find(
      (ins) => !idsInsumosElegidos.has(ins.id),
    )
    if (!disponibleParaElegir) return
    setLineasInsumo((prev) => [
      ...prev,
      {
        uid: `${Date.now()}-${Math.random()}`,
        insumoId: String(disponibleParaElegir.id),
        cantidadUtilizada: '',
      },
    ])
  }

  function actualizarLinea(uid, cambios) {
    setLineasInsumo((prev) =>
      prev.map((linea) => (linea.uid === uid ? { ...linea, ...cambios } : linea)),
    )
  }

  function quitarLinea(uid) {
    setLineasInsumo((prev) => prev.filter((linea) => linea.uid !== uid))
  }

  const resumenInsumos = useMemo(() => {
    const detalles = lineasInsumo.map((linea) => {
      const insumo = insumos.find(
        (i) => String(i.id) === String(linea.insumoId),
      )
      const cantidad = Number(linea.cantidadUtilizada)
      const disponible = Number(insumo?.stockDisponible ?? 0)
      const precio = Number(insumo?.precioUnitario ?? 0)
      const subtotal =
        Number.isFinite(cantidad) && cantidad > 0 ? cantidad * precio : 0
      const faltante =
        Number.isFinite(cantidad) && cantidad > disponible
          ? cantidad - disponible
          : 0

      return {
        ...linea,
        insumo,
        cantidad,
        disponible,
        subtotal,
        faltante,
      }
    })

    const costoEstimado = detalles.reduce((acc, d) => acc + d.subtotal, 0)
    const hayExcesoStock = detalles.some((d) => d.faltante > 0)

    return { detalles, costoEstimado, hayExcesoStock }
  }, [lineasInsumo, insumos])

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')

    if (!form.loteId) {
      setError('Seleccioná un lote para programar la orden de trabajo.')
      return
    }

    if (loteBloqueaTipoLabor) {
      setError(
        `El lote "${loteSeleccionado.nombre}" está en estado ${etiquetaEstadoLote(loteSeleccionado.estado)}: sólo admite labores de Labranza.`,
      )
      return
    }

    for (const det of resumenInsumos.detalles) {
      if (!det.insumo) {
        setError('Seleccioná un insumo válido en cada fila de la grilla.')
        return
      }
      if (!Number.isFinite(det.cantidad) || det.cantidad <= 0) {
        setError(
          `Ingresá una cantidad mayor a 0 para "${det.insumo.nombre}".`,
        )
        return
      }
    }

    const fechaNormalizada =
      form.fechaPlanificada.length === 16
        ? `${form.fechaPlanificada}:00`
        : form.fechaPlanificada

    const payload = {
      loteId: Number(form.loteId),
      maquinariaId: form.maquinariaId ? Number(form.maquinariaId) : null,
      tipoLabor: form.tipoLabor,
      fechaPlanificada: fechaNormalizada,
      observaciones: form.observaciones.trim() || null,
      insumos: resumenInsumos.detalles.map((det) => ({
        insumoId: Number(det.insumoId),
        cantidadUtilizada: det.cantidad,
      })),
    }

    setGuardando(true)
    try {
      const creada = await programarLabor(payload)
      onGuardado(
        `Orden de trabajo #${creada.id} (${etiquetaTipoLabor(creada.tipoLabor)} en "${creada.loteNombre}") programada en estado Planificada.`,
      )
    } catch (err) {
      setError(err.message || 'No se pudo programar la orden de trabajo.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho
      titulo="Programar orden de trabajo (CU-03)"
      onCerrar={onCerrar}
    >
      {error && (
        <div className="alerta alerta--error" role="alert">
          {error}
        </div>
      )}

      <form onSubmit={manejarSubmit} className="formulario">
        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="labor-lote">Lote agrícola</label>
            <select
              id="labor-lote"
              required
              value={form.loteId}
              onChange={(e) => setForm({ ...form, loteId: e.target.value })}
            >
              <option value="">Seleccionar lote...</option>
              {lotes.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.nombre} ({formatearNumero(l.superficieHa)} ha ·{' '}
                  {etiquetaEstadoLote(l.estado)})
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="labor-tipo">Tipo de labor</label>
            <select
              id="labor-tipo"
              required
              value={form.tipoLabor}
              onChange={(e) => setForm({ ...form, tipoLabor: e.target.value })}
            >
              {TIPOS_LABOR.map((t) => (
                <option key={t.valor} value={t.valor}>
                  {t.etiqueta}
                  {t.valor === 'LABRANZA'
                    ? ' (sin regla climática)'
                    : ' (requiere validación climática)'}
                </option>
              ))}
            </select>
          </div>
        </div>

        {loteBloqueaTipoLabor && (
          <div className="alerta alerta--error" role="alert">
            El lote <strong>{loteSeleccionado.nombre}</strong> se encuentra en
            estado{' '}
            <strong>{etiquetaEstadoLote(loteSeleccionado.estado)}</strong>.
            Según CU-03, en lotes cosechados o en descanso sólo pueden
            programarse labores de <strong>Labranza</strong>.
          </div>
        )}

        <div className="fila-campos">
          <div className="campo">
            <label htmlFor="labor-fecha">Fecha y hora planificada</label>
            <input
              id="labor-fecha"
              type="datetime-local"
              required
              value={form.fechaPlanificada}
              onChange={(e) =>
                setForm({ ...form, fechaPlanificada: e.target.value })
              }
            />
          </div>

          <div className="campo">
            <label htmlFor="labor-maquinaria">
              Maquinaria asignada (opcional)
            </label>
            <select
              id="labor-maquinaria"
              value={form.maquinariaId}
              onChange={(e) =>
                setForm({ ...form, maquinariaId: e.target.value })
              }
            >
              <option value="">Sin maquinaria (labor manual)</option>
              {maquinarias.map((m) => (
                <option
                  key={m.id}
                  value={m.id}
                  disabled={m.estado !== 'OPERATIVO'}
                >
                  {m.nombre} ({m.matriculaOSerie}) ·{' '}
                  {etiquetaTipoMaquinaria(m.tipo)} —{' '}
                  {m.estado === 'OPERATIVO'
                    ? `$ ${formatearNumero(m.costoOperativoHora)}/h${m.mantenimientoVencido ? ' · ⚠ Service vencido' : ''}`
                    : `No disponible (${etiquetaEstadoMaquinaria(m.estado)})`}
                </option>
              ))}
            </select>
          </div>
        </div>

        {maquinariaSeleccionada && (
          <div className="resumen-ajuste">
            <div>
              <strong>Maquinaria:</strong> {maquinariaSeleccionada.nombre}
            </div>
            <div>
              <strong>Costo operativo:</strong> ${' '}
              {formatearNumero(maquinariaSeleccionada.costoOperativoHora)} / h
            </div>
            <div>
              <strong>Consumo ref.:</strong>{' '}
              {formatearNumero(maquinariaSeleccionada.consumoLitrosHora)} L/h
            </div>
            {maquinariaSeleccionada.mantenimientoVencido && (
              <div className="texto-peligro">
                <strong>⚠ Advertencia (RN-06):</strong> Esta máquina superó el
                umbral de próximo service (
                {formatearNumero(maquinariaSeleccionada.horasUsoActuales)} /{' '}
                {formatearNumero(
                  maquinariaSeleccionada.horasParaProximoService,
                )}{' '}
                hs). Se recomienda registrar el mantenimiento antes de ejecutar
                la labor.
              </div>
            )}
          </div>
        )}

        <div className="campo">
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '0.5rem',
              flexWrap: 'wrap',
            }}
          >
            <label>Insumos a reservar (RN-03 / RN-07)</label>
            <button
              type="button"
              className="btn btn--secundario btn--chico"
              onClick={agregarLineaInsumo}
              disabled={lineasInsumo.length >= insumos.length}
            >
              + Agregar insumo
            </button>
          </div>
          <span className="texto-suave texto-chico">
            Al guardar la labor en estado Planificada, las cantidades indicadas
            quedan en estado Reservado y se descuentan del stock disponible.
          </span>

          {resumenInsumos.detalles.length === 0 ? (
            <p className="aviso" style={{ margin: '0.25rem 0 0' }}>
              Sin insumos asignados. Usá &laquo;+ Agregar insumo&raquo; para
              reservar semillas, fertilizantes, fitosanitarios o combustible.
            </p>
          ) : (
            <div className="tabla-contenedor" style={{ marginTop: '0.35rem' }}>
              <table className="tabla">
                <thead>
                  <tr>
                    <th>Insumo</th>
                    <th className="texto-derecha">Stock disponible</th>
                    <th>Cantidad a reservar</th>
                    <th className="texto-derecha">Subtotal est.</th>
                    <th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {resumenInsumos.detalles.map((det) => (
                    <tr
                      key={det.uid}
                      className={det.faltante > 0 ? 'fila--critica' : ''}
                    >
                      <td>
                        <select
                          aria-label="Seleccionar insumo"
                          value={det.insumoId}
                          onChange={(e) =>
                            actualizarLinea(det.uid, {
                              insumoId: e.target.value,
                            })
                          }
                          style={{ width: '100%' }}
                        >
                          {insumos.map((ins) => {
                            const usadoEnOtraFila =
                              String(ins.id) !== String(det.insumoId) &&
                              idsInsumosElegidos.has(ins.id)
                            return (
                              <option
                                key={ins.id}
                                value={ins.id}
                                disabled={usadoEnOtraFila}
                              >
                                {ins.nombre} ({ins.unidadMedida})
                              </option>
                            )
                          })}
                        </select>
                      </td>
                      <td className="texto-derecha">
                        <strong>{formatearNumero(det.disponible)}</strong>{' '}
                        <span className="texto-suave texto-chico">
                          {det.insumo?.unidadMedida}
                        </span>
                        <div className="texto-suave texto-chico">
                          Físico: {formatearNumero(det.insumo?.stockActual)} ·
                          Reservado:{' '}
                          {formatearNumero(det.insumo?.stockReservado)}
                        </div>
                      </td>
                      <td>
                        <input
                          type="number"
                          step="0.01"
                          min="0.01"
                          required
                          aria-label={`Cantidad de ${det.insumo?.nombre || 'insumo'}`}
                          placeholder="0.00"
                          value={det.cantidadUtilizada}
                          onChange={(e) =>
                            actualizarLinea(det.uid, {
                              cantidadUtilizada: e.target.value,
                            })
                          }
                          style={{ width: '120px' }}
                        />
                        {det.faltante > 0 && (
                          <div className="texto-peligro texto-chico">
                            Faltan {formatearNumero(det.faltante)}{' '}
                            {det.insumo?.unidadMedida}
                          </div>
                        )}
                      </td>
                      <td className="texto-derecha">
                        $ {formatearNumero(det.subtotal)}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn btn--peligro btn--chico"
                          onClick={() => quitarLinea(det.uid)}
                        >
                          Quitar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {resumenInsumos.detalles.length > 0 && (
            <div className="resumen-ajuste" style={{ marginTop: '0.4rem', marginBottom: 0 }}>
              <div>
                <strong>Insumos seleccionados:</strong>{' '}
                {resumenInsumos.detalles.length}
              </div>
              <div>
                <strong>Costo estimado de insumos:</strong> ${' '}
                {formatearNumero(resumenInsumos.costoEstimado)}
              </div>
            </div>
          )}
        </div>

        <div className="campo">
          <label htmlFor="labor-observaciones">
            Observaciones de planificación (opcional)
          </label>
          <textarea
            id="labor-observaciones"
            rows={2}
            maxLength={1000}
            value={form.observaciones}
            onChange={(e) =>
              setForm({ ...form, observaciones: e.target.value })
            }
            placeholder="Ej: Programada para validación con pronóstico del día."
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
            disabled={guardando || loteBloqueaTipoLabor}
          >
            {guardando ? 'Programando...' : 'Programar labor'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default LaborFormModal
