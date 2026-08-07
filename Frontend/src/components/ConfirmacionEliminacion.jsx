import { useEffect, useRef } from 'react'

function ConfirmacionEliminacion({ confirmacion, ocupada, error, onCancelar, onConfirmar }) {
  const cancelarRef = useRef(null)
  const { tipo, libro, copia } = confirmacion
  const todas = tipo === 'todas'
  const ultimaCopia = !todas && libro.numeroArchivos === 1

  useEffect(() => {
    cancelarRef.current?.focus()
  }, [])

  useEffect(() => {
    const cerrarConEscape = (evento) => {
      if (evento.key === 'Escape' && !ocupada) onCancelar()
    }
    document.addEventListener('keydown', cerrarConEscape)
    return () => document.removeEventListener('keydown', cerrarConEscape)
  }, [ocupada, onCancelar])

  return (
    <div className="dialogo-fondo">
      <section
        className="dialogo-biblioteca dialogo-eliminacion"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-confirmacion-eliminacion"
      >
        <h2 id="titulo-confirmacion-eliminacion">
          {todas
            ? '¿Enviar todas las copias a la Papelera?'
            : '¿Enviar esta copia a la Papelera?'}
        </h2>

        {todas ? (
          <>
            <dl className="datos-eliminacion">
              <div><dt>Título</dt><dd>{libro.titulo}</dd></div>
              <div><dt>Autores</dt><dd>{libro.autores?.join(', ') || 'Autor desconocido'}</dd></div>
              <div><dt>Número de copias</dt><dd>{libro.numeroArchivos}</dd></div>
            </dl>
            {confirmacion.copias?.length > 0 && (
              <div className="lista-resumida-copias">
                <strong>Archivos que se enviarán:</strong>
                <ul>
                  {confirmacion.copias.map((item) => (
                    <li key={item.id}>{item.nombreArchivo}</li>
                  ))}
                </ul>
              </div>
            )}
            <p className="aviso-eliminacion-importante">
              Todas las copias físicas de este libro se enviarán a la Papelera de reciclaje.
              El libro dejará de aparecer en la biblioteca, pero su información y estado de
              lectura se conservarán.
            </p>
          </>
        ) : (
          <>
            <dl className="datos-eliminacion">
              <div><dt>Nombre del archivo</dt><dd>{copia.nombreArchivo}</dd></div>
              <div><dt>Formato</dt><dd>{copia.extension || 'Sin formato'}</dd></div>
              <div><dt>Ruta completa</dt><dd>{copia.ruta}</dd></div>
              <div><dt>Copias actuales</dt><dd>{libro.numeroArchivos}</dd></div>
            </dl>
            <p>Únicamente se enviará a la Papelera esta copia física.</p>
            <p className={ultimaCopia ? 'aviso-eliminacion-importante' : 'aviso-eliminacion'}>
              {ultimaCopia
                ? 'Esta es la última copia disponible. El libro dejará de aparecer en la biblioteca, pero se conservarán su información y su estado de lectura.'
                : 'El archivo se enviará a la Papelera de reciclaje de Windows. El libro seguirá disponible mientras conserve al menos otra copia.'}
            </p>
          </>
        )}

        {error && (
          <div className="mensaje-error error-dialogo" role="alert">
            <strong>{error.titulo}</strong>
            {error.detalle && <span>{error.detalle}</span>}
            {error.recomendarActualizacion && (
              <span>Actualiza la biblioteca para sincronizar los cambios.</span>
            )}
          </div>
        )}

        <div className="acciones-dialogo">
          <button
            ref={cancelarRef}
            type="button"
            className="boton-secundario"
            onClick={onCancelar}
            disabled={ocupada}
          >
            Cancelar
          </button>
          <button
            type="button"
            className="boton-papelera-confirmar"
            onClick={onConfirmar}
            disabled={ocupada}
          >
            {ocupada
              ? todas ? 'Enviando copias a la Papelera...' : 'Enviando a la Papelera...'
              : todas ? 'Enviar todas a la Papelera' : 'Enviar a la Papelera'}
          </button>
        </div>
      </section>
    </div>
  )
}

export default ConfirmacionEliminacion
