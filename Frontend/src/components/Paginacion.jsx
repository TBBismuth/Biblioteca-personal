function Paginacion({ datos, onPagina }) {
  if (datos.totalPaginas <= 1) return null

  return (
    <nav className="paginacion" aria-label="Paginación de libros">
      <button
        type="button"
        className="boton-secundario"
        disabled={datos.primeraPagina}
        onClick={() => onPagina(datos.paginaActual - 1)}
      >
        Anterior
      </button>
      <span>Página {datos.paginaActual + 1} de {datos.totalPaginas}</span>
      <button
        type="button"
        className="boton-secundario"
        disabled={datos.ultimaPagina}
        onClick={() => onPagina(datos.paginaActual + 1)}
      >
        Siguiente
      </button>
    </nav>
  )
}

export default Paginacion
