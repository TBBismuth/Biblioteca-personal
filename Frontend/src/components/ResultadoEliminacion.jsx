import { useEffect, useRef } from 'react'

function ListaCopiasEnviadas({ copias, titulo }) {
  if (!copias.length) return null
  return (
    <details className="detalles-escaneo archivos-enviados-papelera">
      <summary>{titulo}</summary>
      <ul>
        {copias.map((copia) => (
          <li key={copia.idArchivo}>
            <strong>{copia.nombreArchivo}</strong>
            <span>{copia.rutaAnterior}</span>
          </li>
        ))}
      </ul>
    </details>
  )
}

function ResultadoEliminacion({ resultado, onCerrar }) {
  const cerrarRef = useRef(null)
  const copias = Array.isArray(resultado.datos?.copiasEliminadas)
    ? resultado.datos.copiasEliminadas
    : []

  useEffect(() => {
    cerrarRef.current?.focus()
    const cerrarConEscape = (evento) => {
      if (evento.key === 'Escape') onCerrar()
    }
    document.addEventListener('keydown', cerrarConEscape)
    return () => document.removeEventListener('keydown', cerrarConEscape)
  }, [onCerrar])

  if (resultado.parcial) {
    return (
      <div className="dialogo-fondo">
        <section
          className="dialogo-biblioteca resultado-eliminacion-dialogo"
          role="dialog"
          aria-modal="true"
          aria-labelledby="titulo-resultado-eliminacion"
        >
          <h2 id="titulo-resultado-eliminacion">El proceso no pudo completarse</h2>
          <p>
            Algunas copias se enviaron correctamente a la Papelera, pero no fue posible
            completar la operación.
          </p>
          {resultado.mensaje && <p className="aviso-eliminacion">{resultado.mensaje}</p>}
          <dl className="datos-eliminacion">
            <div><dt>Copias enviadas correctamente</dt><dd>{copias.length}</dd></div>
            <div><dt>Copias restantes</dt><dd>{resultado.datos.copiasRestantes}</dd></div>
          </dl>
          <ListaCopiasEnviadas copias={copias} titulo="Ver copias enviadas" />
          <p>La biblioteca se ha recargado con el estado confirmado por el Backend.</p>
          <div className="acciones-dialogo una-accion">
            <button ref={cerrarRef} type="button" onClick={onCerrar}>Cerrar</button>
          </div>
        </section>
      </div>
    )
  }

  const unaCopia = resultado.tipo === 'copia'
  return (
    <div className="dialogo-fondo">
      <section
        className="dialogo-biblioteca resultado-eliminacion-dialogo"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-resultado-eliminacion"
      >
        <h2 id="titulo-resultado-eliminacion">
          {unaCopia ? 'Copia enviada a la Papelera' : 'Copias enviadas a la Papelera'}
        </h2>

        {unaCopia && copias[0] ? (
          <dl className="datos-eliminacion">
            <div><dt>Nombre del archivo</dt><dd>{copias[0].nombreArchivo}</dd></div>
            <div><dt>Ruta anterior</dt><dd>{copias[0].rutaAnterior}</dd></div>
            <div><dt>Copias restantes</dt><dd>{resultado.datos.copiasRestantes}</dd></div>
          </dl>
        ) : (
          <>
            <dl className="datos-eliminacion">
              <div><dt>Copias enviadas</dt><dd>{copias.length}</dd></div>
            </dl>
            <ListaCopiasEnviadas
              copias={copias}
              titulo="Ver archivos enviados a la Papelera"
            />
          </>
        )}

        <p className="aviso-eliminacion">
          {resultado.datos.libroDisponible
            ? 'El libro continúa disponible.'
            : 'El libro ya no tiene copias disponibles. Su información y estado de lectura se conservan.'}
        </p>
        <div className="acciones-dialogo una-accion">
          <button ref={cerrarRef} type="button" onClick={onCerrar}>Cerrar</button>
        </div>
      </section>
    </div>
  )
}

export default ResultadoEliminacion
