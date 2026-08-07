import { useEffect, useRef } from 'react'

function ResultadoRenombrado({ resultado, onCerrar }) {
  const botonCerrarRef = useRef(null)
  const archivos = Array.isArray(resultado.archivosRenombrados)
    ? resultado.archivosRenombrados
    : []

  useEffect(() => {
    botonCerrarRef.current?.focus()
    const cerrarConEscape = (evento) => {
      if (evento.key === 'Escape') onCerrar()
    }
    document.addEventListener('keydown', cerrarConEscape)
    return () => document.removeEventListener('keydown', cerrarConEscape)
  }, [onCerrar])

  return (
    <div className="dialogo-fondo">
      <section
        className="dialogo-biblioteca resultado-renombrado-dialogo"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-resultado-renombrado"
      >
        <h2 id="titulo-resultado-renombrado">Libro actualizado</h2>
        <dl className="datos-libro-actualizado">
          <div><dt>Nuevo título</dt><dd>{resultado.libro.titulo}</dd></div>
          <div>
            <dt>Autores</dt>
            <dd>{resultado.libro.autores?.join(', ') || 'Autor desconocido'}</dd>
          </div>
          <div><dt>Archivos renombrados</dt><dd>{archivos.length}</dd></div>
        </dl>

        {archivos.length === 0 ? (
          <p>No fue necesario cambiar el nombre de los archivos.</p>
        ) : (
          <details className="detalles-escaneo archivos-renombrados">
            <summary>Ver archivos renombrados</summary>
            <ul>
              {archivos.map((archivo) => (
                <li key={archivo.idArchivo}>
                  <dl>
                    <div><dt>Nombre anterior</dt><dd>{archivo.nombreAnterior}</dd></div>
                    <div><dt>Nombre nuevo</dt><dd>{archivo.nombreNuevo}</dd></div>
                    <div><dt>Ruta anterior</dt><dd>{archivo.rutaAnterior}</dd></div>
                    <div><dt>Ruta nueva</dt><dd>{archivo.rutaNueva}</dd></div>
                  </dl>
                </li>
              ))}
            </ul>
          </details>
        )}

        <div className="acciones-dialogo una-accion">
          <button ref={botonCerrarRef} type="button" onClick={onCerrar}>Cerrar</button>
        </div>
      </section>
    </div>
  )
}

export default ResultadoRenombrado
