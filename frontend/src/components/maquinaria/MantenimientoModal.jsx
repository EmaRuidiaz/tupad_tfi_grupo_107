import { useEffect, useState } from 'react'
import Modal from '../Modal.jsx'
import {
  listarMantenimientos,
  registrarMantenimiento,
} from '../../services/maquinariaService.js'
import {
  TIPOS_MANTENIMIENTO,
  etiquetaEstadoMaquinaria,
  etiquetaTipoMantenimiento,
  fechaHoyIso,
  formatearFechaSimple,
  formatearNumero,
} from '../../utils/formatters.js'

function MantenimientoModal({ maquinaria, onCerrar, onGuardado }) {
  // CU-06: no se registra service con la máquina en labor ni fuera de servicio.
  const puedeRegistrar =
    maquinaria.estado === 'OPERATIVO' ||
    maquinaria.estado === 'EN_MANTENIMIENTO'

  const [historial, setHistorial] = useState([])
  const [cargando, setCargando] = useState(true)
  const [errorHistorial, setErrorHistorial] = useState('')

  const [form, setForm] = useState({
    fechaService: fechaHoyIso(),
    horasMaquinaEnService: String(maquinaria.horasUsoActuales ?? ''),
    tipoMantenimiento: 'PREVENTIVO_PROGRAMADO',
    costo: '',
    proximoServiceHoras: '',
    descripcion: '',
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelado = false
    listarMantenimientos(maquinaria.id)
      .then((lista) => {
        if (!cancelado) setHistorial(lista)
      })
      .catch((err) => {
        if (!cancelado) {
          setErrorHistorial(
            err.message || 'No se pudo obtener el historial de mantenimientos.',
          )
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
  }, [maquinaria.id])

  // Si la lectura supera el horómetro, el backend la toma como horómetro actual.
  const horometroResultante = Math.max(
    Number(form.horasMaquinaEnService || 0),
    Number(maquinaria.horasUsoActuales || 0),
  )
  const proximoInvalido =
    form.proximoServiceHoras !== '' &&
    Number(form.proximoServiceHoras) <= horometroResultante

  const costoTotal = historial.reduce((suma, m) => suma + Number(m.costo), 0)

  async function manejarSubmit(e) {
    e.preventDefault()
    setError('')
    setGuardando(true)

    const payload = {
      fechaService: form.fechaService,
      horasMaquinaEnService: Number(form.horasMaquinaEnService),
      tipoMantenimiento: form.tipoMantenimiento,
      costo: Number(form.costo),
      proximoServiceHoras: Number(form.proximoServiceHoras),
      descripcion: form.descripcion.trim() || null,
    }

    try {
      await registrarMantenimiento(maquinaria.id, payload)
      onGuardado(
        `Service registrado para "${maquinaria.nombre}". Próximo service a las ${formatearNumero(payload.proximoServiceHoras)} hs.`,
      )
    } catch (err) {
      setError(err.message || 'No se pudo registrar el mantenimiento.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto
      ancho
      titulo={`Mantenimientos: ${maquinaria.nombre}`}
      onCerrar={onCerrar}
    >
      <div className="resumen-ajuste">
        <div>
          <span>Estado:</span>{' '}
          <strong>{etiquetaEstadoMaquinaria(maquinaria.estado)}</strong>
        </div>
        <div>
          <span>Horómetro:</span>{' '}
          <strong>{formatearNumero(maquinaria.horasUsoActuales)} hs</strong>
        </div>
        <div>
          <span>Próximo service:</span>{' '}
          <strong>
            {formatearNumero(maquinaria.horasParaProximoService)} hs
          </strong>
          {maquinaria.mantenimientoVencido && (
            <span className="etiqueta etiqueta--critica"> Vencido</span>
          )}
        </div>
      </div>

      {puedeRegistrar ? (
        <form onSubmit={manejarSubmit} className="formulario">
          {error && <div className="alerta alerta--error">{error}</div>}

          <div className="fila-campos">
            <div className="campo">
              <label htmlFor="mant-fecha">Fecha del service</label>
              <input
                id="mant-fecha"
                type="date"
                required
                max={fechaHoyIso()}
                value={form.fechaService}
                onChange={(e) =>
                  setForm({ ...form, fechaService: e.target.value })
                }
              />
            </div>

            <div className="campo">
              <label htmlFor="mant-tipo">Tipo de mantenimiento</label>
              <select
                id="mant-tipo"
                value={form.tipoMantenimiento}
                onChange={(e) =>
                  setForm({ ...form, tipoMantenimiento: e.target.value })
                }
              >
                {TIPOS_MANTENIMIENTO.map((t) => (
                  <option key={t.valor} value={t.valor}>
                    {t.etiqueta}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="fila-campos">
            <div className="campo">
              <label htmlFor="mant-lectura">Horómetro en el service (hs)</label>
              <input
                id="mant-lectura"
                type="number"
                step="0.01"
                min="0"
                required
                value={form.horasMaquinaEnService}
                onChange={(e) =>
                  setForm({ ...form, horasMaquinaEnService: e.target.value })
                }
              />
            </div>

            <div className="campo">
              <label htmlFor="mant-proximo">Próximo service (hs)</label>
              <input
                id="mant-proximo"
                type="number"
                step="0.01"
                min="0.01"
                required
                value={form.proximoServiceHoras}
                onChange={(e) =>
                  setForm({ ...form, proximoServiceHoras: e.target.value })
                }
                placeholder={`Mayor a ${formatearNumero(horometroResultante)}`}
              />
            </div>

            <div className="campo">
              <label htmlFor="mant-costo">Costo ($)</label>
              <input
                id="mant-costo"
                type="number"
                step="0.01"
                min="0"
                required
                value={form.costo}
                onChange={(e) => setForm({ ...form, costo: e.target.value })}
              />
            </div>
          </div>

          {proximoInvalido && (
            <p className="texto-peligro texto-chico">
              El próximo service debe ser mayor a{' '}
              {formatearNumero(horometroResultante)} hs, si no la máquina
              quedaría con el service vencido.
            </p>
          )}

          <div className="campo">
            <label htmlFor="mant-descripcion">Descripción (opcional)</label>
            <textarea
              id="mant-descripcion"
              rows={2}
              maxLength={1000}
              value={form.descripcion}
              onChange={(e) =>
                setForm({ ...form, descripcion: e.target.value })
              }
              placeholder="Ej: Cambio de aceite 15W40 y filtros de aire y combustible"
            />
          </div>

          {maquinaria.estado === 'EN_MANTENIMIENTO' && (
            <p className="texto-suave texto-chico">
              Al registrar el service la máquina vuelve a estado
              &laquo;Operativo&raquo;.
            </p>
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
              disabled={guardando || proximoInvalido}
            >
              {guardando ? 'Guardando...' : 'Registrar service'}
            </button>
          </div>
        </form>
      ) : (
        <p className="aviso">
          {maquinaria.estado === 'EN_LABOR'
            ? 'La máquina está trabajando en una labor: el service se registra cuando la labor finalice.'
            : 'La máquina está fuera de servicio: un administrador debe reingresarla a taller antes de registrar el service.'}
        </p>
      )}

      <h3>Historial de services</h3>

      {errorHistorial && (
        <div className="alerta alerta--error">{errorHistorial}</div>
      )}

      {cargando ? (
        <p className="aviso">Cargando historial...</p>
      ) : historial.length === 0 ? (
        <p className="aviso">Esta máquina todavía no registra services.</p>
      ) : (
        <>
          <div className="tabla-contenedor">
            <table className="tabla">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Tipo</th>
                  <th className="texto-derecha">Horómetro</th>
                  <th className="texto-derecha">Próx. service</th>
                  <th className="texto-derecha">Costo</th>
                  <th>Responsable</th>
                  <th>Descripción</th>
                </tr>
              </thead>
              <tbody>
                {historial.map((m) => (
                  <tr key={m.id}>
                    <td>{formatearFechaSimple(m.fechaService)}</td>
                    <td>
                      <span className="etiqueta">
                        {etiquetaTipoMantenimiento(m.tipoMantenimiento)}
                      </span>
                    </td>
                    <td className="texto-derecha">
                      {formatearNumero(m.horasMaquinaEnService)} hs
                    </td>
                    <td className="texto-derecha">
                      {formatearNumero(m.proximoServiceHoras)} hs
                    </td>
                    <td className="texto-derecha">
                      $ {formatearNumero(m.costo)}
                    </td>
                    <td>{m.usuarioUsername || '-'}</td>
                    <td className="texto-chico">{m.descripcion || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="texto-suave texto-chico">
            {historial.length} service{historial.length > 1 ? 's' : ''} · Costo
            acumulado: $ {formatearNumero(costoTotal)}
          </p>
        </>
      )}

      {!puedeRegistrar && (
        <div className="modal__pie">
          <button
            type="button"
            className="btn btn--secundario"
            onClick={onCerrar}
          >
            Cerrar
          </button>
        </div>
      )}
    </Modal>
  )
}

export default MantenimientoModal
