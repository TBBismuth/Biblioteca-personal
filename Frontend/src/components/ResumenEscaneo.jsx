import { useEffect, useRef } from 'react'

const CAMPOS_RESUMEN = [
  ['archivosEncontrados', 'Archivos encontrados'],
  ['librosNuevos', 'Libros nuevos'],
  ['archivosNuevos', 'Archivos nuevos'],
  ['archivosMovidosRenombrados', 'Movidos o renombrados'],
  ['copiasIdenticasNuevas', 'Copias idénticas nuevas'],
  ['archivosModificados', 'Archivos modificados'],
  ['archivosDesaparecidosEliminados', 'Archivos desaparecidos'],
  ['nombresInvalidos', 'Nombres no válidos'],
  ['erroresLectura', 'Errores de lectura'],
]

const CAMPOS_CAMBIOS = CAMPOS_RESUMEN.slice(1, 7).map(([campo]) => campo)

function ListaDetalles({ titulo, detalles }) {
  if (!Array.isArray(detalles) || detalles.length === 0) return null

  return (
    <details className="detalles-escaneo">
      <summary>{titulo}</summary>
      <ul>
        {detalles.map((detalle, indice) => <li key={`${indice}-${detalle}`}>{detalle}</li>)}
      </ul>
    </details>
  )
}

function ListaArchivosEscaneo({ titulo, detalles, aclaracion }) {
  if (!Array.isArray(detalles) || detalles.length === 0) return null

  return (
    <details className="detalles-escaneo detalles-archivos-escaneo">
      <summary>{titulo}</summary>
      {aclaracion && <p className="aclaracion-detalles">{aclaracion}</p>}
      <ul>
        {detalles.map((detalle, indice) => (
          <li key={`${detalle.ruta}-${detalle.nombreArchivo}-${indice}`}>
            <strong>{detalle.nombreArchivo}</strong>
            <span>{detalle.ruta}</span>
          </li>
        ))}
      </ul>
    </details>
  )
}

const COLETILLA_NOMBRE_INVALIDO = ' | No cumple el formato Autor - Título'

function limpiarDetalleInvalido(detalle) {
  return typeof detalle === 'string' && detalle.endsWith(COLETILLA_NOMBRE_INVALIDO)
    ? detalle.slice(0, -COLETILLA_NOMBRE_INVALIDO.length)
    : detalle
}

function ResumenEscaneo({ resultado, onCerrar }) {
  const botonCerrarRef = useRef(null)
  const tieneCambios = CAMPOS_CAMBIOS.some((campo) => Number(resultado[campo]) > 0)

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
        className="dialogo-biblioteca resumen-escaneo-dialogo"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-resumen-escaneo"
      >
        <h2 id="titulo-resumen-escaneo">Biblioteca actualizada</h2>
        {!tieneCambios && <p className="sin-cambios">La biblioteca ya estaba actualizada.</p>}
        <dl className="resumen-escaneo">
          {CAMPOS_RESUMEN.map(([campo, etiqueta]) => (
            <div className={Number(resultado[campo]) > 0 ? 'con-cambios' : ''} key={campo}>
              <dt>{etiqueta}</dt>
              <dd>{Number(resultado[campo]) || 0}</dd>
            </div>
          ))}
          <div>
            <dt>Archivos ya registrados</dt>
            <dd>{Number(resultado.archivosYaRegistrados) || 0}</dd>
          </div>
        </dl>
        {Number(resultado.archivosNuevos) > 0 && (
          <ListaArchivosEscaneo
            titulo="Ver archivos nuevos"
            detalles={resultado.detallesArchivosNuevos}
          />
        )}
        {Number(resultado.archivosDesaparecidosEliminados) > 0 && (
          <ListaArchivosEscaneo
            titulo="Ver archivos desaparecidos"
            detalles={resultado.detallesArchivosDesaparecidos}
            aclaracion="La ruta mostrada corresponde a la última ubicación registrada del archivo."
          />
        )}
        <ListaDetalles
          titulo="Ver nombres no válidos"
          detalles={resultado.detallesInvalidos?.map(limpiarDetalleInvalido)}
        />
        <ListaDetalles titulo="Ver errores de lectura" detalles={resultado.detallesErrores} />
        <div className="acciones-dialogo una-accion">
          <button ref={botonCerrarRef} type="button" onClick={onCerrar}>Cerrar</button>
        </div>
      </section>
    </div>
  )
}

export default ResumenEscaneo
