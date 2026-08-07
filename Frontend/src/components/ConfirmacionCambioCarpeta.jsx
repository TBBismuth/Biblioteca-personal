import { useEffect, useRef } from 'react'

function ConfirmacionCambioCarpeta({ rutaActual, rutaNueva, error, ocupada, onCancelar, onConfirmar }) {
  const botonCancelarRef = useRef(null)

  useEffect(() => {
    botonCancelarRef.current?.focus()

    const cerrarConEscape = (evento) => {
      if (evento.key === 'Escape' && !ocupada) onCancelar()
    }

    document.addEventListener('keydown', cerrarConEscape)
    return () => document.removeEventListener('keydown', cerrarConEscape)
  }, [ocupada, onCancelar])

  return (
    <div className="dialogo-fondo">
      <section
        className="dialogo-biblioteca"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-cambio-carpeta"
      >
        <h2 id="titulo-cambio-carpeta">¿Cambiar la carpeta de la biblioteca?</h2>
        <p>
          Se analizará la nueva ubicación y se actualizarán los archivos registrados.
          Los estados de lectura se conservarán.
        </p>
        <dl className="rutas-cambio">
          <div><dt>Ruta actual</dt><dd>{rutaActual}</dd></div>
          <div><dt>Nueva ruta</dt><dd>{rutaNueva}</dd></div>
        </dl>
        {error && <p className="mensaje-error" role="alert">{error}</p>}
        <div className="acciones-dialogo">
          <button
            ref={botonCancelarRef}
            type="button"
            className="boton-secundario"
            onClick={onCancelar}
            disabled={ocupada}
          >
            Cancelar
          </button>
          <button type="button" onClick={onConfirmar} disabled={ocupada}>
            Cambiar y escanear
          </button>
        </div>
      </section>
    </div>
  )
}

export default ConfirmacionCambioCarpeta
