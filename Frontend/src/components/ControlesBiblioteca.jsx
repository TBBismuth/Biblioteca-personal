const FILTROS = [
  { valor: 'TODOS', etiqueta: 'Todos' },
  { valor: 'PENDIENTES', etiqueta: 'Pendientes' },
  { valor: 'LEIDOS', etiqueta: 'Leídos' },
]

function ControlesBiblioteca({ busqueda, estado, buscando, onBusqueda, onEstado }) {
  return (
    <section className="controles-biblioteca" aria-label="Búsqueda y filtros">
      <div className="buscador-biblioteca">
        <label htmlFor="busqueda-libros">Buscar libros</label>
        <div className="campo-busqueda">
          <input
            id="busqueda-libros"
            type="search"
            value={busqueda}
            onChange={(evento) => onBusqueda(evento.target.value)}
            placeholder="Buscar por título o autor..."
          />
          {busqueda && (
            <button type="button" className="boton-secundario" onClick={() => onBusqueda('')}>
              Limpiar búsqueda
            </button>
          )}
        </div>
        <span className="indicador-busqueda" role="status">
          {buscando ? 'Buscando...' : ''}
        </span>
      </div>

      <div className="filtros-biblioteca" aria-label="Filtrar por estado de lectura">
        {FILTROS.map((filtro) => (
          <button
            key={filtro.valor}
            type="button"
            className={estado === filtro.valor ? 'filtro-activo' : ''}
            aria-pressed={estado === filtro.valor}
            onClick={() => onEstado(filtro.valor)}
          >
            {filtro.etiqueta}
          </button>
        ))}
      </div>
    </section>
  )
}

export default ControlesBiblioteca
