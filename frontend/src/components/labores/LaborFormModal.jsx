import { useMemo, useState } from 'react'
import Modal from '../Modal.jsx'
import { actualizarLabor, programarLabor } from '../../services/laboresService.js'
import {
  TIPOS_LABOR,
  etiquetaEstadoLote,
  etiquetaEstadoMaquinaria,
  etiquetaTipoLabor,
  etiquetaTipoMaquinaria,
  fechaHoraManianaIso,
  formatearFecha,
  formatearNumero,
} from '../../utils/formatters.js'

function aDatetimeLocal(iso) {
  if (!iso) return fechaHoraManianaIso()
  return String(iso).slice(0, 16)
}

function obtenerIncompatibilidadAgronomica(lote, tipoLabor) {
  if (!lote || !tipoLabor) return ''
  const estado = lote.estado
  const etiquetaEst = etiquetaEstadoLote(estado)
  const etiquetaLab = etiquetaTipoLabor(tipoLabor)

  if (
    (estado === 'COSECHADO' || estado === 'DESCANSO') &&
    tipoLabor !== 'LABRANZA'
  ) {
    return `El lote "${lote.nombre}" está en estado "${etiquetaEst}": sólo admite labores de Labranza (CU-03).`
  }

  if (tipoLabor === 'SIEMBRA' && estado !== 'EN_PREPARACION') {
    return `Sólo se puede programar Siembra cuando el lote está "En preparación" (estado actual de "${lote.nombre}": ${etiquetaEst}).`
  }

  if (
    tipoLabor === 'LABRANZA' &&
    (estado === 'SEMBRADO' ||
      estado === 'EN_CRECIMIENTO' ||
      estado === 'LISTO_COSECHA')
  ) {
    return `No se puede programar Labranza en "${lote.nombre}" porque tiene cultivo activo (${etiquetaEst}).`
  }

  if (
    tipoLabor === 'COSECHA' &&
    estado !== 'LISTO_COSECHA' &&
    estado !== 'EN_CRECIMIENTO'
  ) {
    return `No se puede programar ${etiquetaLab} en "${lote.nombre}" porque su estado actual es "${etiquetaEst}" (debe estar "Listo para cosecha" o "En crecimiento").`
  }

  return ''
}

function redondearDosDecimales(num) {
  if (!Number.isFinite(num) || num <= 0) return ''
  return String(Math.round(num * 100) / 100)
}

