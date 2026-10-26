import Modal from './Modal.jsx'

function ConfirmModal({
  abierto,
  titulo,
  mensaje,
  detalle,
  textoConfirmar = 'Eliminar',
  textoCargando,
  cargando = false,
  error = '',
  onCancelar,
  onConfirmar,
}) {
  const cargandoTexto =
    textoCargando ||
    (textoConfirmar === 'Eliminar' ? 'Eliminando...' : 'Procesando...')

  return (
    <Modal abierto={abierto} titulo={titulo} onCerrar={onCancelar}>
      {error && (
        <div className="alerta alerta--error" role="alert">
          {error}
        </div>
      )}

      <div className="modal__cuerpo">
        <p className="modal__mensaje">{mensaje}</p>
        {detalle && (
          <p className="modal__detalle texto-suave texto-chico">{detalle}</p>
        )}
      </div>

      <div className="modal__pie">
        <button
          type="button"
          className="btn btn--secundario"
          onClick={onCancelar}
          disabled={cargando}
        >
          Cancelar
        </button>
        <button
          type="button"
          className="btn btn--peligro-solido"
          onClick={onConfirmar}
          disabled={cargando}
        >
          {cargando ? cargandoTexto : textoConfirmar}
        </button>
      </div>
    </Modal>
  )
}

export default ConfirmModal
