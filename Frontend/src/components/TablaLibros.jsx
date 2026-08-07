import { Fragment } from 'react'
import DetalleCopias from './DetalleCopias.jsx'
import SelectorEstado from './SelectorEstado.jsx'

function TablaLibros({
  libros,
  menuEstadoId,
  guardandoId,
  detalle,
  onAbrirEstado,
  onCerrarEstado,
  onCambiarEstado,
  onAlternarCopias,
  onEditar,
  edicionBloqueada,
}) {
  return (
    <div className="tabla-contenedor">
      <table>
        <thead>
          <tr>
            <th>Título</th>
            <th>Autor o autores</th>
            <th>Formato</th>
            <th>Copias</th>
            <th>Estado</th>
            <th>Detalles</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {libros.map((libro) => (
            <Fragment key={libro.id}>
              <tr>
                <td className="celda-titulo">{libro.titulo}</td>
                <td>{libro.autores?.length ? libro.autores.join(', ') : 'Autor desconocido'}</td>
                <td>
                  <div className="formatos-libro">
                    {libro.formatos?.length
                      ? libro.formatos.map((formato) => (
                        <span className="etiqueta-formato" key={formato}>{formato}</span>
                      ))
                      : 'Sin formato'}
                  </div>
                </td>
                <td>{libro.numeroArchivos}</td>
                <td>
                  <SelectorEstado
                    libro={libro}
                    abierto={menuEstadoId === libro.id}
                    guardando={guardandoId === libro.id}
                    onAbrir={() => onAbrirEstado(libro.id)}
                    onCerrar={onCerrarEstado}
                    onSeleccionar={(leido) => onCambiarEstado(libro, leido)}
                  />
                </td>
                <td>
                  <button
                    type="button"
                    className="boton-tabla"
                    aria-expanded={detalle?.id === libro.id}
                    onClick={() => onAlternarCopias(libro.id)}
                  >
                    {detalle?.id === libro.id ? 'Ocultar copias' : 'Ver copias'}
                  </button>
                </td>
                <td>
                  <button
                    type="button"
                    className="boton-tabla boton-editar"
                    onClick={() => onEditar(libro)}
                    disabled={edicionBloqueada}
                  >
                    Editar
                  </button>
                </td>
              </tr>
              {detalle?.id === libro.id && (
                <tr className="fila-detalle">
                  <td colSpan="7"><DetalleCopias detalle={detalle} /></td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default TablaLibros
