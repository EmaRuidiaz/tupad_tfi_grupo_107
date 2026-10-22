import { Link } from 'react-router-dom'

function NoEncontrado() {
  return (
    <section>
      <h1>Página no encontrada</h1>
      <p>La dirección ingresada no existe.</p>
      <Link to="/">Volver al inicio</Link>
    </section>
  )
}

export default NoEncontrado
