import { useMemo, useState } from 'react'
import Modal from '../Modal.jsx'
import { completarLabor } from '../../services/laboresService.js'
import {
  etiquetaEstadoLabor,
  etiquetaEstadoLote,
  etiquetaTipoLabor,
  formatearFecha,
  formatearNumero,
} from '../../utils/formatters.js'

const HORAS_POR_DIA = 24

function calcularTopeHorasDesdeInicio(fechaEjecucion) {
  if (!fechaEjecucion) return HORAS_POR_DIA
  const inicio = new Date(fechaEjecucion)
  if (Number.isNaN(inicio.getTime())) return HORAS_POR_DIA
  const hoy = new Date()
  const inicioDia = new Date(
    inicio.getFullYear(),
    inicio.getMonth(),
    inicio.getDate(),
  )
  const hoyDia = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate())
  const diffMs = hoyDia.getTime() - inicioDia.getTime()
  const dias = Math.floor(diffMs / (1000 * 60 * 60 * 24)) + 1
  return Math.max(1, dias) * HORAS_POR_DIA
}

function calcularHorasTranscurridasSugeridas(fechaEjecucion, topeHoras) {
  if (!fechaEjecucion) return null
  const inicio = new Date(fechaEjecucion)
  if (Number.isNaN(inicio.getTime())) return null
  const diffHoras = (Date.now() - inicio.getTime()) / (1000 * 60 * 60)
  const acotadas = Math.min(topeHoras, Math.max(0.25, diffHoras))
  return (Math.round(acotadas * 4) / 4).toFixed(2)
}

