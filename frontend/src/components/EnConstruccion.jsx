// Contenido provisorio para las pantallas que todavía no se implementaron.
function EnConstruccion({ titulo, descripcion, historia }) {
  return (
    <section>
      <h1>{titulo}</h1>
      <p>{descripcion}</p>
      <p className="aviso">
        Pantalla en construcción{historia ? ` (${historia})` : ''}.
      </p>
    </section>
  )
}

export default EnConstruccion
