import { useCallback, useEffect, useState } from 'react'
import Modal from '../Modal.jsx'
import { obtenerClimaLote } from '../../services/lotesService.js'
import { formatearFecha } from '../../utils/formatters.js'

function formatearMedida(valor, decimales = 1) {
  if (valor === null || valor === undefined) return '-'
  return Number(valor).toLocaleString('es-AR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimales,
  })
}

function ClimaLoteModal({ lote, onCerrar }) {
  const [clima, setClima] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  const consultar = useCallback(async () => {
    setCargando(true)
    setError('')
    try {
      setClima(await obtenerClimaLote(lote.id))
    } catch (err) {
      setClima(null)
      setError(err.message || 'No se pudo obtener el clima del lote.')
    } finally {
      setCargando(false)
    }
  }, [lote.id])

  useEffect(() => {
    let cancelado = false
    obtenerClimaLote(lote.id)
      .then((datos) => {
        if (!cancelado) setClima(datos)
      })
      .catch((err) => {
        if (!cancelado) {
          setError(err.message || 'No se pudo obtener el clima del lote.')
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
  }, [lote.id])

  return (
    <Modal
      abierto
      ancho
      titulo={`Clima actual: ${lote.nombre}`}
      onCerrar={onCerrar}
    >
      <p className="texto-suave texto-chico">
        Coordenadas: {lote.latitud}, {lote.longitud}
      </p>

      {cargando && !clima && (
        <p className="aviso">Consultando el servicio meteorológico...</p>
      )}

      {error && (
        <div className="alerta alerta--error" role="alert">
          {error}
        </div>
      )}

      {clima && (
        <>
          {clima.origen === 'CACHE' && (
            <div className="alerta alerta--advertencia" role="status">
              El servicio meteorológico no responde. Se muestra la última
              lectura disponible, obtenida el {formatearFecha(clima.obtenidaEn)}
              .
            </div>
          )}

          <div className="tarjetas-resumen">
            <div className="tarjeta-kpi">
              <span className="tarjeta-kpi__titulo">Temperatura</span>
              <strong className="tarjeta-kpi__valor">
                {formatearMedida(clima.temperaturaC)} °C
              </strong>
              <span className="tarjeta-kpi__detalle">A 2 m del suelo</span>
            </div>

            <div className="tarjeta-kpi">
              <span className="tarjeta-kpi__titulo">Viento</span>
              <strong className="tarjeta-kpi__valor">
                {formatearMedida(clima.vientoVelocidadKmh)} km/h
              </strong>
              <span className="tarjeta-kpi__detalle">A 10 m del suelo</span>
            </div>

            <div className="tarjeta-kpi">
              <span className="tarjeta-kpi__titulo">
                Probabilidad de lluvia
              </span>
              <strong className="tarjeta-kpi__valor">
                {formatearMedida(clima.probabilidadLluviaPct, 0)} %
              </strong>
              <span className="tarjeta-kpi__detalle">
                Precipitación: {formatearMedida(clima.precipitacionMm)} mm
              </span>
            </div>

            <div className="tarjeta-kpi">
              <span className="tarjeta-kpi__titulo">Humedad relativa</span>
              <strong className="tarjeta-kpi__valor">
                {formatearMedida(clima.humedadRelativaPct, 0)} %
              </strong>
              <span className="tarjeta-kpi__detalle">A 2 m del suelo</span>
            </div>
          </div>

          <p className="texto-suave texto-chico">
            Lectura del {formatearFecha(clima.fechaLectura)} (hora local del
            lote) ·{' '}
            <span
              className={`etiqueta ${clima.origen === 'API' ? 'etiqueta--ok' : 'etiqueta--advertencia'}`}
            >
              {clima.origen === 'API' ? 'En tiempo real' : 'Desde caché'}
            </span>
          </p>
        </>
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
          type="button"
          className="btn btn--primario"
          onClick={consultar}
          disabled={cargando}
        >
          {cargando ? 'Consultando...' : 'Actualizar'}
        </button>
      </div>
    </Modal>
  )
}

export default ClimaLoteModal