function CompletarLaborModal({ labor, maquinarias = [], onCerrar, onGuardado }) {
  const tieneMaquinaria = Boolean(labor?.maquinariaId)

  const maquinariaActual = useMemo(() => {
    if (!labor?.maquinariaId) return null
    return (
      maquinarias.find((m) => Number(m.id) === Number(labor.maquinariaId)) ||
      null
    )
  }, [maquinarias, labor])

  const [horasMaquina, setHorasMaquina] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  const horasNum = useMemo(() => {
    const n = Number(horasMaquina)
    return Number.isFinite(n) && n > 0 ? n : 0
  }, [horasMaquina])

  const topeHoras = useMemo(
    () => calcularTopeHorasDesdeInicio(labor?.fechaEjecucion),
    [labor],
  )

  const horasSugeridasDesdeInicio = useMemo(
    () =>
      calcularHorasTranscurridasSugeridas(labor?.fechaEjecucion, topeHoras),
    [labor, topeHoras],
  )

  const costoHoraMaquina = tieneMaquinaria
    ? Number(
        labor?.maquinariaCostoOperativoHora ??
          maquinariaActual?.costoOperativoHora ??
          0,
      )
    : 0

  const costoMaquinariaCalculado = tieneMaquinaria
    ? Math.round(horasNum * costoHoraMaquina * 100) / 100
    : 0

  const costoInsumos = Number(
    labor?.costoInsumosConsumido ?? labor?.costoInsumosEstimado ?? 0,
  )

  const costoTotalProyectado =
    Math.round((costoInsumos + costoMaquinariaCalculado) * 100) / 100

  const proyeccionHorometro = useMemo(() => {
    if (!maquinariaActual) return null
    const actuales = Number(maquinariaActual.horasUsoActuales || 0)
    const proximo = Number(maquinariaActual.horasParaProximoService || 0)
    const nuevasHoras = Math.round((actuales + horasNum) * 100) / 100
    const superaraService = proximo > 0 && nuevasHoras >= proximo
    return { actuales, proximo, nuevasHoras, superaraService }
  }, [maquinariaActual, horasNum])

  const tieneCombustibleEnOrden = useMemo(() => {
    return Boolean(
      labor?.insumos?.some((i) => i.insumoCategoria === 'COMBUSTIBLE'),
    )
  }, [labor])

  const estimacionCombustibleFaltante = useMemo(() => {
    if (!tieneMaquinaria || tieneCombustibleEnOrden || horasNum <= 0) {
      return null
    }
    const consumoPorHora = Number(maquinariaActual?.consumoLitrosHora || 0)
    if (consumoPorHora <= 0) return null
    const litrosEstimados = Math.round(horasNum * consumoPorHora * 100) / 100
    return { consumoPorHora, litrosEstimados }
  }, [tieneMaquinaria, tieneCombustibleEnOrden, horasNum, maquinariaActual])

  const cambiaraLoteASembrado =
    labor?.tipoLabor === 'SIEMBRA' && labor?.loteEstado === 'EN_PREPARACION'

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')

    if (tieneMaquinaria) {
      if (horasMaquina === '' || horasNum <= 0) {
        setError(
          'Indicá las horas reales de máquina empleadas (deben ser mayores a 0).',
        )
        return
      }
      if (horasNum > topeHoras) {
        setError(
          `Las horas informadas (${formatearNumero(horasNum)} hs) superan el máximo posible desde el inicio de la labor (${topeHoras} hs: 24 hs por día calendario).`,
        )
        return
      }
    }

    setGuardando(true)
    try {
      const payload = {
        horasMaquinaConsumidas: tieneMaquinaria ? horasNum : 0,
        observaciones: observaciones.trim() || null,
      }
      const actualizada = await completarLabor(labor.id, payload)
      const avisoService = actualizada.maquinariaMantenimientoVencido
        ? ` ⚠ Atención (RN-06): La maquinaria «${actualizada.maquinariaNombre}» alcanzó el umbral de mantenimiento preventivo.`
        : ''
      onGuardado(
        `Orden #${actualizada.id} completada. Costo total consolidado: $ ${formatearNumero(actualizada.costoTotalCalculado)}.${avisoService}`,
      )
    } catch (err) {
      setError(err.message || 'No se pudo completar la orden de trabajo.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho
      titulo={`Completar orden de trabajo #${labor.id} (HU-11)`}
      onCerrar={() => {
        if (!guardando) onCerrar()
      }}
    >
      <form className="formulario" onSubmit={manejarSubmit} noValidate>
        <div className="resumen-ajuste">
          <div>
            <strong>Orden:</strong> #{labor.id} ·{' '}
            {etiquetaTipoLabor(labor.tipoLabor)}
          </div>
          <div>
            <strong>Estado actual:</strong> {etiquetaEstadoLabor(labor.estado)}
          </div>
          <div>
            <strong>Inicio de ejecución:</strong>{' '}
            {formatearFecha(labor.fechaEjecucion)}
          </div>
          <div>
            <strong>Lote:</strong> {labor.loteNombre} (
            {formatearNumero(labor.loteSuperficieHa)} ha ·{' '}
            {etiquetaEstadoLote(labor.loteEstado)})
          </div>
          <div>
            <strong>Maquinaria:</strong>{' '}
            {tieneMaquinaria
              ? `${labor.maquinariaNombre} (${labor.maquinariaMatriculaOSerie})`
              : 'Sin maquinaria (labor manual)'}
          </div>
        </div>

        {cambiaraLoteASembrado && (
          <div className="alerta alerta--exito" role="status">
            Al completar esta labor de <strong>Siembra</strong>, el lote{' '}
            <strong>{labor.loteNombre}</strong> pasará automáticamente al estado{' '}
            <strong>Sembrado</strong> (RN-08).
          </div>
        )}

        {tieneMaquinaria && (
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
              <label htmlFor="completar-horas">
                Horas reales de máquina empleadas * (máx. {topeHoras} hs)
              </label>
              {horasSugeridasDesdeInicio && (
                <button
                  type="button"
                  className="btn btn--secundario btn--chico"
                  onClick={() => setHorasMaquina(horasSugeridasDesdeInicio)}
                  disabled={guardando}
                >
                  Usar tiempo desde inicio ({horasSugeridasDesdeInicio} hs)
                </button>
              )}
            </div>
            <input
              id="completar-horas"
              type="number"
              step="0.01"
              min="0.01"
              max={topeHoras}
              placeholder="Ej. 8.50"
              value={horasMaquina}
              onChange={(e) => setHorasMaquina(e.target.value)}
              disabled={guardando}
              required
            />
            {proyeccionHorometro && (
              <span className="campo__ayuda">
                Horómetro actual:{' '}
                <strong>
                  {formatearNumero(proyeccionHorometro.actuales)} hs
                </strong>{' '}
                → tras cierre:{' '}
                <strong>
                  {formatearNumero(proyeccionHorometro.nuevasHoras)} hs
                </strong>{' '}
                (próximo service:{' '}
                {formatearNumero(proyeccionHorometro.proximo)} hs)
                {proyeccionHorometro.superaraService && (
                  <strong style={{ color: '#b45309' }}>
                    {' '}
                    · ⚠ Alcanzará el umbral de mantenimiento (RN-06)
                  </strong>
                )}
              </span>
            )}
          </div>
        )}

        {estimacionCombustibleFaltante && (
          <div className="alerta alerta--advertencia" role="note">
            <strong>Aviso de combustible:</strong> Esta orden no incluye un
            insumo de categoría <strong>Combustible</strong>. El costo operativo
            por hora no incluye gasoil (consumo estimado para{' '}
            {formatearNumero(horasNum)} hs:{' '}
            <strong>
              {formatearNumero(estimacionCombustibleFaltante.litrosEstimados)} L
            </strong>{' '}
            a {formatearNumero(estimacionCombustibleFaltante.consumoPorHora)}{' '}
            L/h).
          </div>
        )}

        <div className="resumen-ajuste">
          <div>
            <strong>Insumos consumidos al iniciar:</strong>{' '}
            {!labor.insumos || labor.insumos.length === 0
              ? 'Esta orden no tiene insumos asociados.'
              : `${labor.insumos.length} línea(s) en estado Consumido.`}
          </div>
          <div>
            <strong>Subtotal insumos (histórico, RN-05):</strong> ${' '}
            {formatearNumero(costoInsumos)}
          </div>
          {tieneMaquinaria && (
            <div>
              <strong>
                Costo operativo maquinaria ({formatearNumero(horasNum)} hs × ${' '}
                {formatearNumero(costoHoraMaquina)}/h):
              </strong>{' '}
              $ {formatearNumero(costoMaquinariaCalculado)}
            </div>
          )}
          <div>
            <strong>Costo total consolidado (RN-05, inmutable):</strong>{' '}
            <strong>$ {formatearNumero(costoTotalProyectado)}</strong>
          </div>
        </div>

        <div className="campo">
          <label htmlFor="completar-obs">
            Observaciones de cierre (opcional)
          </label>
          <textarea
            id="completar-obs"
            rows={2}
            maxLength={1000}
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            disabled={guardando}
            placeholder="Novedades operativas, estado del lote al finalizar..."
          />
        </div>

        {error && (
          <div className="alerta alerta--error" role="alert">
            {error}
          </div>
        )}

        <div className="modal__pie">
          <button
            type="button"
            className="btn btn--secundario"
            onClick={onCerrar}
            disabled={guardando}
          >
            Cancelar
          </button>
          <button
            type="submit"
            className="btn btn--primario"
            disabled={guardando}
          >
            {guardando ? 'Registrando cierre...' : 'Confirmar cierre de labor'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default CompletarLaborModal
