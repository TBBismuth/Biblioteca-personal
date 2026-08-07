function formatearTamano(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return 'Tamaño desconocido'

  const unidades = ['bytes', 'KB', 'MB', 'GB']
  let valor = bytes
  let unidad = 0

  while (valor >= 1024 && unidad < unidades.length - 1) {
    valor /= 1024
    unidad += 1
  }

  const decimales = unidad === 0 ? 0 : valor >= 10 ? 1 : 2
  return `${new Intl.NumberFormat('es-ES', { maximumFractionDigits: decimales }).format(valor)} ${unidades[unidad]}`
}

function formatearFecha(fecha) {
  const valor = new Date(fecha)
  if (Number.isNaN(valor.getTime())) return 'Fecha desconocida'

  return new Intl.DateTimeFormat('es-ES', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(valor)
}

function DetalleCopias({ detalle, libro, accionesBloqueadas, onEliminarCopia, onEliminarTodas }) {
  if (detalle.cargando) return <p role="status">Cargando copias...</p>
  if (detalle.error) return <p className="error-detalle" role="alert">{detalle.error}</p>
  if (!detalle.copias.length) return <p>No hay copias físicas registradas para este libro.</p>

  return (
    <ul className="lista-copias">
      {detalle.copias.map((copia) => (
        <li key={copia.id}>
          <div className="cabecera-copia">
            <strong>{copia.nombreArchivo}</strong>
            <span className="etiqueta-formato">{copia.extension || 'Sin formato'}</span>
          </div>
          <p className="ruta-copia">{copia.ruta}</p>
          <p className="metadatos-copia">
            {formatearTamano(copia.tamanioBytes)} · Modificado el {formatearFecha(copia.ultimaModificacion)}
          </p>
          <div className="acciones-copia">
            <button
              type="button"
              className="boton-secundario boton-papelera-copia"
              onClick={() => onEliminarCopia(libro, copia)}
              disabled={accionesBloqueadas}
            >
              Enviar a la Papelera
            </button>
          </div>
        </li>
      ))}
      <li className="eliminar-todas-copias">
        <div>
          <strong>Retirar este libro de la biblioteca disponible</strong>
          <p>Esta acción enviará sus {libro.numeroArchivos} copias físicas a la Papelera.</p>
        </div>
        <button
          type="button"
          className="boton-papelera-todas"
          onClick={() => onEliminarTodas(libro, detalle.copias)}
          disabled={accionesBloqueadas}
        >
          Enviar todas las copias a la Papelera
        </button>
      </li>
    </ul>
  )
}

export default DetalleCopias