function LaborFormModal({
  labor = null,
  lotes,
  maquinarias,
  insumos,
  laboresExistentes = [],
  onCerrar,
  onGuardado,
}) {
  const loteInicial =
    labor?.loteId ??
    lotes.find(
      (l) => l.estado !== 'COSECHADO' && l.estado !== 'DESCANSO',
    )?.id ??
    lotes[0]?.id ??
    ''

  const superficieInicial = Number(
    lotes.find((l) => String(l.id) === String(loteInicial))?.superficieHa ?? 0,
  )

  const [form, setForm] = useState(() => ({
    loteId: loteInicial ? String(loteInicial) : '',
    tipoLabor: labor?.tipoLabor ?? 'PULVERIZACION',
    fechaPlanificada: labor
      ? aDatetimeLocal(labor.fechaPlanificada)
      : fechaHoraManianaIso(),
    maquinariaId: labor?.maquinariaId ? String(labor.maquinariaId) : '',
    observaciones: labor?.observacionesClimaticas ?? '',
  }))

  const [horasEstimadasMaquina, setHorasEstimadasMaquina] = useState('6')

  const [lineasInsumo, setLineasInsumo] = useState(() => {
    if (!labor?.insumos?.length) return []
    return labor.insumos
      .filter((item) => item.estadoReserva === 'RESERVADO')
      .map((item, idx) => {
        const cant = Number(item.cantidadUtilizada || 0)
        const dosis =
          superficieInicial > 0
            ? redondearDosDecimales(cant / superficieInicial)
            : ''
        return {
          uid: `init-${idx}-${item.insumoId}`,
          insumoId: String(item.insumoId),
          dosisPorHa: dosis,
          cantidadUtilizada: String(item.cantidadUtilizada ?? ''),
        }
      })
  })

  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  const reservasPreviasEnEstaLabor = useMemo(() => {
    const mapa = new Map()
    if (labor?.insumos) {
      for (const item of labor.insumos) {
        if (item.estadoReserva === 'RESERVADO') {
          mapa.set(Number(item.insumoId), Number(item.cantidadUtilizada || 0))
        }
      }
    }
    return mapa
  }, [labor])

  const loteSeleccionado = useMemo(
    () => lotes.find((l) => String(l.id) === String(form.loteId)) || null,
    [lotes, form.loteId],
  )

  const superficieLoteHa = Number(loteSeleccionado?.superficieHa || 0)

  const maquinariaSeleccionada = useMemo(
    () =>
      maquinarias.find((m) => String(m.id) === String(form.maquinariaId)) ||
      null,
    [maquinarias, form.maquinariaId],
  )

  const incompatibilidadLote = useMemo(
    () => obtenerIncompatibilidadAgronomica(loteSeleccionado, form.tipoLabor),
    [loteSeleccionado, form.tipoLabor],
  )

  const conflictoHorarioMaquinaria = useMemo(() => {
    if (!maquinariaSeleccionada || !form.fechaPlanificada) return null
    const instanteElegido = new Date(form.fechaPlanificada).getTime()
    if (!Number.isFinite(instanteElegido)) return null
    const ventanaMs = 119 * 60 * 1000

    return (
      laboresExistentes.find((otra) => {
        if (labor && otra.id === labor.id) return false
        if (String(otra.maquinariaId) !== String(maquinariaSeleccionada.id)) {
          return false
        }
        if (
          otra.estado !== 'PLANIFICADA' &&
          otra.estado !== 'APROBADA_CLIMA' &&
          otra.estado !== 'BLOQUEADA_CLIMA'
        ) {
          return false
        }
        const instanteOtra = new Date(otra.fechaPlanificada).getTime()
        return Math.abs(instanteElegido - instanteOtra) <= ventanaMs
      }) || null
    )
  }, [maquinariaSeleccionada, form.fechaPlanificada, laboresExistentes, labor])

  const idsInsumosElegidos = useMemo(
    () =>
      new Set(
        lineasInsumo
          .map((l) => Number(l.insumoId))
          .filter((id) => Number.isFinite(id) && id > 0),
      ),
    [lineasInsumo],
  )

  function cambiarLote(nuevoLoteId) {
    const nuevoLote = lotes.find((l) => String(l.id) === String(nuevoLoteId))
    const nuevaSup = Number(nuevoLote?.superficieHa || 0)
    setForm((prev) => ({ ...prev, loteId: nuevoLoteId }))
    if (nuevaSup > 0) {
      setLineasInsumo((prev) =>
        prev.map((linea) => {
          const dosis = Number(linea.dosisPorHa)
          if (Number.isFinite(dosis) && dosis > 0) {
            return {
              ...linea,
              cantidadUtilizada: redondearDosDecimales(dosis * nuevaSup),
            }
          }
          return linea
        }),
      )
    }
  }

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
        dosisPorHa: '',
        cantidadUtilizada: '',
      },
    ])
  }

  function actualizarDosisPorHa(uid, valorDosis) {
    const dosisNum = Number(valorDosis)
    const cantidadCalculada =
      superficieLoteHa > 0 && Number.isFinite(dosisNum) && dosisNum > 0
        ? redondearDosDecimales(dosisNum * superficieLoteHa)
        : ''
    setLineasInsumo((prev) =>
      prev.map((linea) =>
        linea.uid === uid
          ? {
              ...linea,
              dosisPorHa: valorDosis,
              cantidadUtilizada:
                valorDosis === '' ? linea.cantidadUtilizada : cantidadCalculada,
            }
          : linea,
      ),
    )
  }

  function actualizarCantidadTotal(uid, valorCantidad) {
    const cantNum = Number(valorCantidad)
    const dosisEquivalente =
      superficieLoteHa > 0 && Number.isFinite(cantNum) && cantNum > 0
        ? redondearDosDecimales(cantNum / superficieLoteHa)
        : ''
    setLineasInsumo((prev) =>
      prev.map((linea) =>
        linea.uid === uid
          ? {
              ...linea,
              cantidadUtilizada: valorCantidad,
              dosisPorHa: dosisEquivalente,
            }
          : linea,
      ),
    )
  }

  function actualizarInsumoFila(uid, nuevoInsumoId) {
    setLineasInsumo((prev) =>
      prev.map((linea) =>
        linea.uid === uid ? { ...linea, insumoId: nuevoInsumoId } : linea,
      ),
    )
  }

  function aplicarConsumoCombustible(uid) {
    if (!maquinariaSeleccionada) return
    const horas = Number(horasEstimadasMaquina)
    const consumoHora = Number(maquinariaSeleccionada.consumoLitrosHora || 0)
    if (!Number.isFinite(horas) || horas <= 0 || consumoHora <= 0) return
    const litrosTotales = redondearDosDecimales(horas * consumoHora)
    actualizarCantidadTotal(uid, litrosTotales)
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
      const reservaPreviaPropia =
        reservasPreviasEnEstaLabor.get(Number(linea.insumoId)) || 0
      const disponible =
        Number(insumo?.stockDisponible ?? 0) + reservaPreviaPropia
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
        reservaPreviaPropia,
        subtotal,
        faltante,
      }
    })

    const costoEstimado = detalles.reduce((acc, d) => acc + d.subtotal, 0)
    const hayExcesoStock = detalles.some((d) => d.faltante > 0)

    return { detalles, costoEstimado, hayExcesoStock }
  }, [lineasInsumo, insumos, reservasPreviasEnEstaLabor])

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')

    if (!form.loteId) {
      setError('Seleccioná un lote para programar la orden de trabajo.')
      return
    }

    if (incompatibilidadLote) {
      setError(incompatibilidadLote)
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
      if (labor) {
        const actualizada = await actualizarLabor(labor.id, payload)
        onGuardado(
          `Orden de trabajo #${actualizada.id} (${etiquetaTipoLabor(actualizada.tipoLabor)} en "${actualizada.loteNombre}") actualizada correctamente.`,
        )
      } else {
        const creada = await programarLabor(payload)
        onGuardado(
          `Orden de trabajo #${creada.id} (${etiquetaTipoLabor(creada.tipoLabor)} en "${creada.loteNombre}") programada en estado Planificada.`,
        )
      }
    } catch (err) {
      setError(err.message || 'No se pudo guardar la orden de trabajo.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho
      titulo={
        labor
          ? `Editar orden de trabajo #${labor.id}`
          : 'Programar orden de trabajo (CU-03)'
      }
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
              onChange={(e) => cambiarLote(e.target.value)}
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

        {incompatibilidadLote && (
          <div className="alerta alerta--error" role="alert">
            {incompatibilidadLote}
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

        {conflictoHorarioMaquinaria && (
          <div className="alerta alerta--error" role="alert">
            <strong>Conflicto horario de maquinaria:</strong>{' '}
            {maquinariaSeleccionada?.nombre} ya está asignada a la orden
            pendiente #{conflictoHorarioMaquinaria.id} en &laquo;
            {conflictoHorarioMaquinaria.loteNombre}&raquo; para el{' '}
            {formatearFecha(conflictoHorarioMaquinaria.fechaPlanificada)} (se
            requieren al menos 2 horas de separación).
          </div>
        )}

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
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
              <label htmlFor="horas-est-maq" className="texto-chico">
                <strong>Horas est.:</strong>
              </label>
              <input
                id="horas-est-maq"
                type="number"
                step="0.5"
                min="0.5"
                value={horasEstimadasMaquina}
                onChange={(e) => setHorasEstimadasMaquina(e.target.value)}
                style={{ width: '70px', padding: '0.2rem 0.4rem' }}
              />
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
            <label>
              Insumos a reservar (RN-03 / RN-07)
              {superficieLoteHa > 0
                ? ` · Superficie del lote: ${formatearNumero(superficieLoteHa)} ha`
                : ''}
            </label>
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
            Podés ingresar la <strong>Dosis / ha</strong> para calcular
            automáticamente el total según las hectáreas del lote, o cargar
            directamente la <strong>Cantidad total</strong>.
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
                    <th>Dosis / ha</th>
                    <th>Cantidad total</th>
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
                            actualizarInsumoFila(det.uid, e.target.value)
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
                          min="0"
                          aria-label={`Dosis por ha de ${det.insumo?.nombre || 'insumo'}`}
                          placeholder={
                            superficieLoteHa > 0 ? 'Ej: 2.00' : 'Seleccione lote'
                          }
                          disabled={superficieLoteHa <= 0}
                          value={det.dosisPorHa}
                          onChange={(e) =>
                            actualizarDosisPorHa(det.uid, e.target.value)
                          }
                          style={{ width: '95px' }}
                        />
                        <div className="texto-suave texto-chico">
                          {det.insumo?.unidadMedida} / ha
                        </div>
                      </td>
                      <td>
                        <input
                          type="number"
                          step="0.01"
                          min="0.01"
                          required
                          aria-label={`Cantidad total de ${det.insumo?.nombre || 'insumo'}`}
                          placeholder="0.00"
                          value={det.cantidadUtilizada}
                          onChange={(e) =>
                            actualizarCantidadTotal(det.uid, e.target.value)
                          }
                          style={{ width: '115px' }}
                        />
                        {det.insumo?.categoria === 'COMBUSTIBLE' &&
                          maquinariaSeleccionada &&
                          Number(maquinariaSeleccionada.consumoLitrosHora) >
                            0 && (
                            <div style={{ marginTop: '0.25rem' }}>
                              <button
                                type="button"
                                className="btn btn--secundario btn--chico"
                                onClick={() =>
                                  aplicarConsumoCombustible(det.uid)
                                }
                              >
                                Usar {horasEstimadasMaquina || 0}h ×{' '}
                                {formatearNumero(
                                  maquinariaSeleccionada.consumoLitrosHora,
                                )}{' '}
                                L/h
                              </button>
                            </div>
                          )}
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
            <div
              className="resumen-ajuste"
              style={{ marginTop: '0.4rem', marginBottom: 0 }}
            >
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
            disabled={
              guardando ||
              Boolean(incompatibilidadLote) ||
              Boolean(conflictoHorarioMaquinaria)
            }
          >
            {guardando
              ? 'Guardando...'
              : labor
                ? 'Guardar cambios'
                : 'Programar labor'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default LaborFormModal
