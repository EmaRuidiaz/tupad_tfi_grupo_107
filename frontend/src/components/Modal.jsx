import { useEffect } from 'react'

function Modal({ abierto, titulo, onCerrar, ancho = false, children }) {
  useEffect(() => {
    if (!abierto) return undefined

    function manejarTecla(e) {
      if (e.key === 'Escape') {
        onCerrar()
      }
    }

    window.addEventListener('keydown', manejarTecla)
    return () => window.removeEventListener('keydown', manejarTecla)
  }, [abierto, onCerrar])

  if (!abierto) return null

  return (
    <div
      className="modal-fondo"
      role="dialog"
      aria-modal="true"
      aria-label={titulo}
      onClick={(e) => {
        if (e.target === e.currentTarget) onCerrar()
      }}
    >
      <div className={`modal ${ancho ? 'modal--ancho' : ''}`}>
        <div className="modal__encabezado">
          <h2>{titulo}</h2>
          <button
            type="button"
            className="modal__cerrar"
            onClick={onCerrar}
            aria-label="Cerrar"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

export default Modal
