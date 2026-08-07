import { useEffect, useRef, useState } from 'react'
import { actualizarLibro } from '../services/bibliotecaApi.js'

function normalizarParaComparar(texto) {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es')
    .trim()
    .replace(/\s+/g, ' ')
}

function prepararAutores(autores) {
  const autoresUnicos = new Map()
  for (const valor of autores) {
    const autor = valor.trim()
    if (!autor) continue
    const clave = normalizarParaComparar(autor)
    if (!autoresUnicos.has(clave)) autoresUnicos.set(clave, autor)
  }
  return [...autoresUnicos.values()]
}

function mensajeErrorGuardado(error) {
  if (error?.message?.startsWith('No se puede contactar')) return error.message
  if (error?.status === 500) {
    return error.message && !error.message.includes('interno inesperado')
      ? error.message
      : 'No se pudo completar el renombrado. Los archivos pueden requerir una comprobación manual.'
  }
  return error?.message || 'No se pudieron guardar los cambios del libro.'
}

function EditarLibroDialogo({ libro, onCancelar, onGuardando, onGuardado }) {
  const [titulo, setTitulo] = useState(libro.titulo)
  const autoresIniciales = libro.autores?.length ? libro.autores : ['']
  const siguienteAutorId = useRef(autoresIniciales.length)
  const [autores, setAutores] = useState(() => autoresIniciales.map((valor, indice) => ({
    id: indice,
    valor,
  })))
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')
  const [aviso, setAviso] = useState('')
  const [autorFocoId, setAutorFocoId] = useState(null)
  const tituloRef = useRef(null)
  const autoresRef = useRef(new Map())

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  useEffect(() => {
    if (autorFocoId === null) return
    autoresRef.current.get(autorFocoId)?.focus()
    setAutorFocoId(null)
  }, [autores, autorFocoId])

  useEffect(() => {
    const cerrarConEscape = (evento) => {
      if (evento.key === 'Escape' && !guardando) onCancelar()
    }
    document.addEventListener('keydown', cerrarConEscape)
    return () => document.removeEventListener('keydown', cerrarConEscape)
  }, [guardando, onCancelar])

  const cambiarAutor = (id, valor) => {
    setAutores((actuales) => actuales.map((autor) => (
      autor.id === id ? { ...autor, valor } : autor
    )))
    setError('')
    setAviso('')
  }

  const anadirAutor = () => {
    const id = siguienteAutorId.current
    siguienteAutorId.current += 1
    setAutorFocoId(id)
    setAutores((actuales) => [...actuales, { id, valor: '' }])
  }

  const eliminarAutor = (id) => {
    if (autores.length === 1) return
    setAutores((actuales) => actuales.filter((autor) => autor.id !== id))
    autoresRef.current.delete(id)
    setError('')
    setAviso('')
  }

  const guardar = async (evento) => {
    evento.preventDefault()
    if (guardando) return

    const tituloPreparado = titulo.trim()
    const autoresPreparados = prepararAutores(autores.map((autor) => autor.valor))
    if (!tituloPreparado) {
      setError('El título es obligatorio.')
      tituloRef.current?.focus()
      return
    }
    if (autoresPreparados.length === 0) {
      setError('Debe indicarse al menos un autor.')
      autoresRef.current.get(autores[0]?.id)?.focus()
      return
    }

    const tituloIgual = tituloPreparado === libro.titulo
    const autoresIguales = autoresPreparados.length === (libro.autores?.length || 0)
      && autoresPreparados.every((autor, indice) => autor === libro.autores[indice])
    if (tituloIgual && autoresIguales) {
      setError('')
      setAviso('No hay cambios que guardar.')
      return
    }

    setGuardando(true)
    onGuardando(true)
    setError('')
    setAviso('')
    try {
      const respuesta = await actualizarLibro(libro.id, {
        titulo: tituloPreparado,
        autores: autoresPreparados,
      })
      onGuardado(respuesta)
    } catch (errorGuardado) {
      setError(mensajeErrorGuardado(errorGuardado))
      setGuardando(false)
      onGuardando(false)
    }
  }

  return (
    <div className="dialogo-fondo">
      <section
        className="dialogo-biblioteca dialogo-editar-libro"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-dialogo-editar"
      >
        <h2 id="titulo-dialogo-editar">Editar libro</h2>
        <p className="explicacion-edicion">
          Se actualizarán el título, los autores y los nombres de todas las copias.
        </p>
        <form onSubmit={guardar}>
          <fieldset disabled={guardando}>
            <div className="campo-formulario">
              <label htmlFor="editar-titulo">Título</label>
              <input
                ref={tituloRef}
                id="editar-titulo"
                type="text"
                value={titulo}
                onChange={(evento) => {
                  setTitulo(evento.target.value)
                  setError('')
                  setAviso('')
                }}
              />
            </div>

            <div className="autores-formulario">
              <span className="etiqueta-grupo">Autores</span>
              {autores.map((autor, indice) => (
                <div className="fila-autor" key={autor.id}>
                  <label htmlFor={`editar-autor-${autor.id}`}>Autor {indice + 1}</label>
                  <input
                    ref={(elemento) => {
                      if (elemento) autoresRef.current.set(autor.id, elemento)
                      else autoresRef.current.delete(autor.id)
                    }}
                    id={`editar-autor-${autor.id}`}
                    type="text"
                    value={autor.valor}
                    onChange={(evento) => cambiarAutor(autor.id, evento.target.value)}
                  />
                  {autores.length > 1 && (
                    <button
                      type="button"
                      className="boton-secundario boton-eliminar-autor"
                      onClick={() => eliminarAutor(autor.id)}
                    >
                      Eliminar
                    </button>
                  )}
                </div>
              ))}
              <button type="button" className="boton-secundario" onClick={anadirAutor}>
                Añadir autor
              </button>
            </div>
          </fieldset>

          {error && <p className="mensaje-error error-dialogo" role="alert">{error}</p>}
          {aviso && <p className="aviso-dialogo" role="status">{aviso}</p>}

          <div className="acciones-dialogo">
            <button
              type="button"
              className="boton-secundario"
              onClick={onCancelar}
              disabled={guardando}
            >
              Cancelar
            </button>
            <button type="submit" disabled={guardando}>
              {guardando ? 'Guardando y renombrando archivos...' : 'Guardar cambios'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}

export default EditarLibroDialogo
