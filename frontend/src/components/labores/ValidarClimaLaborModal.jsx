import { useEffect, useState } from 'react'
import Modal from '../Modal.jsx'
import { tieneAlgunRol } from '../../services/api.js'
import {
  autorizarLaborManualmente,
  validarClimaLabor,
} from '../../services/laboresService.js'
import { etiquetaTipoLabor, formatearFecha } from '../../utils/formatters.js'

function formatearMedida(valor, decimales = 1) {
  if (valor === null || valor === undefined) return '-'
  return Number(valor).toLocaleString('es-AR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimales,
  })
}

// HU-10 / CU-04: al abrirse valida el clima actual del lote contra la regla del tipo de labor.
function ValidarClimaLaborModal({ labor, onCerrar }) {
  // RN-11: sólo AGRONOMO o ADMIN pueden autorizar manualmente ante una contingencia climática.
  const puedeAutorizarManual = tieneAlgunRol('ADMIN', 'AGRONOMO')

  const [resultado, setResultado] = useState(null)
  const [validando, setValidando] = useState(true)
  const [error, setError] = useState('')
  const [climaNoDisponible, setClimaNoDisponible] = useState(false)

  const [justificacion, setJustificacion] = useState('')
  const [autorizando, setAutorizando] = useState(false)
  const [errorAutorizacion, setErrorAutorizacion] = useState('')

  useEffect(() => {
    let cancelado = false
    validarClimaLabor(labor.id)
      .then((datos) => {
        if (!cancelado) setResultado(datos)
      })
      .catch((err) => {
        if (!cancelado) {
          setError(err.message || 'No se pudo validar el clima de la labor.')
          setClimaNoDisponible(err.status === 503)
        }
      })
      .finally(() => {
        if (!cancelado) setValidando(false)
      })
    return () => {
      cancelado = true
    }
  }, [labor.id])

  async function autorizar(e) {
    e.preventDefault()
    setErrorAutorizacion('')
    setAutorizando(true)
    try {
      const datos = await autorizarLaborManualmente(
        labor.id,
        justificacion.trim(),
      )
      setError('')
      setClimaNoDisponible(false)
      setResultado(datos)
    } catch (err) {
      setErrorAutorizacion(
        err.message || 'No se pudo registrar la autorización manual.',
      )
    } finally {
      setAutorizando(false)
    }
  }

  const justificacionValida = justificacion.trim().length >= 10

  return (
    <Modal
      abierto
      ancho
      titulo={`Validación climática: orden #${labor.id}`}
      onCerrar={() => onCerrar(Boolean(resultado))}
    >
      <div className="resumen-ajuste">
        <div>
          <span>Labor:</span>{' '}
          <strong>{etiquetaTipoLabor(labor.tipoLabor)}</strong>
        </div>
        <div>
          <span>Lote:</span> <strong>{labor.loteNombre}</strong>
        </div>
        <div>
          <span>Planificada:</span>{' '}
          <strong>{formatearFecha(labor.fechaPlanificada)}</strong>
        </div>
      </div>

      {validando && (
        <p className="aviso">
          Consultando el clima actual del lote y evaluando la regla
          agronómica...
        </p>
      )}

      {error && (
        <div className="alerta alerta--error" role="alert">
          {error}
        </div>
      )}

      {resultado?.autorizacionManual && (
        <div className="alerta alerta--exito" role="status">
          Labor autorizada manualmente por contingencia climática (RN-11). Ya
          puede iniciarse; la justificación quedó registrada en las
          observaciones.
        </div>
      )}

      {resultado && !resultado.autorizacionManual && (
        <>
          {resultado.aprobada ? (
            <div className="alerta alerta--exito" role="status">
              Clima apto: la labor quedó <strong>aprobada por clima</strong> y
              ya puede iniciarse.
            </div>
          ) : (
            <div className="alerta alerta--error" role="alert">
              <strong>Labor bloqueada por clima.</strong>{' '}
              {resultado.mensajeAlerta}
              <ul>
                {resultado.incumplimientos.map((motivo) => (
                  <li key={motivo}>{motivo}</li>
                ))}
              </ul>
              Las reservas de insumos se mantienen. Podés volver a validar
              cuando mejoren las condiciones o reprogramar la labor.
            </div>
          )}

          {resultado.origen === 'CACHE' && (
            <div className="alerta alerta--advertencia" role="status">
              El servicio meteorológico no responde: se evaluó la última lectura
              disponible, obtenida el {formatearFecha(resultado.obtenidaEn)}.
            </div>
          )}

          <div className="tarjetas-resumen">
            <div className="tarjeta-kpi">
              <span className="tarjeta-kpi__titulo">Temperatura</span>
              <strong className="tarjeta-kpi__valor">
                {formatearMedida(resultado.temperaturaC)} °C
              </strong>
            </div>
            <div className="tarjeta-kpi">
              <span className="tarjeta-kpi__titulo">Viento</span>
              <strong className="tarjeta-kpi__valor">
                {formatearMedida(resultado.vientoVelocidadKmh)} km/h
              </strong>
            </div>
            <div className="tarjeta-kpi">
              <span className="tarjeta-kpi__titulo">
                Probabilidad de lluvia
              </span>
              <strong className="tarjeta-kpi__valor">
                {formatearMedida(resultado.probabilidadLluviaPct, 0)} %
              </strong>
            </div>
            <div className="tarjeta-kpi">
              <span className="tarjeta-kpi__titulo">Humedad relativa</span>
              <strong className="tarjeta-kpi__valor">
                {formatearMedida(resultado.humedadRelativaPct, 0)} %
              </strong>
            </div>
          </div>

          <p className="texto-suave texto-chico">
            Lectura del {formatearFecha(resultado.fechaLectura)} (hora local del
            lote). Los valores quedaron registrados en la labor.
          </p>
        </>
      )}

      {climaNoDisponible &&
        (puedeAutorizarManual ? (
          <form onSubmit={autorizar} className="formulario">
            <p className="texto-suave texto-chico">
              Sin datos climáticos, un agrónomo o administrador puede autorizar
              la labor manualmente dejando constancia de la justificación
              (RN-11).
            </p>
            {errorAutorizacion && (
              <div className="alerta alerta--error">{errorAutorizacion}</div>
            )}
            <div className="campo">
              <label htmlFor="clima-justificacion">
                Justificación de la autorización manual
              </label>
              <textarea
                id="clima-justificacion"
                rows={3}
                maxLength={500}
                required
                value={justificacion}
                onChange={(e) => setJustificacion(e.target.value)}
                placeholder="Ej: Viento calmo y cielo despejado verificados en el lote con anemómetro portátil"
              />
            </div>
            <div className="modal__pie">
              <button
                type="button"
                className="btn btn--secundario"
                onClick={() => onCerrar(false)}
              >
                Cerrar
              </button>
              <button
                type="submit"
                className="btn btn--primario"
                disabled={autorizando || !justificacionValida}
              >
                {autorizando ? 'Autorizando...' : 'Autorizar manualmente'}
              </button>
            </div>
          </form>
        ) : (
          <p className="aviso">
            Reintentá en unos minutos o pedile a un agrónomo que autorice la
            labor manualmente.
          </p>
        ))}

      {!(climaNoDisponible && puedeAutorizarManual) && (
        <div className="modal__pie">
          <button
            type="button"
            className="btn btn--secundario"
            onClick={() => onCerrar(Boolean(resultado))}
            disabled={validando}
          >
            Cerrar
          </button>
        </div>
      )}
    </Modal>
  )
}

export default ValidarClimaLaborModal
