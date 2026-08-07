function formatearNumero(valor) {
  return new Intl.NumberFormat('es-ES').format(valor)
}

function ResumenBiblioteca({ resumen }) {
  return (
    <dl className="resumen-global" aria-label="Resumen de la biblioteca">
      <div>
        <dd>{formatearNumero(resumen.totalLibros)}</dd>
        <dt>libros</dt>
      </div>
      <div>
        <dd>{formatearNumero(resumen.totalLeidos)}</dd>
        <dt>leídos</dt>
      </div>
      <div>
        <dd>{formatearNumero(resumen.totalPendientes)}</dd>
        <dt>pendientes</dt>
      </div>
    </dl>
  )
}

export default ResumenBiblioteca
