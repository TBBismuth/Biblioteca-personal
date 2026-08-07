const API_BASE = '/api'

async function solicitar(ruta, opciones) {
  let respuesta

  try {
    respuesta = await fetch(`${API_BASE}${ruta}`, opciones)
  } catch {
    throw new Error('No se puede contactar con el backend. Comprueba que está iniciado.')
  }

  if (!respuesta.ok) {
    let detalle = ''

    try {
      const cuerpo = await respuesta.json()
      detalle = typeof cuerpo.message === 'string' ? cuerpo.message : ''
    } catch {
      // El backend puede responder sin un cuerpo JSON.
    }

    const error = new Error(detalle)
    error.status = respuesta.status
    throw error
  }

  return respuesta.json()
}

export function obtenerConfiguracion() {
  return solicitar('/configuracion')
}

export function guardarRuta(ruta) {
  return solicitar('/configuracion/ruta', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ ruta }),
  })
}

export function escanearBiblioteca() {
  return solicitar('/escaneo', {
    method: 'POST',
  })
}

export function obtenerLibros({
  busqueda = '',
  estado = 'TODOS',
  pagina = 0,
  tamano = 50,
  signal,
} = {}) {
  const parametros = new URLSearchParams()

  if (busqueda.trim()) {
    parametros.set('busqueda', busqueda.trim())
  }

  parametros.set('estado', estado)
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))

  return solicitar(`/libros?${parametros.toString()}`, { signal })
}

export function obtenerResumenBiblioteca({ signal } = {}) {
  return solicitar('/libros/resumen', { signal })
}

export function cambiarEstadoLectura(id, leido) {
  return solicitar(`/libros/${id}/leido`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ leido }),
  })
}

export function obtenerCopiasLibro(id) {
  return solicitar(`/libros/${id}/copias`)
}

export function actualizarLibro(id, datos) {
  return solicitar(`/libros/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      titulo: datos.titulo,
      autores: datos.autores,
    }),
  })
}
